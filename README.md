Elasticsearch Combo Analyzer
----------------------------

This analyzer combines multiple analyzers into one and is based on the work of Olivier Favre.

Installation
-----------

	bin/plugin -install jprante/elasticsearch-analysis-combo/1.0.0

Introduction
------------

If you have a multilingual index, where each document has its source language, you can analyze the text fields using an analyzer based on the detected language. 
But what if you can't detect the language, the language detected was wrong, or aggressive stemming deforms the indexed terms? Or if you want to use other language-specific analyzers at search time, but still like to search original word forms indexed in another language?

In such cases, it helps to have the original words together with the stemmed words in the index.

The method is to configure an analyzer with type `combo` and a list of `sub_analyzers`, like shown below.

For example, you want to index German text together with other multilingual text in one field. A `combo` setup could look like

	{
	    "index" : {
	        "analysis" : {
	            "analyzer" : {
	                "default" : {
	                    "type" : "custom",
	                    "tokenizer" : "icu_tokenizer",
	                    "filter" : [ "snowball", "icu_folding" ]
	                },
	                "combo" : {
	                    "type" : "combo",
	                    "sub_analyzers" : [ "standard", "default" ]
	                }
	            },
	            "filter" : {
	                "snowball" : {
	                    "type" : "snowball",
	                    "language" : "German2"
	                }
	            }
	        }
	    }
	}

For handling German words with umlauts, snowball `German2` filter will expand umlauts (ä to ae, ö to oe, ü to ue). The ICU folding also recognizes german ß as ss, and performs de-accentuation (also known as normalization) on the other languages (also arabic and asian characters). The standard analyzer keeps the original word forms, and is combined with the new default analyzer.

The result can be checked with the Analyze API of Elasticsearch.

	curl -XGET 'localhost:9200/test/_analyze?analyzer=combo&pretty=true' -d 'Ein schöner Tag in Köln'
	{
	  "tokens" : [ {
	    "token" : "ein",
	    "start_offset" : 0,
	    "end_offset" : 3,
	    "type" : "<ALPHANUM>",
	    "position" : 1
	  }, {
	    "token" : "ein",
	    "start_offset" : 0,
	    "end_offset" : 3,
	    "type" : "<ALPHANUM>",
	    "position" : 1
	  }, {
	    "token" : "schöner",
	    "start_offset" : 4,
	    "end_offset" : 11,
	    "type" : "<ALPHANUM>",
	    "position" : 2
	  }, {
	    "token" : "schon",
	    "start_offset" : 4,
	    "end_offset" : 11,
	    "type" : "<ALPHANUM>",
	    "position" : 2
	  }, {
	    "token" : "tag",
	    "start_offset" : 12,
	    "end_offset" : 15,
	    "type" : "<ALPHANUM>",
	    "position" : 3
	  }, {
	    "token" : "tag",
	    "start_offset" : 12,
	    "end_offset" : 15,
	    "type" : "<ALPHANUM>",
	    "position" : 3
	  }, {
	   "token" : "in",
	    "start_offset" : 16,
	    "end_offset" : 18,
	    "type" : "<ALPHANUM>",
	    "position" : 4
	  }, {
	    "token" : "köln",
	    "start_offset" : 19,
	    "end_offset" : 23,
	    "type" : "<ALPHANUM>",
	    "position" : 5
	  }, {
	    "token" : "koln",
	    "start_offset" : 19,
	    "end_offset" : 23,
	    "type" : "<ALPHANUM>",
	    "position" : 5
	  } ]
	}

Some tokens may repeat, the offsets and positions may have same values. This should have minimal impact on indexing or relevance scoring. An improvement would be to get rid of the duplicates, but the Lucene token stream API is rather awkward right now, making it hard to run many analyzers over the input again and again. Behind the scenes, the token reader must be cloned. Hopefully this changes with a later Lucene version.

It is good practice to use the combo analyzer against index and also against search.


See also 

https://github.com/elasticsearch/elasticsearch/issues/1128

https://issues.apache.org/jira/browse/LUCENE-3392

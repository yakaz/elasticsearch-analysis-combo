Elasticsearch Combo Analyzer
============================

The Combo Analyzer plugin provides with a new analyzer type that combines the output of multiple analyzers into one.

Installation
-----------

Simply run at the root of your ElasticSearch v0.90+ installation:

	bin/plugin -install com.yakaz.elasticsearch.plugins/elasticsearch-analysis-combo/1.4.0

This will download the plugin from the Central Maven Repository.

For older versions of ElasticSearch, you can still use v1.2.0 using the longer command:

	bin/plugin -url http://oss.sonatype.org/content/repositories/releases/com/yakaz/elasticsearch/plugins/elasticsearch-analysis-combo/1.2.0/elasticsearch-analysis-combo-1.2.0.zip install elasticsearch-analysis-combo

In order to declare this plugin as a dependency, add the following to your `pom.xml`:

	<dependency>
	    <groupId>com.yakaz.elasticsearch.plugins</groupId>
	    <artifactId>elasticsearch-analysis-combo</artifactId>
	    <version>1.3.1</version>
	</dependency>

Version matrix:
    ┌───────────────────────┬──────────────────┐
    │ Combo Analyzer Plugin │ ElasticSearch    │
    ├───────────────────────┼──────────────────┤
    │ master                │ 0.90.3 ->        │
    ├───────────────────────┼──────────────────┤
    │ 1.4.x                 │ 0.90.3 ->        │
    ├───────────────────────┼──────────────────┤
    │ 1.3.x                 │ 0.90.0 -> 0.90.2 │
    ├───────────────────────┼──────────────────┤
    │ 1.2.x                 │ 0.19 -> 0.20     │
    ├───────────────────────┼──────────────────┤
    │ 1.1.x                 │ 0.19 -> 0.20     │
    ├───────────────────────┼──────────────────┤
    │ 1.0.x                 │ 0.19 -> 0.20     │
    └───────────────────────┴──────────────────┘

Description
-----------

If you have a multilingual index, where each document has its source language, you can analyze the text fields using an analyzer based on the detected language. 
But what if you can't detect the language, the language detected was wrong, or aggressive stemming deforms the indexed terms? Or if you want to use other language-specific analyzers at search time, but still like to search original word forms indexed in another language?

In such cases, it helps to have the original words together with the stemmed words in the index.

The proposed solution merges the terms received from multiple analyzers. It is called the `ComboAnalyzer`.

Configuration
-------------

The plugin provides you with the `combo` analyzer type.  
It expects a list of other analyzers to be used, under the `sub_analyzers` property.  
You can remove duplicated tokens sharing the same position by setting `true` for the `deduplication` property.

It is good practice to use the combo analyzer for both index and search.

### Example

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

Some tokens may repeat, the offsets and positions may have same values.
This should have minimal impact on indexing or relevance scoring. Note that it may even improve scoring, because of the reweighting implied by more matches on some words.

Behind the scene
----------------

Lucene TokenStream API is rather awkward right now, making it hard to run many analyzers over the input again and again.
Behind the scenes, the `Reader` given to the `Analyzer` must be cloned, in order to feed all the sub-analyzers.
Hopefully it may get easier to get multiple `Reader` in upcoming Lucene versions.

Pitfalls
--------

There have been reported problems with using multiple time the same `Analyzer` instance.
You can recognize such problem by noticing dropped tokens, and a final empty token.
Test the analysis with a simple word that should neither be tokenized, nor stemmed nor removed,
you should see it in the output as many times as you have sub-analyzers,
and you should not see any other tokens.

While this can be detected if it happens inside the same `ComboAnalyzer`, some problem can still arise in an awkward environment
(like cascading combo analyzers, sharing some `Analyzer` instances, or re-using an `Analyzer` while consuming the `ComboTokenStream`).

There are two possible fixes to remedy this problem:
* Disable completely the `TokenStream` reuse. Calls `Analyzer.tokenStream()` instead of `Analyzer.reusableTokenStream()`.
  Set the `tokenstream_reuse` property to `false` to use this solution.
* Activate use of token caching. All gotten `TokenStream`s will be completely consumed and cached right after its creation.
  This loads the information in memory, and may be unsuitable for very long documents.
  Set the `tokenstream_caching` property to `true` to use this solution.

Both solution affect memory usage and garbage collection a bit, thus affecting performance a bit too.
If you experience no problem with your setup, keep the defaults!
There should be no particular reason to use both these options simultaneously.

See also
--------

https://github.com/elasticsearch/elasticsearch/issues/1128

https://issues.apache.org/jira/browse/LUCENE-3392

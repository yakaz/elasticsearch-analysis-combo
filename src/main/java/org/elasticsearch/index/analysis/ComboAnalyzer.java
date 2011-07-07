/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.ReusableStringReaderCloner;
import org.apache.lucene.util.Version;
import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.io.FastCharArrayReader;
import org.elasticsearch.common.io.FastCharArrayReaderCloner;
import org.elasticsearch.common.io.FastStringReader;
import org.elasticsearch.common.io.FastStringReaderCloner;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;

import javax.io.ReaderCloner;
import javax.io.ReaderClonerDefaultImpl;
import javax.io.StringReaderCloner;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author ofavre
 */
public class ComboAnalyzer extends Analyzer {

    public static final String NAME = "combo";

    private final ESLogger logger;

    private final Injector injector;
    private final Settings settings;
    private final Version version;
    private final String name;

    private Analyzer[] subAnalyzers;
    private TokenStream[] lastTokenStreams;
    private TokenStream[] tempTokenStreams;
    private ComboTokenStream lastComboTokenStream;

    public ComboAnalyzer(Version version, String name, Settings settings, Injector injector) {
        logger = ESLoggerFactory.getLogger(ComboAnalyzer.NAME+">"+name);

        this.name = name;

        // Store parameters for lazy usage
        // See ComboAnalyzerProvider comments to learn why
        this.injector = injector;
        this.settings = settings;
        this.version = version;

        this.subAnalyzers = null;
        this.lastTokenStreams = null;
        this.tempTokenStreams = null;
        this.lastComboTokenStream = null;
    }

    @Override public TokenStream tokenStream(String fieldName, Reader originalReader) {
        // First call lazy loading of sub-analyzers
        // Here, we are free of the cyclic dependency on AnalysisService
        if (subAnalyzers == null) {
            init();
        }

        // Duplication of the original reader, to feed all sub-analyzers
        ReaderCloner readerCloner = null;
        if (subAnalyzers.length <= 1) {

            // Can reuse the only reader we have, there will be no need of duplication
            // Usage of the AtomicReference ensures that the same reader won't be duplicated.
            final AtomicReference<Reader> singleUsageReference = new AtomicReference<Reader>(originalReader);
            readerCloner = new ReaderCloner() {
                @Override public Reader giveAClone() {
                    return singleUsageReference.getAndSet(null);
                }
            };

        } else {

            // Try multiple optimized specialized ReaderCloner
            try {
                if (originalReader instanceof FastStringReader) {

                    @SuppressWarnings("unchecked") FastStringReader typedReader = (FastStringReader)originalReader;
                    readerCloner = new FastStringReaderCloner(typedReader);

                } else if (ReusableStringReaderCloner.canHandleReader(originalReader)) {

                    readerCloner = new ReusableStringReaderCloner(originalReader);

                } else if (originalReader instanceof StringReader) {

                    @SuppressWarnings("unchecked") StringReader typedReader = (StringReader)originalReader;
                    readerCloner = new StringReaderCloner(typedReader);

                } else if (originalReader instanceof FastCharArrayReader) {

                    @SuppressWarnings("unchecked") FastCharArrayReader typedReader = (FastCharArrayReader)originalReader;
                    readerCloner = new FastCharArrayReaderCloner(typedReader);

                }
            } catch (Exception ex) {
                logger.debug("Exception while trying to duplicate a known Reader subclass instance. Will fallback to the default implementation.", ex);
            }

            // In case we did not have the opportunity to use a specialized ReaderCloner, or we encountered an exception.
            // Use the fallback implementation
            if (readerCloner == null) {
                logger.debug("The reader is not an instance of a known class, it is a " + originalReader.getClass().getCanonicalName() + " and it cannot be used in an optimal manner!");
                try {
                    readerCloner = new ReaderClonerDefaultImpl(originalReader);
                } catch (IOException ex) {
                    logger.debug("Error while using last-resort duplication of an unknown Reader class", ex);
                }
            }

            // Even the default implementation failed, we cannot proceed
            if (readerCloner == null) {
                throw new ElasticSearchIllegalArgumentException("Could not duplicate the original reader to feed multiple sub-readers");
            }

        }

        // We remember last used TokenStreams because many times Analyzers can provide a reusable TokenStream
        // Detecting that all sub-TokenStreams are reusable permits to reuse our ComboTokenStream as well.
        if (tempTokenStreams == null) tempTokenStreams = new TokenStream[subAnalyzers.length]; // each time non reusability has been detected
        if (lastTokenStreams == null) lastTokenStreams = new TokenStream[subAnalyzers.length]; // only at first run

        // Get sub-TokenStreams from sub-analyzers
        for (int i = subAnalyzers.length-1 ; i >= 0 ; --i) {

            // Feed the troll
            Reader reader = readerCloner.giveAClone();
            // Try a reusable sub-TokenStream
            try {
                tempTokenStreams[i] = subAnalyzers[i].reusableTokenStream(fieldName, reader);
            } catch (IOException ex) {
                tempTokenStreams[i] = subAnalyzers[i].tokenStream(fieldName, reader);
            }
            // Detect non reusability
            if (tempTokenStreams[i] != lastTokenStreams[i]) {
                lastComboTokenStream = null;
            }

        }

        // If last ComboTokenStream is not available create a new one
        // This happens in the first call and in case of non reusability
        if (lastComboTokenStream == null) {
            // Swap temporary and last (non reusable) TokenStream references
            TokenStream[] tmp = lastTokenStreams;
            lastTokenStreams = tempTokenStreams;
            tempTokenStreams = tmp;
            // Clear old invalid references (preferred over allocating a new array)
            Arrays.fill(tempTokenStreams, null);
            // New ComboTokenStream to use
            lastComboTokenStream = new ComboTokenStream(lastTokenStreams);
        }
        return lastComboTokenStream;
    }

    /**
     * Read settings and load the appropriate sub-analyzers.
     */
    protected void init() {
        AnalysisService analysisService = injector.getInstance(AnalysisService.class);

        String[] sub = settings.getAsArray("sub_analyzers");
        ArrayList<Analyzer> subAnalyzers = new ArrayList<Analyzer>();
        if (sub == null) {
            throw new ElasticSearchIllegalArgumentException("\""+NAME+"\" analyzers must have a \"sub_analyzers\" list property");
        }

        for (String subname : sub) {
            NamedAnalyzer analyzer = analysisService.analyzer(subname);
            if (analyzer == null) {
                logger.debug("Sub-analyzer \""+subname+"\" not found!");
            } else {
                subAnalyzers.add(analyzer);
            }
        }

        this.subAnalyzers = subAnalyzers.toArray(new Analyzer[subAnalyzers.size()]);
    }

}

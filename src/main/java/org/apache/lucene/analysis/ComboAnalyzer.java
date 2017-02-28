/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.analysis;

import org.apache.lucene.analysis.miscellaneous.UniqueTokenFilter;
import org.apache.lucene.util.ReaderCloneFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An analyzer that combines multiple sub-analyzers into one.
 *
 * It internally uses {@link ReaderCloneFactory} in order to feed the multiple
 * sub-analyzers from a single input.
 * If you analyzer big inputs or have a performance critical application,
 * please see the remarks of the latter's documentation.
 *
 * The instances are thread safe with regards to the reused TokenStreams.
 * As duplicated sub-analyzer instance would break this safety,
 * there is a special case detection for it.
 * However, there can still be cases where an {@link Analyzer} is used from
 * multiple places at a time before the {@link ComboTokenStream} is fully consumed,
 * and this causes problem.
 * For a solution, see {@link #setTokenStreamCachingEnabled(boolean)}
 * and {@link #setTokenStreamCachingEnabled(boolean)}.
 */
public class ComboAnalyzer extends Analyzer {

    /**
     * Default value for the enabled state of {@link TokenStream} caching.
     */
    public static final boolean TOKENSTREAM_CACHING_ENABLED_DEFAULT = false;

    /**
     * Default value for the enabled state of token deduplication.
     */
    public static final boolean DEDUPLICATION_ENABLED_DEFAULT = false;

    private Analyzer[] subAnalyzers;

    private boolean hasDuplicatedAnalyzers;
    private Set<Analyzer> duplicatedAnalyzers;

    private boolean cacheTokenStreams = TOKENSTREAM_CACHING_ENABLED_DEFAULT;

    private boolean deduplication = DEDUPLICATION_ENABLED_DEFAULT;

    public ComboAnalyzer(Analyzer... subAnalyzers) {
        super();
        this.subAnalyzers = subAnalyzers;

        // Detect duplicates in analyzers
        // this prevents reusable token streams
        hasDuplicatedAnalyzers = false;
        duplicatedAnalyzers = new HashSet<Analyzer>();
        Set<Analyzer> seen = new HashSet<Analyzer>(subAnalyzers.length);
        for (Analyzer analyzer : subAnalyzers) {
            if (!seen.add(analyzer)) {
                hasDuplicatedAnalyzers = true;
                duplicatedAnalyzers.add(analyzer);
            }
        }
    }

    /**
     * Enable or disable the systematic caching of {@link Analyzer} {@link TokenStream}s.
     *
     * {@link TokenStream}s gotten from the {@link Analyzer}s will be cached upfront.
     * This helps with one of the {@link Analyzer}s being reused before having completely
     * consumed our {@link ComboTokenStream}.
     * Note that this can happen too, if the same {@link Analyzer} instance is given twice.
     *
     * @param value {@code true} to enable the caching of {@link TokenStream}s,
     *              {@code false} to disable it.
     *
     * @return This instance, for chainable construction.
     *
     * @see #TOKENSTREAM_CACHING_ENABLED_DEFAULT
     */
    public ComboAnalyzer setTokenStreamCachingEnabled(boolean value) {
        cacheTokenStreams = value;
        return this;
    }

    /**
     * Enable the systematic caching of {@link Analyzer} {@link TokenStream}s.
     * @return This instance, for chainable construction.
     * @see #setTokenStreamCachingEnabled(boolean)
     */
    public ComboAnalyzer enableTokenStreamCaching() {
        cacheTokenStreams = true;
        return this;
    }

    /**
     * Disable the systematic caching of {@link Analyzer} {@link TokenStream}s.
     * @return This instance, for chainable construction.
     * @see #setTokenStreamCachingEnabled(boolean)
     */
    public ComboAnalyzer disableTokenStreamCaching() {
        cacheTokenStreams = false;
        return this;
    }

    /**
     * Enable or disable deduplication of repeated tokens at the same position.
     *
     * @param value {@code true} to enable the deduplication of tokens,
     *              {@code false} to disable it.
     *
     * @return This instance, for chainable construction.
     *
     * @see #DEDUPLICATION_ENABLED_DEFAULT
     */
    public ComboAnalyzer setDeduplicationEnabled(boolean value) {
        deduplication = value;
        return this;
    }

    /**
     * Enable deduplication of repeated tokens at the same position.
     * @return This instance, for chainable construction.
     * @see #setDeduplicationEnabled(boolean)
     */
    public ComboAnalyzer enableDeduplication() {
        deduplication = true;
        return this;
    }

    /**
     * Disable deduplication of repeated tokens at the same position.
     * @return This instance, for chainable construction.
     * @see #setDeduplicationEnabled(boolean)
     */
    public ComboAnalyzer disableDeduplication() {
        deduplication = false;
        return this;
    }

    private static Tokenizer DUMMY_TOKENIZER = new Tokenizer(){
        @Override
        public boolean incrementToken() throws IOException {
            return false;
        }
    };

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        return new CombiningTokenStreamComponents(fieldName);
    }

    @Override public void close() {
        super.close();
    }

    private class CombiningTokenStreamComponents extends TokenStreamComponents {

        private final Map<Analyzer, CachingTokenStream> duplicateAnalyzers = new HashMap<Analyzer, CachingTokenStream>();
        private final String field;
        private Reader reader;

        public CombiningTokenStreamComponents(String field) {
            super(DUMMY_TOKENIZER);
            this.field = field;
        }

        @Override
        public void setReader(Reader reader) {
            duplicateAnalyzers.clear();
            this.reader = reader;
        }

        @Override
        public TokenStream getTokenStream() {
            TokenStream ret = createTokenStreams();
            return deduplication ? new UniqueTokenFilter(ret): ret;
        }

        private TokenStream createTokenStreams() {
            if(subAnalyzers.length == 1){
                return createTokenStream(subAnalyzers[0], field, reader);
            }
            else{
                ReaderCloneFactory.ReaderCloner<Reader> cloner = ReaderCloneFactory.getCloner(reader);
                TokenStream[] streams = new TokenStream[subAnalyzers.length];
                for (int i = 0; i < subAnalyzers.length; i++) {
                    streams[i] = createTokenStream(subAnalyzers[i], field, cloner.giveAClone());
                }
                return new ComboTokenStream(streams);
            }
        }

        private TokenStream createTokenStream(Analyzer analyzer, String field, Reader reader)  {
            try {
                if(hasDuplicatedAnalyzers && duplicatedAnalyzers.contains(analyzer)) {
                    return createCachedCopies(analyzer, field, reader);
                }
                else if(cacheTokenStreams){
                    return loadAndClose(analyzer.tokenStream(field, reader));
                }
                else{
                    return  analyzer.tokenStream(field, reader);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private TokenStream createCachedCopies(Analyzer analyzer, String field ,Reader reader) throws IOException {
            //First time we see this analyzer, means that we have to cache the content
            if(!duplicateAnalyzers.containsKey(analyzer)){
                CachingTokenStream caching = loadAndClose(analyzer.tokenStream(field, reader));
                duplicateAnalyzers.put(analyzer, caching);
                return caching;
            }
            else{
                //Already seen, can just create a new copy of the cached
                return loadAsCaching(duplicateAnalyzers.get(analyzer));
            }
        }

        private CachingTokenStream loadAndClose(TokenStream tokenStream) {
            CachingTokenStream cache = loadAsCaching(tokenStream);
            try{
                tokenStream.close();
            }
            catch (IOException e){
                throw new RuntimeException(e);
            }
            return cache;
        }

        private CachingTokenStream loadAsCaching(TokenStream tokenStream) {
            try{
                CachingTokenStream cachingTokenStream = new CachingTokenStream(tokenStream);
                tokenStream.reset();
                cachingTokenStream.fillCache();
                return cachingTokenStream;
            }
            catch (Exception e){
                throw new RuntimeException(e);
            }
        }
    }
}

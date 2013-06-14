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
import org.apache.lucene.util.CloseableThreadLocal;
import org.apache.lucene.util.ReaderCloneFactory;
import org.apache.lucene.util.Version;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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

    protected static final ESLogger logger = ESLoggerFactory.getLogger(ComboAnalyzer.class.getSimpleName());

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

    private CloseableThreadLocal<TokenStream[]> lastTokenStreams = new CloseableThreadLocal<TokenStream[]>();
    private CloseableThreadLocal<TokenStream[]> tempTokenStreams = new CloseableThreadLocal<TokenStream[]>();
    private CloseableThreadLocal<ReusableTokenStreamComponents> lastComboTokenStream = new CloseableThreadLocal<ReusableTokenStreamComponents>();

    public ComboAnalyzer(Version version, Analyzer... subAnalyzers) {
        super(new GlobalReuseStrategy());

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

    protected ReaderCloneFactory.ReaderCloner<? extends Reader> cloneReader(Reader originalReader) {
        ReaderCloneFactory.ReaderCloner<? extends Reader> rtn;

        // Duplication of the original reader, to feed all sub-analyzers
        if (subAnalyzers.length <= 1) {

            // Can reuse the only reader we have, there will be no need of duplication
            // Usage of the AtomicReference ensures that the same reader won't be duplicated.
            ReaderCloneFactory.ReaderCloner<Reader> useOnceReaderCloner = new ReaderCloneFactory.ReaderCloner<Reader>() {
                private AtomicReference<Reader> singleUsageReference = null;
                public void init(Reader originalReader) throws IOException {
                    singleUsageReference = new AtomicReference<Reader>(originalReader);
                }
                public Reader giveAClone() {
                    return singleUsageReference.getAndSet(null);
                }
            };
            try {
                useOnceReaderCloner.init(originalReader);
            } catch (Throwable fail) {
                useOnceReaderCloner = null;
            }
            rtn = useOnceReaderCloner;

        } else {

            rtn = ReaderCloneFactory.getCloner(originalReader); // internally uses the default "should always work" implementation

        }

        if (rtn == null) {
            throw new IllegalArgumentException("Could not duplicate the original reader to feed multiple sub-readers");
        }
        return rtn;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName, Reader originalReader) {
        // Duplication of the original reader, to feed all sub-analyzers
        ReaderCloneFactory.ReaderCloner readerCloner = cloneReader(originalReader);

        // We remember last used TokenStreams because many times Analyzers can provide a reusable TokenStream
        // Detecting that all sub-TokenStreams are reusable permits to reuse our ComboTokenStream as well.
        if (tempTokenStreams.get() == null) tempTokenStreams.set(new TokenStream[subAnalyzers.length]); // each time non reusability has been detected
        if (lastTokenStreams.get() == null) lastTokenStreams.set(new TokenStream[subAnalyzers.length]); // only at first run
        TokenStream[] tempTokenStreams_local = tempTokenStreams.get();
        TokenStream[] lastTokenStreams_local = lastTokenStreams.get();
        ReusableTokenStreamComponents lastComboTokenStream_local = lastComboTokenStream.get();
        if (lastComboTokenStream_local == null)
            lastComboTokenStream_local = new ReusableTokenStreamComponents(fieldName, this);

        // Get sub-TokenStreams from sub-analyzers
        for (int i = subAnalyzers.length-1 ; i >= 0 ; --i) {

            // Feed the troll
            Reader reader = readerCloner.giveAClone();
            tempTokenStreams_local[i] = null;
            try {
                tempTokenStreams_local[i] = subAnalyzers[i].tokenStream(fieldName, reader);
            } catch (IOException ignored) {
                logger.debug("Ignoring {}th analyzer [{}]. Could not get a TokenStream.", ignored, i, subAnalyzers[i]);
            }
            // Use caching if asked or if required in case of duplicated analyzers
            if (cacheTokenStreams || hasDuplicatedAnalyzers && duplicatedAnalyzers.contains(subAnalyzers[i])) {
                CachingTokenStream cache = new CachingTokenStream(tempTokenStreams_local[i]);
                try {
                    tempTokenStreams_local[i].reset();
                    cache.fillCache();
                } catch (IOException ignored) {
                    logger.debug("Got an error when caching TokenStream from the {}th analyzer [{}]", i, subAnalyzers[i]);
                }
                try {
                    // Close original stream, all tokens are buffered
                    tempTokenStreams_local[i].close();
                } catch (IOException ignored) {
                    logger.debug("Got an error when closing TokenStream from the {}th analyzer [{}]", i, subAnalyzers[i]);
                }
                tempTokenStreams_local[i] = cache;
            }
            // Detect non reusability
            if (tempTokenStreams_local[i] != lastTokenStreams_local[i]) {
                lastComboTokenStream_local.setTokenStream(null);
            }
        }

        // If last ComboTokenStream is not available create a new one
        // This happens in the first call and in case of non reusability
        if (lastComboTokenStream_local.getTokenStream() == null) {
            // Clear old invalid references (preferred over allocating a new array)
            Arrays.fill(lastTokenStreams_local, null);
            // Swap temporary and last (non reusable) TokenStream references
            lastTokenStreams.set(tempTokenStreams_local);
            tempTokenStreams.set(lastTokenStreams_local);
            // New ComboTokenStream to use
            lastComboTokenStream_local.setTokenStream(new ComboTokenStream(tempTokenStreams_local));
            if (deduplication)
                lastComboTokenStream_local.setTokenStream(new UniqueTokenFilter(lastComboTokenStream_local.getTokenStream(), true));
            lastComboTokenStream.set(lastComboTokenStream_local);
        }
        return lastComboTokenStream_local;
    }

    @Override public void close() {
        super.close();
        lastTokenStreams.close();
        tempTokenStreams.close();
        lastComboTokenStream.close();
    }

}

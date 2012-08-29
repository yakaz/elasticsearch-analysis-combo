package org.apache.lucene.analysis;

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

import org.apache.lucene.util.CloseableThreadLocal;
import org.apache.lucene.util.ReaderCloneFactory;
import org.apache.lucene.util.Version;

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
 * For a solution, see {@link ComboAnalyzer.setTokenStreamReuseEnabled(boolean)}
 * and {@link ComboAnalyzer.setTokenStreamCachingEnabled(boolean)}.
 */
public class ComboAnalyzer extends Analyzer {

    /**
     * Default value for the enabled state of {@link TokenStream} reuse.
     */
    public static final boolean TOKENSTREAM_REUSE_ENABLED_DEFAULT = false;
    /**
     * Default value for the enabled state of {@link TokenStream} caching.
     */
    public static final boolean TOKENSTREAM_CACHING_ENABLED_DEFAULT = false;

    private Analyzer[] subAnalyzers;

    private boolean disableTokenStreamReuse = TOKENSTREAM_REUSE_ENABLED_DEFAULT;
    private boolean hasDuplicatedAnalyzers;
    private Set<Analyzer> duplicatedAnalyzers;

    private boolean cacheTokenStreams = TOKENSTREAM_CACHING_ENABLED_DEFAULT;

    private CloseableThreadLocal<TokenStream[]> lastTokenStreams = new CloseableThreadLocal<TokenStream[]>();
    private CloseableThreadLocal<TokenStream[]> tempTokenStreams = new CloseableThreadLocal<TokenStream[]>();
    private CloseableThreadLocal<ComboTokenStream> lastComboTokenStream = new CloseableThreadLocal<ComboTokenStream>();

    public ComboAnalyzer(Version version, Analyzer... subAnalyzers) {
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
     * Enable or disable completely the reuse of {@link Analyzer} {@link TokenStream}s.
     *
     * Some analyzers have a true implementation of {@link Analyzer.reusableTokenStream(String,Reader)},
     * like those inheriting {@link ReusableAnalyzerBase}.
     * This is thread-safe by contract, but prevents the same {@link Analyzer} instance from providing
     * two {@link TokenStream} for simultaneous use.
     *
     * There is a support for multiple use of the same {@link Analyzer} instance in the list of analyzers
     * provided in the constructor, but there is no way of asserting that no other code will alter one of
     * the reused {@link TokenStream}s before the caller is done reading the {@link ComboTokenStream}.
     *
     * As it is a rather rare scenario, we provide a simple way of getting past those problems
     * when you see them: disabling the reuse of {@link TokenStream}s.
     *
     * @param value {@code false} to disable the reuse of {@link TokenStream}s,
     *              {@code true} to enable it.
     *
     * @return This instance, for chainable construction.
     *
     * @see ComboAnalyzer.TOKENSTREAM_REUSE_ENABLED_DEFAULT
     * @see ComboAnalyzer.setTokenStreamCachingEnabled(boolean)
     */
    public ComboAnalyzer setTokenStreamReuseEnabled(boolean value) {
        disableTokenStreamReuse = value;
        return this;
    }

    /**
     * Enable the reuse of {@link Analyzer} {@link TokenStream}s.
     * @return This instance, for chainable construction.
     * @see ComboAnalyzer.setTokenStreamReuseEnabled(boolean)
     */
    public ComboAnalyzer enableTokenStreamReuse() {
        disableTokenStreamReuse = false;
        return this;
    }

    /**
     * Disable completely the reuse of {@link Analyzer} {@link TokenStream}s.
     * @return This instance, for chainable construction.
     * @see ComboAnalyzer.setTokenStreamReuseEnabled(boolean)
     */
    public ComboAnalyzer disableTokenStreamReuse() {
        disableTokenStreamReuse = true;
        return this;
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
     * @see ComboAnalyzer.TOKENSTREAM_CACHING_ENABLED_DEFAULT
     * @see ComboAnalyzer.setTokenStreamReuseEnabled(boolean)
     */
    public ComboAnalyzer setTokenStreamCachingEnabled(boolean value) {
        cacheTokenStreams = value;
        return this;
    }

    /**
     * Enable the systematic caching of {@link Analyzer} {@link TokenStream}s.
     * @return This instance, for chainable construction.
     * @see ComboAnalyzer.setTokenStreamCachingEnabled(boolean)
     */
    public ComboAnalyzer enableTokenStreamCaching() {
        cacheTokenStreams = true;
        return this;
    }

    /**
     * Disable the systematic caching of {@link Analyzer} {@link TokenStream}s.
     * @return This instance, for chainable construction.
     * @see ComboAnalyzer.setTokenStreamCachingEnabled(boolean)
     */
    public ComboAnalyzer disableTokenStreamCaching() {
        cacheTokenStreams = false;
        return this;
    }

    @Override public final TokenStream tokenStream(String fieldName, Reader originalReader) {
        // Duplication of the original reader, to feed all sub-analyzers
        ReaderCloneFactory.ReaderCloner readerCloner = null;
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
            readerCloner = useOnceReaderCloner;

        } else {

            readerCloner = ReaderCloneFactory.getCloner(originalReader); // internally uses the default "should always work" implementation
            if (readerCloner == null) {
                throw new IllegalArgumentException("Could not duplicate the original reader to feed multiple sub-readers");
            }

        }

        // We remember last used TokenStreams because many times Analyzers can provide a reusable TokenStream
        // Detecting that all sub-TokenStreams are reusable permits to reuse our ComboTokenStream as well.
        if (tempTokenStreams.get() == null) tempTokenStreams.set(new TokenStream[subAnalyzers.length]); // each time non reusability has been detected
        if (lastTokenStreams.get() == null) lastTokenStreams.set(new TokenStream[subAnalyzers.length]); // only at first run
        TokenStream[] tempTokenStreams_local = tempTokenStreams.get();
        TokenStream[] lastTokenStreams_local = lastTokenStreams.get();
        ComboTokenStream lastComboTokenStream_local = lastComboTokenStream.get();

        // Get sub-TokenStreams from sub-analyzers
        Set<Analyzer> unusedDuplicated = hasDuplicatedAnalyzers ? new HashSet<Analyzer>(duplicatedAnalyzers) : null;
        for (int i = subAnalyzers.length-1 ; i >= 0 ; --i) {

            // Feed the troll
            Reader reader = readerCloner.giveAClone();
            boolean tryReusable = !disableTokenStreamReuse;
            if (!disableTokenStreamReuse && hasDuplicatedAnalyzers && duplicatedAnalyzers.contains(subAnalyzers[i])) {
                if (unusedDuplicated.contains(subAnalyzers[i])) {
                    // Only try reusable once for an analyzer instance used multiple times
                    unusedDuplicated.remove(subAnalyzers[i]);
                } else {
                    tryReusable = false;
                }
            }
            tempTokenStreams_local[i] = null;
            if (tryReusable) {
                // Try a reusable sub-TokenStream
                try {
                    tempTokenStreams_local[i] = subAnalyzers[i].reusableTokenStream(fieldName, reader);
                } catch (IOException ex) {
                }
            }
            if (tempTokenStreams_local[i] == null) {
                // Either forced to, or falls back to non-reusable
                tempTokenStreams_local[i] = subAnalyzers[i].tokenStream(fieldName, reader);
            }
            if (cacheTokenStreams) {
                CachingTokenFilter cache = new CachingTokenFilter(tempTokenStreams_local[i]);
                try {
                    // Force lazy initialization to take place
                    cache.incrementToken();
                    // Rewind to the beginning
                    cache.reset();
                } catch (IOException e) {
                }
                try {
                    // Close original stream, all tokens are buffered
                    tempTokenStreams_local[i].close();
                } catch (IOException e) {
                }
                tempTokenStreams_local[i] = cache;
            }
            // Detect non reusability
            if (tempTokenStreams_local[i] != lastTokenStreams_local[i]) {
                lastComboTokenStream_local = null;
            }
        }

        // If last ComboTokenStream is not available create a new one
        // This happens in the first call and in case of non reusability
        if (lastComboTokenStream_local == null) {
            // Clear old invalid references (preferred over allocating a new array)
            Arrays.fill(lastTokenStreams_local, null);
            // Swap temporary and last (non reusable) TokenStream references
            lastTokenStreams.set(tempTokenStreams_local);
            tempTokenStreams.set(lastTokenStreams_local);
            // New ComboTokenStream to use
            lastComboTokenStream_local = new ComboTokenStream(tempTokenStreams_local);
            lastComboTokenStream.set(lastComboTokenStream_local);
        }
        return lastComboTokenStream_local;
    }

    @Override
    public final TokenStream reusableTokenStream(String fieldName, Reader reader) throws IOException {
        return super.reusableTokenStream(fieldName, reader);
    }

    @Override public void close() {
        super.close();
        lastTokenStreams.close();
        tempTokenStreams.close();
        lastComboTokenStream.close();
    }
}

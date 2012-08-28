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
 */
public class ComboAnalyzer extends Analyzer {

    private Analyzer[] subAnalyzers;
    private CloseableThreadLocal<TokenStream[]> lastTokenStreams = new CloseableThreadLocal<TokenStream[]>();
    private CloseableThreadLocal<TokenStream[]> tempTokenStreams = new CloseableThreadLocal<TokenStream[]>();
    private CloseableThreadLocal<ComboTokenStream> lastComboTokenStream = new CloseableThreadLocal<ComboTokenStream>();

    public ComboAnalyzer(Version version, Analyzer... subAnalyzers) {
        this.subAnalyzers = subAnalyzers;
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
        for (int i = subAnalyzers.length-1 ; i >= 0 ; --i) {

            // Feed the troll
            Reader reader = readerCloner.giveAClone();
            // Try a reusable sub-TokenStream
            try {
                tempTokenStreams_local[i] = subAnalyzers[i].reusableTokenStream(fieldName, reader);
            } catch (IOException ex) {
                tempTokenStreams_local[i] = subAnalyzers[i].tokenStream(fieldName, reader);
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

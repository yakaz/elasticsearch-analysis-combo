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

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.elasticsearch.index.analysis.AnalyzerScope;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.hamcrest.CoreMatchers.equalTo;

/**
 * Testcase for {@link ComboAnalyzer}
 */
public class TestComboAnalyzer extends BaseTokenStreamTestCase {

    @Test
    public void testSingleAnalyzer() throws IOException {
        ComboAnalyzer cb = new ComboAnalyzer(new WhitespaceAnalyzer());
        for (int i = 0 ; i < 3 ; i++)
            assertTokenStreamContents(cb.tokenStream("field", new StringReader("just a little test "+i)),
                    new String[]{"just", "a", "little", "test", Integer.toString(i)},
                    new int[]{ 0,  5,  7, 14, 19},
                    new int[]{ 4,  6, 13, 18, 20},
                    new int[]{ 1,  1,  1,  1,  1});
    }

    @Test
    public void testMultipleAnalyzers() throws IOException {
        ComboAnalyzer cb = new ComboAnalyzer(
                new WhitespaceAnalyzer(),
                new StandardAnalyzer(),
                new KeywordAnalyzer()
        );
        for (int i = 0 ; i < 3 ; i++)
            assertTokenStreamContents(cb.tokenStream("field", new StringReader("just a little test "+i)),
                    new String[]{"just", "just", "just a little test "+i, "a", "little", "little", "test", "test", Integer.toString(i), Integer.toString(i)},
                    new int[]{ 0,  0,  0,  5,  7,  7, 14, 14, 19, 19},
                    new int[]{ 4,  4, 20,  6, 13, 13, 18, 18, 20, 20},
                    new int[]{ 1,  0,  0,  1,  1,  0,  1,  0,  1,  0});
    }

    @Test
    public void testMultipleAnalyzersDeduplication() throws IOException {
        ComboAnalyzer cb = new ComboAnalyzer(
                new WhitespaceAnalyzer(),
                new StandardAnalyzer(),
                new KeywordAnalyzer()
        );
        cb.enableDeduplication();
        for (int i = 0 ; i < 3 ; i++)
            assertTokenStreamContents(cb.tokenStream("field", new StringReader("just a little test "+i)),
                    new String[]{"just", "just a little test "+i, "a", "little", "test", Integer.toString(i)},
                    new int[]{ 0,   0,  5,  7, 14, 19},
                    new int[]{ 4,  20,  6, 13, 18, 20},
                    new int[]{ 1,   0,  1,  1,  1,  1});
    }

    @Test
    public void testCanReuseForDifferentReader() throws IOException {
        Analyzer analyzer = new WhitespaceAnalyzer();
        ComboAnalyzer cb = new ComboAnalyzer(
                analyzer,
                analyzer,
                analyzer
        );
        for (int i = 0 ; i < 3 ; i++) {
            assertTokenStreamContents(cb.tokenStream("field", new StringReader("just a little test " + i)),
                    new String[]{"just", "just", "just", "a", "a", "a", "little", "little", "little", "test", "test", "test", Integer.toString(i), Integer.toString(i), Integer.toString(i)},
                    new int[]{0, 0, 0, 5, 5, 5, 7, 7, 7, 14, 14, 14, 19, 19, 19},
                    new int[]{4, 4, 4, 6, 6, 6, 13, 13, 13, 18, 18, 18, 20, 20, 20},
                    new int[]{1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0});
            assertTokenStreamContents(cb.tokenStream("field", new StringReader("another little test " + i)),
                    new String[]{"another", "another", "another", "little", "little", "little", "test", "test", "test", Integer.toString(i), Integer.toString(i), Integer.toString(i)},
                    new int[]{0, 0, 0, 8, 8, 8, 15, 15, 15, 20, 20, 20},
                    new int[]{7, 7, 7, 14, 14, 14, 19, 19, 19, 21, 21, 21},
                    new int[]{1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0});
        }
    }

    @Test
    public void testThreeTimesTheSameAnalyzerInstance() throws IOException {
        Analyzer analyzer = new WhitespaceAnalyzer();
        ComboAnalyzer cb = new ComboAnalyzer(
                analyzer,
                analyzer,
                analyzer
        );
        for (int i = 0 ; i < 3 ; i++)
            assertTokenStreamContents(cb.tokenStream("field", new StringReader("just a little test "+i)),
                    new String[]{"just", "just", "just", "a", "a", "a", "little", "little", "little", "test", "test", "test", Integer.toString(i), Integer.toString(i), Integer.toString(i)},
                    new int[]{ 0,  0,  0,  5,  5, 5,  7,  7,  7, 14, 14, 14, 19, 19, 19},
                    new int[]{ 4,  4,  4,  6,  6, 6, 13, 13, 13, 18, 18, 18, 20, 20, 20},
                    new int[]{ 1,  0,  0,  1,  0, 0,  1,  0,  0,  1,  0,  0,  1,  0,  0});
    }

    @Test
    public void testCascadeCombo() throws IOException {
        ComboAnalyzer cb = new ComboAnalyzer(
                new ComboAnalyzer(
                        new WhitespaceAnalyzer(),
                        new KeywordAnalyzer()
                ),
                new StandardAnalyzer(),
                new KeywordAnalyzer()
        );
        for (int i = 0 ; i < 3 ; i++)
            assertTokenStreamContents(cb.tokenStream("field", new StringReader("just a little test "+i)),
                    new String[]{"just", "just", "just a little test "+i, "just a little test "+i, "a", "little", "little", "test", "test", Integer.toString(i), Integer.toString(i)},
                    new int[]{ 0,  0,  0,  0,  5,  7,  7, 14, 14, 19, 19},
                    new int[]{ 4,  4, 20, 20,  6, 13, 13, 18, 18, 20, 20},
                    new int[]{ 1,  0,  0,  0,  1,  1,  0,  1,  0,  1,  0});
    }

    @Test
    public void testCascadeComboTwiceSameInstanceSolvedByCaching() throws IOException {
        Analyzer analyzer = new KeywordAnalyzer();
        ComboAnalyzer cb = new ComboAnalyzer(
                new ComboAnalyzer(
                        new WhitespaceAnalyzer(),
                        analyzer
                ).enableTokenStreamCaching(),
                new StandardAnalyzer(),
                analyzer
        ).enableTokenStreamCaching();
        for (int i = 0 ; i < 3 ; i++)
            assertTokenStreamContents(cb.tokenStream("field", new StringReader("just a little test "+i)),
                    new String[]{"just", "just", "just a little test "+i, "just a little test "+i, "a", "little", "little", "test", "test", Integer.toString(i), Integer.toString(i)},
                    new int[]{ 0,  0,  0,  0,  5,  7,  7, 14, 14, 19, 19},
                    new int[]{ 4,  4, 20, 20,  6, 13, 13, 18, 18, 20, 20},
                    new int[]{ 1,  0,  0,  0,  1,  1,  0,  1,  0,  1,  0});
    }

    @Test
    public void testCanUseFromNamedAnalyzer() throws IOException {
        ComboAnalyzer cb = new ComboAnalyzer(new WhitespaceAnalyzer());
        NamedAnalyzer namedAnalyzer = new NamedAnalyzer("name", AnalyzerScope.INDEX, cb);
        for (int i = 0 ; i < 3 ; i++)
            assertTokenStreamContents(namedAnalyzer.tokenStream("field", new StringReader("just a little test " + i)),
                    new String[]{"just", "a", "little", "test", Integer.toString(i)},
                    new int[]{ 0,  5,  7, 14, 19},
                    new int[]{ 4,  6, 13, 18, 20},
                    new int[]{ 1,  1,  1,  1,  1});
    }

    @Test
    public void testReuseSequentialMultithreading() throws IOException, InterruptedException {
        // Create the analyzer
        final ComboAnalyzer cb = new ComboAnalyzer(new WhitespaceAnalyzer());
        final NamedAnalyzer namedAnalyzer = new NamedAnalyzer("name", AnalyzerScope.INDEX, cb);
        // Use N threads, each running M times
        Thread[] threads = new Thread[4];
        final int runs = 4;
        // The lock ensures only one thread is running at a given time
        final Lock lock = new ReentrantLock();
        // This integer ensures each thread runs with a different input
        // Inputs must not be exchanged from one thread to another during object reuse
        final AtomicInteger sequence = new AtomicInteger(0);
        final AtomicBoolean abort = new AtomicBoolean(false);
        // The barrier ensures that each thread gets a chance to execute, for each run
        // We must use extra care so that all threads can exit as soon as one fails
        final CyclicBarrier latch = new CyclicBarrier(threads.length);
        // Code executed on each thread
        Runnable code = new Runnable() {
            @Override
            public void run() {
                // Run multiple times before quitting
                for (int run = 0 ; run < runs ; ++run) {
                    try {
                        // Serialize runs
                        lock.lock();
                        // Get unique sequence number
                        int i = sequence.getAndIncrement();
                        // Check the analysis went well, including the unique sequence number
                        assertTokenStreamContents(namedAnalyzer.tokenStream("field", new StringReader("just a little test " + i)),
                                new String[]{"just", "a", "little", "test", Integer.toString(i)},
                                new int[]{0, 5, 7, 14, 19},
                                new int[]{4, 6, 13, 18, 19 + ("" + i).length()},
                                new int[]{1, 1, 1, 1, 1});
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Make other fail,
                        abort.set(true); // if they will soon be waiting,
                        latch.reset(); // and if they are already waiting
                        // Now we can fail!
                        assertNull(e);
                    } finally {
                        lock.unlock();
                    }
                    // Wait for other threads, so calls are well interleaved between threads
                    try {
                        if (abort.get()) return;
                        latch.await();
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Make other fail,
                        abort.set(true); // if they will soon be waiting,
                        latch.reset(); // and if they are already waiting
                        // Now we can fail!
                        assertNull(e);
                    }
                }
            }
        };
        // Create the threads
        for (int i = 0 ; i < threads.length ; i++)
            threads[i] = new Thread(code);
        // Start the threads
        for (int i = 0 ; i < threads.length ; i++)
            threads[i].start();
        // Wait for completion
        for (int i = 0 ; i < threads.length ; i++)
            threads[i].join();
        // Ensure all desired runs have been performed
        assertThat(abort.get(), equalTo(false));
        assertThat(sequence.get(), equalTo(runs * threads.length));
    }

    @Test
    public void testReuseConcurrentMultithreading() throws IOException, InterruptedException {
        // Create the analyzer
        final ComboAnalyzer cb = new ComboAnalyzer(new WhitespaceAnalyzer());
        final NamedAnalyzer namedAnalyzer = new NamedAnalyzer("name", AnalyzerScope.INDEX, cb);
        // Use N threads, each running M times
        Thread[] threads = new Thread[4];
        final int runs = 4000; // leave time for threads to run concurrently
        // This integer ensures each thread runs with a different input
        // Inputs must not be exchanged from one thread to another during object reuse
        final AtomicInteger sequence = new AtomicInteger(0);
        // The barrier ensures that each thread gets a chance to execute, for each run
        final CyclicBarrier latch = new CyclicBarrier(threads.length);
        // Code executed on each thread
        Runnable code = new Runnable() {
            @Override
            public void run() {
                try {
                    latch.await();
                    // Run multiple times before quitting
                    for (int run = 0 ; run < runs ; ++run) {
                        // Get unique sequence number
                        int i = sequence.getAndIncrement();
                        // Check the analysis went well, including the unique sequence number
                        assertTokenStreamContents(namedAnalyzer.tokenStream("field", new StringReader("just a little test " + i)),
                                new String[]{"just", "a", "little", "test", Integer.toString(i)},
                                new int[]{0, 5, 7, 14, 19},
                                new int[]{4, 6, 13, 18, 19 + ("" + i).length()},
                                new int[]{1, 1, 1, 1, 1});
                    }
                } catch (Exception e) {
                    // Fail!
                    assertNull(e);
                }
            }
        };
        // Create the threads
        for (int i = 0 ; i < threads.length ; i++)
            threads[i] = new Thread(code);
        // Start the threads
        for (int i = 0 ; i < threads.length ; i++)
            threads[i].start();
        // Wait for completion
        for (int i = 0 ; i < threads.length ; i++)
            threads[i].join();
        // Ensure all desired runs have been performed
        assertThat(sequence.get(), equalTo(runs * threads.length));
    }

}

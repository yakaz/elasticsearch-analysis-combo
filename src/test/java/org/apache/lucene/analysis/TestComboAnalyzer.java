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
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.StringReader;

/**
 * Testcase for {@link ComboAnalyzer}
 */
@Test
public class TestComboAnalyzer extends BaseTokenStreamTestCase {

    @Test
    public void testSingleAnalyzer() throws IOException {
        ComboAnalyzer cb = new ComboAnalyzer(TEST_VERSION_CURRENT, new WhitespaceAnalyzer(TEST_VERSION_CURRENT));
        for (int i = 0 ; i < 3 ; i++)
            assertTokenStreamContents(cb.tokenStream("field", new StringReader("just a little test "+i)),
                    new String[]{"just", "a", "little", "test", Integer.toString(i)},
                    new int[]{ 0,  5,  7, 14, 19},
                    new int[]{ 4,  6, 13, 18, 20},
                    new int[]{ 1,  1,  1,  1,  1});
    }

    @Test
    public void testMultipleAnalyzers() throws IOException {
        ComboAnalyzer cb = new ComboAnalyzer(TEST_VERSION_CURRENT,
                new WhitespaceAnalyzer(TEST_VERSION_CURRENT),
                new StandardAnalyzer(TEST_VERSION_CURRENT),
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
        ComboAnalyzer cb = new ComboAnalyzer(TEST_VERSION_CURRENT,
                new WhitespaceAnalyzer(TEST_VERSION_CURRENT),
                new StandardAnalyzer(TEST_VERSION_CURRENT),
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
    public void testThreeTimesTheSameAnalyzerInstance() throws IOException {
        Analyzer analyzer = new WhitespaceAnalyzer(TEST_VERSION_CURRENT);
        ComboAnalyzer cb = new ComboAnalyzer(TEST_VERSION_CURRENT,
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
        ComboAnalyzer cb = new ComboAnalyzer(TEST_VERSION_CURRENT,
                new ComboAnalyzer(TEST_VERSION_CURRENT,
                        new WhitespaceAnalyzer(TEST_VERSION_CURRENT),
                        new KeywordAnalyzer()
                ),
                new StandardAnalyzer(TEST_VERSION_CURRENT),
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
        ComboAnalyzer cb = new ComboAnalyzer(TEST_VERSION_CURRENT,
                new ComboAnalyzer(TEST_VERSION_CURRENT,
                        new WhitespaceAnalyzer(TEST_VERSION_CURRENT),
                        analyzer
                ).enableTokenStreamCaching(),
                new StandardAnalyzer(TEST_VERSION_CURRENT),
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
        ComboAnalyzer cb = new ComboAnalyzer(TEST_VERSION_CURRENT, new WhitespaceAnalyzer(TEST_VERSION_CURRENT));
        NamedAnalyzer namedAnalyzer = new NamedAnalyzer("name", cb);
        for (int i = 0 ; i < 3 ; i++)
            assertTokenStreamContents(namedAnalyzer.tokenStream("field", new StringReader("just a little test "+i)),
                    new String[]{"just", "a", "little", "test", Integer.toString(i)},
                    new int[]{ 0,  5,  7, 14, 19},
                    new int[]{ 4,  6, 13, 18, 20},
                    new int[]{ 1,  1,  1,  1,  1});
    }
}

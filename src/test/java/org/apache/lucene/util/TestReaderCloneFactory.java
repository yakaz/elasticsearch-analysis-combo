package org.apache.lucene.util;

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

import org.testng.annotations.Test;

import javax.io.StringReaderCloner;
import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;

import static javax.util.ReaderContent.assertReaderContent;

/**
 * Testcase for {@link ReaderCloneFactory}.
 */
@Test
public class TestReaderCloneFactory extends LuceneTestCase {

    @Test
    public void testCloningStringReader() throws IOException {
        StringReader reader = new StringReader("test string");
        ReaderCloneFactory.ReaderCloner<Reader> cloner = ReaderCloneFactory.getCloner(reader);
        assertNotNull(cloner);
        assertEquals(cloner.getClass().getName(), StringReaderCloner.class.getName());
        Reader clone;
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");

        // Test reusability
        ReaderCloneFactory.ReaderCloner<StringReader> forClassClonerStrict = ReaderCloneFactory.getClonerStrict(StringReader.class);
        assertNotNull(forClassClonerStrict);
        assertEquals(forClassClonerStrict.getClass().getName(), StringReaderCloner.class.getName());
        forClassClonerStrict.init(new StringReader("test string"));
        clone = forClassClonerStrict.giveAClone();
        assertReaderContent(clone, "test string");
        clone = forClassClonerStrict.giveAClone();
        assertReaderContent(clone, "test string");
        forClassClonerStrict.init(new StringReader("another test string"));
        clone = forClassClonerStrict.giveAClone();
        assertReaderContent(clone, "another test string");
        clone = forClassClonerStrict.giveAClone();
        assertReaderContent(clone, "another test string");

        ReaderCloneFactory.ReaderCloner<Reader> forClassCloner = ReaderCloneFactory.getCloner(StringReader.class);
        assertNotNull(forClassCloner);
        assertEquals(forClassCloner.getClass().getName(), StringReaderCloner.class.getName());
        forClassCloner.init(new StringReader("test string"));
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "test string");
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "test string");
        forClassCloner.init(new StringReader("another test string"));
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "another test string");
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "another test string");
    }

    @Test
    public void testCloningCharArrayReader() throws IOException {
        CharArrayReader reader = new CharArrayReader("test string".toCharArray());
        ReaderCloneFactory.ReaderCloner<Reader> cloner = ReaderCloneFactory.getCloner(reader);
        assertNotNull(cloner);
        assertEquals(cloner.getClass().getName(), CharArrayReaderCloner.class.getName());
        Reader clone;
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");

        // Test reusability
        ReaderCloneFactory.ReaderCloner<CharArrayReader> forClassClonerStrict = ReaderCloneFactory.getClonerStrict(CharArrayReader.class);
        assertNotNull(forClassClonerStrict);
        assertEquals(cloner.getClass().getName(), CharArrayReaderCloner.class.getName());
        forClassClonerStrict.init(new CharArrayReader("test string".toCharArray()));
        clone = forClassClonerStrict.giveAClone();
        assertReaderContent(clone, "test string");
        clone = forClassClonerStrict.giveAClone();
        assertReaderContent(clone, "test string");
        forClassClonerStrict.init(new CharArrayReader("another test string".toCharArray()));
        clone = forClassClonerStrict.giveAClone();
        assertReaderContent(clone, "another test string");
        clone = forClassClonerStrict.giveAClone();
        assertReaderContent(clone, "another test string");

        ReaderCloneFactory.ReaderCloner<Reader> forClassCloner = ReaderCloneFactory.getCloner(CharArrayReader.class);
        assertNotNull(forClassCloner);
        assertEquals(cloner.getClass().getName(), CharArrayReaderCloner.class.getName());
        forClassCloner.init(new CharArrayReader("test string".toCharArray()));
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "test string");
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "test string");
        forClassCloner.init(new CharArrayReader("another test string".toCharArray()));
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "another test string");
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "another test string");
    }

    @Test
    public void testCloningBufferedStringReader() throws IOException {
        // The (useless) BufferedReader should be unwrapped, and a StringReaderCloner should be returned
        StringReader stringReader = new StringReader("test string");
        BufferedReader reader = new BufferedReader(stringReader);
        ReaderCloneFactory.ReaderCloner<Reader> cloner = ReaderCloneFactory.getCloner(reader);
        assertNotNull(cloner);
        assertEquals(cloner.getClass().getName(), StringReaderCloner.class.getName());
        Reader clone;
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");

        // Test reusability (does not use unwrapping)
        ReaderCloneFactory.ReaderCloner<BufferedReader> forClassClonerStrict = ReaderCloneFactory.getClonerStrict(BufferedReader.class);
        assertNull(forClassClonerStrict);

        ReaderCloneFactory.ReaderCloner<Reader> forClassCloner = ReaderCloneFactory.getCloner(BufferedReader.class);
        assertNotNull(forClassCloner);
        assertEquals(forClassCloner.getClass().getName(), ReaderClonerDefaultImpl.class.getName());
        forClassCloner.init(new BufferedReader(new StringReader("test string")));
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "test string");
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "test string");
        forClassCloner.init(new BufferedReader(new StringReader("another test string")));
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "another test string");
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "another test string");
    }

    @Test
    public void testCloningFilterStringReader() throws IOException {
        // The (useless) FilterReader should be unwrapped, and a StringReaderCloner should be returned
        StringReader stringReader = new StringReader("test string");
        FilterReader reader = new PushbackReader(stringReader);
        ReaderCloneFactory.ReaderCloner<Reader> cloner = ReaderCloneFactory.getCloner(reader);
        assertNotNull(cloner);
        assertEquals(cloner.getClass().getName(), StringReaderCloner.class.getName());
        Reader clone;
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");

        // Test reusability (does not use unwrapping)
        ReaderCloneFactory.ReaderCloner<FilterReader> forClassClonerStrict = ReaderCloneFactory.getClonerStrict(FilterReader.class);
        assertNull(forClassClonerStrict);

        ReaderCloneFactory.ReaderCloner<Reader> forClassCloner = ReaderCloneFactory.getCloner(FilterReader.class);
        assertNotNull(forClassCloner);
        assertEquals(forClassCloner.getClass().getName(), ReaderClonerDefaultImpl.class.getName());
        forClassCloner.init(new BufferedReader(new StringReader("test string")));
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");
        forClassCloner.init(new BufferedReader(new StringReader("another test string")));
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "another test string");
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "another test string");
    }

    @Test
    public void testCloningAnonymousReader() throws IOException {
        final StringReader delegated1 = new StringReader("test string");
        Reader reader = new Reader() {
            @Override public int read(char[] cbuf, int off, int len) throws IOException {
                return delegated1.read(cbuf, off, len);
            }
            @Override public void close() throws IOException {
                delegated1.close();
            }
        };
        ReaderCloneFactory.ReaderCloner<Reader> cloner = ReaderCloneFactory.getCloner(reader);
        assertEquals(cloner.getClass().getName(), ReaderClonerDefaultImpl.class.getName());
        Reader clone;
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");

        // Test reusability (does not use unwrapping)
        ReaderCloneFactory.ReaderCloner/*<unspecifiable anonymous-class type>*/ forClassClonerStrict = ReaderCloneFactory.getClonerStrict(reader.getClass());
        assertNull(forClassClonerStrict);

        ReaderCloneFactory.ReaderCloner<Reader> forClassCloner = ReaderCloneFactory.getCloner(reader.getClass());
        assertNotNull(forClassCloner);
        assertEquals(forClassCloner.getClass().getName(), ReaderClonerDefaultImpl.class.getName());
        final StringReader delegated2 = new StringReader("test string");
        Reader reader2 = new Reader() {
            @Override public int read(char[] cbuf, int off, int len) throws IOException {
                return delegated2.read(cbuf, off, len);
            }
            @Override public void close() throws IOException {
                delegated2.close();
            }
        };
        forClassCloner.init(reader2);
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");
        final StringReader delegated3 = new StringReader("another test string");
        Reader reader3 = new Reader() {
            @Override public int read(char[] cbuf, int off, int len) throws IOException {
                return delegated3.read(cbuf, off, len);
            }
            @Override public void close() throws IOException {
                delegated3.close();
            }
        };
        forClassCloner.init(reader3);
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "another test string");
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "another test string");
    }

}

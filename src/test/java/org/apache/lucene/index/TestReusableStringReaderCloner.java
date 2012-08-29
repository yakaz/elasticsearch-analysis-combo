package org.apache.lucene.index;

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

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.ReaderCloneFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.Reader;

import static javax.util.ReaderContent.assertReaderContent;

/**
 * Testcase for {@link ReusableStringReaderCloner}
 */
@Test
public class TestReusableStringReaderCloner extends BaseTokenStreamTestCase {

    @Test
    public void testCloningReusableStringReader() throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InstantiationException {
        // This test cannot be located inside TestReaderCloneFactory
        // because of the ReusableStringReader class being package private
        // (and it's a real pain to use Java reflection to gain access to
        //  a package private constructor)
        Reader clone;
        ReusableStringReader reader = new ReusableStringReader();
        reader.init("test string");
        ReaderCloneFactory.ReaderCloner<Reader> cloner = ReaderCloneFactory.getCloner(reader);
        assertNotNull(cloner);
        assertEquals(cloner.getClass().getName(), ReusableStringReaderCloner.class.getName());
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");
        clone = cloner.giveAClone();
        assertReaderContent(clone, "test string");

        // Test reusability
        ReaderCloneFactory.ReaderCloner<ReusableStringReader> forClassClonerStrict = ReaderCloneFactory.getClonerStrict(ReusableStringReader.class);
        assertNotNull(forClassClonerStrict);
        assertEquals(forClassClonerStrict.getClass().getName(), ReusableStringReaderCloner.class.getName());
        reader.init("another test string");
        forClassClonerStrict.init(reader);
        clone = forClassClonerStrict.giveAClone();
        assertReaderContent(clone, "another test string");
        clone = forClassClonerStrict.giveAClone();
        assertReaderContent(clone, "another test string");
        reader.init("test string");
        forClassClonerStrict.init(reader);
        clone = forClassClonerStrict.giveAClone();
        assertReaderContent(clone, "test string");
        clone = forClassClonerStrict.giveAClone();
        assertReaderContent(clone, "test string");

        ReaderCloneFactory.ReaderCloner<Reader> forClassCloner = ReaderCloneFactory.getCloner(ReusableStringReader.class);
        assertNotNull(forClassCloner);
        assertEquals(forClassCloner.getClass().getName(), ReusableStringReaderCloner.class.getName());
        reader.init("another test string");
        forClassCloner.init(reader);
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "another test string");
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "another test string");
        reader.init("test string");
        forClassCloner.init(reader);
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "test string");
        clone = forClassCloner.giveAClone();
        assertReaderContent(clone, "test string");
    }

}

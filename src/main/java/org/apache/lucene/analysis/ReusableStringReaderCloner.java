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

package org.apache.lucene.analysis;

import org.apache.lucene.util.ReaderCloneFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * A ReaderCloner specialized in duplicating Lucene's {@link org.apache.lucene.analysis.ReusableStringReader}.
 *
 * As this class is package private, this cloner has an additional function
 * to perform an {@code instanceof} check for you.
 *
 * The implementation exploits the fact that ReusableStringReader has a package
 * private field {@code String s}, storing the original content.
 * It is therefore sensitive to Lucene implementation changes.
 */
public class ReusableStringReaderCloner implements ReaderCloneFactory.ReaderCloner<ReusableStringReader> {

    private static java.lang.reflect.Field internalField;

    private ReusableStringReader original;
    private String originalContent;

    static {
        try {
            internalField = ReusableStringReader.class.getDeclaredField("s");
            internalField.setAccessible(true);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not give accessibility to private \"str\" field of the given StringReader", ex);
        }
    }

    /**
     * Binds this ReaderCloner with the package-private {@link ReusableStringReader} class
     * into the {@link ReaderCloneFactory}, without giving access to the hidden class.
     */
    public static void registerCloner() {
        ReaderCloneFactory.bindCloner(ReusableStringReader.class, ReusableStringReaderCloner.class);
    }

    /**
     * @param originalReader Must pass the canHandleReader(Reader) test, otherwise an IllegalArgumentException will be thrown.
     */
    public void init(ReusableStringReader originalReader) throws IOException {
        this.original = originalReader;
        this.originalContent = null;
        try {
            this.originalContent = (String) internalField.get(original);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not access private \"s\" field of the given org.apache.lucene.document.ReusableStringReader (actual class: "+original.getClass().getCanonicalName()+")", ex);
        }
    }

    /**
     * First call will return the original Reader provided.
     */
    @Override public Reader giveAClone() {
        if (original != null) {
            Reader rtn = original;
            original = null; // no longer hold a reference
            return rtn;
        }
        return new StringReader(originalContent);
    }

}

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

package org.apache.lucene.index;

import javax.io.ReaderCloner;
import java.io.Reader;
import java.io.StringReader;

/**
 * A ReaderCloner specialized in duplicating Lucene's {@link ReusableStringReader}.
 *
 * As this class is package private, this cloner has an additional function
 * to perform an {@code instanceof} check for you.
 *
 * The implementation exploits the fact that ReusableStringReader has a package
 * private field {@code String s}, storing the original content.
 * It is therefore sensitive to Lucene implementation changes.
 *
 * @author ofavre
 */
public class ReusableStringReaderCloner implements ReaderCloner {

    private ReusableStringReader original;
    private String originalContent;

    /**
     * Checks the given reader to be an instance of {@link org.apache.lucene.index.ReusableStringReader}.
     * which is a package private class, hence the need for this externalized check.
     * @param reader The initial reader that you which to duplicate using this ReaderCloner.
     * @return true if the reader is supported and can be supplied to the constructor.
     */
    public static boolean canHandleReader(Reader reader) {
        return reader instanceof ReusableStringReader;
    }

    /**
     * @param original Must pass the canHandleReader(Reader) test, otherwise an IllegalArgumentException will be thrown.
     */
    public ReusableStringReaderCloner(Reader original) throws IllegalArgumentException {
        if (!canHandleReader(original)) throw new IllegalArgumentException("The provided argument is not an instance of org.apache.lucene.index.ReusableStringReader, and thus cannot be handled properly by ReusableStringReaderCloner");
        @SuppressWarnings("unchecked") ReusableStringReader typedOriginal = (ReusableStringReader)original;
        this.original = typedOriginal;
        try {
            // Exploit package private access to the original String
            this.originalContent = typedOriginal.s;
        } catch (Throwable ex) { // Extra sanity check in case the implementation changes, and this class still can be used
            throw new IllegalArgumentException("The org.apache.lucene.index.ReusableStringReader no longer propose an access to the package private String s field, please consider updating this class to a newer version or use a fallback ReaderCloner");
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

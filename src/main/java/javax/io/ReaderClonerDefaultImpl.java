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

// Using javax instead of java because of JVM security measures!
package javax.io;

import org.elasticsearch.common.io.FastStringReader;

import java.io.IOException;
import java.io.Reader;

/**
 * Default, memory costly but generic implementation of a {@link Reader} duplicator.
 *
 * This implementation makes no assumption on the initial Reader.
 * Therefore, only the read() functions are available to figure out
 * what was the original content provided to the initial Reader.
 *
 * After having read and filled a buffer with the whole content,
 * a String-based Reader implementation will be used and returned.
 *
 * This implementation is memory costly because the initial content is
 * forcefully duplicated once. Moreover, buffer size growth may cost
 * some more memory too.
 *
 * @author ofavre
 */
public class ReaderClonerDefaultImpl implements ReaderCloner {

    public static final int DEFAULT_INITIAL_CAPACITY = 64 * 1024;
    public static final int DEFAULT_READ_BUFFER_SIZE = 16 * 1024;

    private String originalContent;

    public ReaderClonerDefaultImpl(Reader original) throws IOException {
        this(original, DEFAULT_INITIAL_CAPACITY, DEFAULT_READ_BUFFER_SIZE);
    }

    public ReaderClonerDefaultImpl(Reader original, int initialCapacity) throws IOException {
        this(original, initialCapacity, DEFAULT_READ_BUFFER_SIZE);
    }

    /**
     * Extracts the original content from a generic Reader instance
     * by repeatedly calling {@link Reader.read(char[])} on it,
     * feeding a {@link StringBuilder}.
     *
     * @param original          Initial Reader to be duplicated
     * @param initialCapacity   Initial StringBuilder capacity
     * @param readBufferSize    Size of the char[] read buffer at each read() call
     * @throws IOException
     */
    public ReaderClonerDefaultImpl(Reader original, int initialCapacity, int readBufferSize) throws IOException {
        StringBuilder sb = null;
        if (initialCapacity < 0)
            sb = new StringBuilder();
        else
            sb = new StringBuilder(initialCapacity);
        char[] buffer = new char[readBufferSize];
        int read = -1;
        while((read = original.read(buffer)) != -1){
            sb.append(buffer, 0, read);
        }
        this.originalContent = sb.toString();
    }

    /**
     * Returns a new {@link FastStringReader} instance,
     * directly based on the extracted original content.
     * @return A {@link FastStringReader}
     */
    @Override public Reader giveAClone() {
        return new FastStringReader(originalContent);
    }

}

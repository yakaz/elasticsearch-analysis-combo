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

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;

/**
 * A ReaderCloner specialized for CharArrayReader.
 *
 * The only efficient mean of retrieving the original content
 * from a CharArrayReader is to use introspection and access the
 * {@code private String str} field.
 *
 * Apart from being efficient, this code is also very sensitive
 * to the used JVM implementation.
 * If the introspection does not work, an {@link IllegalArgumentException}
 * is thrown.
 *
 * @author ofavre
 */
public class CharArrayReaderCloner implements ReaderCloneFactory.ReaderCloner<CharArrayReader> {

    private static Field internalField;

    private CharArrayReader original;
    private char[] originalContent;

    static {
        try {
            internalField = CharArrayReader.class.getDeclaredField("buf");
            internalField.setAccessible(true);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not give accessibility to private \"buf\" field of the given CharArrayReader", ex);
        }
    }

    public void init(CharArrayReader originalReader) throws IOException {
        this.original = originalReader;
        this.originalContent = null;
        try {
            this.originalContent = (char[]) internalField.get(original);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not access private \"buf\" field of the given CharArrayReader (actual class: "+original.getClass().getCanonicalName()+")", ex);
        }
    }

    /**
     * First call will return the original Reader provided.
     */
    public Reader giveAClone() {
        if (original != null) {
            Reader rtn = original;
            original = null; // no longer hold a reference
            return rtn;
        }
        return new CharArrayReader(originalContent);
    }

}

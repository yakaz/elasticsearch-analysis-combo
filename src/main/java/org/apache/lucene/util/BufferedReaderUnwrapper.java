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

import java.io.BufferedReader;
import java.io.Reader;
import java.lang.reflect.Field;

/**
 * A {@link java.io.BufferedReader} ReaderUnwrapper that
 * returns the Reader wrapped inside the BufferReader.
 *
 * @author ofavre
 */
public class BufferedReaderUnwrapper implements ReaderCloneFactory.ReaderUnwrapper<BufferedReader> {

    private static Field internalField;

    static {
        try {
            internalField = BufferedReader.class.getDeclaredField("in");
            internalField.setAccessible(true);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not give accessibility to private \"in\" field of the given BufferedReader", ex);
        }
    }

    public Reader unwrap(BufferedReader originalReader) throws IllegalArgumentException {
        try {
            return (Reader) internalField.get(originalReader);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not access private \"in\" field of the given BufferedReader (actual class: "+originalReader.getClass().getCanonicalName()+")", ex);
        }
    }

}

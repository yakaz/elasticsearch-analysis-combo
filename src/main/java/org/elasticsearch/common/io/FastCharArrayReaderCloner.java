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

package org.elasticsearch.common.io;

import javax.io.ReaderCloner;
import java.io.Reader;
import java.lang.reflect.Field;

/**
 * A specialized ReaderCloner that works on {@link FastCharArrayReader}.
 *
 * The only efficient mean of retrieving the original content
 * from a FastCharArrayReader is to use introspection and access the
 * {@code protected String buf} field.
 *
 * Apart from being efficient, this code is also very sensitive
 * to the actual ElasticSearch implementation.
 * If the introspection does not work, an {@link IllegalArgumentException}
 * is thrown.
 *
 * @author ofavre
 */
public class FastCharArrayReaderCloner implements ReaderCloner {

    private FastCharArrayReader original;
    private char[] originalContent;

    public FastCharArrayReaderCloner(FastCharArrayReader original) throws IllegalArgumentException {
        try {
            this.original = original;
            Field f = original.getClass().getDeclaredField("buf");
            f.setAccessible(true);
            this.originalContent = (char[]) f.get(original);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not access private \"str\" field of the given StringReader (actual class: "+original.getClass().getCanonicalName()+")", ex);
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
        return new FastCharArrayReader(originalContent);
    }

}

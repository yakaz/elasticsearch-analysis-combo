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

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;

/**
 * A ReaderCloner specialized for StringReader.
 *
 * The only efficient mean of retrieving the original content
 * from a StringReader is to use introspection and access the
 * {@code private String str} field.
 *
 * Apart from being efficient, this code is also very sensitive
 * to the used JVM implementation.
 * If the introspection does not work, an {@link IllegalArgumentException}
 * is thrown.
 *
 * @author ofavre
 */
public class StringReaderCloner implements ReaderCloner {

    private StringReader original;
    private String originalContent;

    public StringReaderCloner(StringReader original) throws IllegalArgumentException {
        try {
            this.original = original;
            Field f = original.getClass().getDeclaredField("str");
            f.setAccessible(true);
            this.originalContent = (String) f.get(original);
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
        return new StringReader(originalContent);
    }
    
}

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

/**
 * A specialized ReaderCloner that works with {@link FastStringReader} instances.
 *
 * The original String is merely gotten through a call to
 * {@link FastStringReader.toString()}.
 * 
 * @author ofavre
 */
public class FastStringReaderCloner implements ReaderCloner {

    private FastStringReader original;
    private String originalContent;

    public FastStringReaderCloner(FastStringReader original) {
        this.original = original;
        this.originalContent = original.toString();
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
        return new FastStringReader(originalContent);
    }
    
}

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

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;

import java.io.IOException;

/**
 * Keeps track of the current term position of the given TokenStream.
 * @author ofavre
 */
public class PositionedTokenStream extends TokenFilter implements Comparable<PositionedTokenStream> {

    // Attributes to track
    private final OffsetAttribute offsetAttr;
    private final PositionIncrementAttribute posAttr;
    /** Position tracker. */
    private int position;

    public PositionedTokenStream(TokenStream input) {
        super(input);

        // Force loading/adding these attributes
        // won't do much bad if they're not read/written
        offsetAttr = input.addAttribute(OffsetAttribute.class);
        posAttr = input.addAttribute(PositionIncrementAttribute.class);

        this.position = 0;
    }

    /**
     * Returns the tracked current token position.
     * @return The accumulated position increment attribute values.
     */
    public int getPosition() {
        return position;
    }

    /*
     * "TokenStream interface"
     */

    public boolean incrementToken() throws IOException {
        boolean rtn = input.incrementToken();
        if (!rtn) {
            position = Integer.MAX_VALUE;
        }
        // Track accumulated position
        position += posAttr.getPositionIncrement();
        return rtn;
    }

    public void end() throws IOException {
        input.end();
        position = 0;
    }

    public void reset() throws IOException {
        input.reset();
        position = 0;
    }

    public void close() throws IOException {
        input.close();
        position = 0;
    }

    /**
     * Permit ordering by reading order: term position, then term offsets (start, then end).
     */
    @Override public int compareTo(PositionedTokenStream that) {
        // Nullity checks
        if (that == null)
            return 1;
        // Position checks
        if (this.position != that.position)
            return this.position - that.position;
        // TokenStream nullity checks
        if (that.input == null) {
            if (this.input == null) return 0;
            else return 1;
        } else if (this.input == null) return -1;
        // Order by reading order, using offsets
        if (this.offsetAttr != null && that.offsetAttr != null) {
            int a = this.offsetAttr.startOffset();
            int b = that.offsetAttr.startOffset();
            if (a != b) {
                return a-b;
            }
            a = this.offsetAttr.endOffset();
            b = that.offsetAttr.endOffset();
            return a-b;
        } else if (that.offsetAttr == null) {
            if (this.offsetAttr == null) return 0;
            return 1;
        } else {
            return -1;
        }
    }

}

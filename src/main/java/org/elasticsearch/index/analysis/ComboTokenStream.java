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

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.Attribute;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeSource;

import java.io.IOException;
import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * A TokenStream combining the output of multiple sub-TokenStreams.
 *
 * This class copies the attributes from the last sub-TokenStream that
 * was read from. If attributes are not uniform between sub-TokenStreams,
 * extraneous attributes will stay untouched.
 *
 * @remark Copying is the only solution since most caller call
 *         get/addAttribute once and keep on reading the same
 *         updated instance returned the first (and only) time.
 *         Fortunately, {@link AttributeImpl}s have a method
 *         for giving their values to another instance.
 * 
 * @author ofavre
 */
public class ComboTokenStream extends TokenStream {

    private int lastPosition;
    // Position tracked sub-TokenStreams
    private final PositionedTokenStream[] positionedTokenStreams;
    // Reading queue, using the reading order from PositionedTokenStream
    private final AbstractQueue<PositionedTokenStream> readQueue;
    // Flag for lazy initialization and reset
    private boolean readQueueResetted;

    public ComboTokenStream(TokenStream[] tokenStreams) {
        // Load the TokenStreams, track their position, and register their attributes
        this.positionedTokenStreams = new PositionedTokenStream[tokenStreams.length];
        for (int i = tokenStreams.length-1 ; i >= 0 ; --i) {
            if (tokenStreams[i] == null) continue;
            this.positionedTokenStreams[i] = new PositionedTokenStream(tokenStreams[i]);
            // Add each and every token seen in the current sub AttributeSource
            Iterator<Class<? extends Attribute>> iterator = this.positionedTokenStreams[i].getAttributeClassesIterator();
            while (iterator.hasNext()) {
                super.addAttribute(iterator.next());
            }
        }
        this.lastPosition = 0;
        // Create an initially empty queue.
        // It will be filled at first incrementToken() call, because
        // it needs to call the same function on each sub-TokenStreams.
        this.readQueue = new PriorityQueue<PositionedTokenStream>(tokenStreams.length);
        readQueueResetted = false;
    }

    /*
     * TokenStream multiplexed methods
     */

    @Override public boolean incrementToken() throws IOException {
        // Fill the queue on first call
        if (!readQueueResetted) {
            readQueueResetted = true;
            readQueue.clear();
            for (PositionedTokenStream pts : positionedTokenStreams) {
                if (pts == null) continue;
                // Read first token
                if (pts.incrementToken()) {
                    // PositionedTokenStream.incrementToken() initialized internal
                    // variables to perform proper ordering.
                    // Therefore we can only add it to the queue now!
                    readQueue.add(pts);
                } // no token left (no token at all)
            }
        }

        // Read from the first token
        PositionedTokenStream toRead = readQueue.peek();
        if (toRead == null) {
            return false; // end of streams
        }
        // Look position to see if it will be increased, see usage a bit below
        int pos = toRead.getPosition();

        // Copy the current token attributes from the sub-TokenStream
        // to our AttributeSource (see class javadoc remark)
        AttributeSource currentAttributeSource = toRead;
        Iterator<Class<? extends Attribute>> iter = toRead.getAttributeClassesIterator();
        while (iter.hasNext()) {
            Class<? extends Attribute> clazz = iter.next();
            @SuppressWarnings("unchecked") AttributeImpl attr = (AttributeImpl) currentAttributeSource.getAttribute(clazz); // forefully an AttributeImpl, read Lucene source
            if (this.hasAttribute(clazz)) {
                @SuppressWarnings("unchecked") AttributeImpl attrLoc = (AttributeImpl) this.getAttribute(clazz);
                attr.copyTo(attrLoc);
            } // otherwise, leave untouched
        }
        // Override the PositionIncrementAttribute
        this.getAttribute(PositionIncrementAttribute.class).setPositionIncrement(Math.max(0,pos - lastPosition));
        lastPosition = pos;

        // Prepare next read
        // We did not remove the TokenStream from the queue yet,
        // because if we have another token available at the same position,
        // we can save a queue movement.
        if (!toRead.incrementToken()) {
            // No more token to read, remove from the queue
            readQueue.poll();
        } else {
            // Check if token position changed
            if (toRead.getPosition() != pos) {
                // If yes, re-enter in the priority queue
                readQueue.add(readQueue.poll());
            }   // Otherwise, next call will continue with the same TokenStream (less queue movements)
        }

        return true;
    }

    @Override public void end() throws IOException {
        super.end();
        lastPosition = 0;
        // Apply on each sub-TokenStream
        for (PositionedTokenStream pts : positionedTokenStreams) {
            if (pts == null) continue;
            pts.end();
        }
        readQueueResetted = false;
        readQueue.clear();
    }

    @Override public void reset() throws IOException {
        super.reset();
        super.clearAttributes();
        lastPosition = 0;
        // Apply on each sub-TokenStream
        for (PositionedTokenStream pts : positionedTokenStreams) {
            if (pts == null) continue;
            pts.reset();
        }
        readQueueResetted = false;
        readQueue.clear();
    }

    @Override public void close() throws IOException {
        super.close();
        lastPosition = 0;
        // Apply on each sub-TokenStream
        for (PositionedTokenStream pts : positionedTokenStreams) {
            if (pts == null) continue;
            pts.close();
        }
        readQueueResetted = false;
        readQueue.clear();
    }

    /*
     * AttributeSource delegated methods
     */

    @Override public AttributeFactory getAttributeFactory() {
        return super.getAttributeFactory();
    }

    @Override public Iterator<Class<? extends Attribute>> getAttributeClassesIterator() {
        return super.getAttributeClassesIterator();
    }

    @Override public Iterator<AttributeImpl> getAttributeImplsIterator() {
        return super.getAttributeImplsIterator();
    }

    @Override public void addAttributeImpl(AttributeImpl att) {
        super.addAttributeImpl(att);
    }

    @Override public <A extends Attribute> A addAttribute(Class<A> attClass) {
        // Apply on each sub-TokenStream
        for (PositionedTokenStream pts : positionedTokenStreams) {
            if (pts == null) continue;
            pts.addAttribute(attClass);
        }
        return super.addAttribute(attClass);
    }

    @Override public boolean hasAttributes() {
        return super.hasAttributes();
    }

    @Override public boolean hasAttribute(Class<? extends Attribute> attClass) {
        return super.hasAttribute(attClass);
    }

    @Override public <A extends Attribute> A getAttribute(Class<A> attClass) {
        return super.getAttribute(attClass);
    }

    @Override public void clearAttributes() {
        // Apply on each sub-TokenStream
        for (PositionedTokenStream pts : positionedTokenStreams) {
            if (pts == null) continue;
            pts.clearAttributes();
        }
        super.clearAttributes();
    }

    //
    // The following methods may be unreliable to use
    // with such a multiplexed implementation...
    //

    @Override public State captureState() {
        return super.captureState();
    }

    @Override public void restoreState(State state) {
        super.restoreState(state);
    }

    @Override public AttributeSource cloneAttributes() {
        return super.cloneAttributes();
    }

    
    @Override public int hashCode() {
        return super.hashCode();
    }

    @Override public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override public String toString() {
        return super.toString();
    }

}

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

import org.apache.lucene.index.ReusableStringReaderCloner;

import javax.io.StringReaderCloner;
import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Duplicates {@link Reader}s in order to feed multiple consumers.
 *
 * This class registers multiple implementations, and tries to resolve which one to use,
 * looking at the actual class of the Reader to clone, and matching with the most bond
 * handled classes for each {@link ReaderCloner} implementation.
 *
 * By default, a few {@link Reader} implementations are handled, including the
 * most used inside Lucene ({@link StringReader}), and a default, fallback implementation
 * that merely reads all the available content, and creates a String out of it.
 *
 * Therefore you should understand the importance of having a proper implementation for
 * any optimizable {@link Reader}. For instance, {@link javax.io.StringReaderCloner} gains access
 * to the underlying String in order to avoid copies. A generic BufferedReader
 */
public class ReaderCloneFactory {

    /**
     * Interface for a utility class, able to unwrap a {@link java.io.Reader}
     * inside another {@link Reader}.
     * @param <T> The base class handled.
     */
    public static interface ReaderUnwrapper<T extends Reader> {
        /**
         * Unwraps a {@link Reader} from another, simplifying an eventual chain.
         */
        public Reader unwrap(T originalReader) throws IllegalArgumentException;
    }

    /**
     * Interface for a utility class, able to clone the content of a {@link java.io.Reader},
     * possibly in an optimized way (such as gaining access to a package private field,
     * or through reflection using {@link java.lang.reflect.Field.setAccessible(boolean)}).
     * @param <T> The base class handled.
     */
    public static interface ReaderCloner<T extends Reader> {
        /**
         * Initialize or reinitialize the cloner with the given reader.
         * The implementing class should have a default no arguments constructor.
         * @remark The given Reader is now controlled by this ReaderCloner, it may
         *         be closed during a call to this method, or it may be returned
         *         at first call to {@link giveAClone()}.
         * @see giveAClone()
         */
        public void init(T originalReader) throws IOException;

        /**
         * Returns a new {@link Reader}.
         * @remark The returned Reader should be closed.
         *         The original Reader, if not consumed by the {@link init(T)} method,
         *         should be returned at first call. Therefore it is important to
         *         call this method at least once, or to be prepared to face possible
         *         exceptions when closing the original Reader.
         */
        public Reader giveAClone();
    }

    /** Map storing the mapping between a handled class and a handling class, for {@link ReaderCloner}s */
    private static final WeakHashMap<Class<? extends Reader>, WeakReference<Class<? extends ReaderCloner>>> typeMap =
            new WeakHashMap<Class<? extends Reader>, WeakReference<Class<? extends ReaderCloner>>>();
    /** Map storing the mapping between a handled class and a handling instance, for {@link ReaderUnwrapper}s */
    private static final WeakHashMap<Class<? extends Reader>, WeakReference<ReaderUnwrapper>> unwrapperTypeMap =
            new WeakHashMap<Class<? extends Reader>, WeakReference<ReaderUnwrapper>>();

    /**
     * Add the association between a (handled) class and its handling {@link ReaderCloner}.
     * @param handledClass The base class that is handled by clonerImplClass.
     *                     Using this parameter, you can further restrict the usage of a more generic cloner.
     * @param clonerImplClass The class of the associated cloner.
     * @param <T> The base handled class of the ReaderCloner.
     * @return The previously associated ReaderCloner for the handledClass.
     */
    public static <T extends Reader> WeakReference<Class<? extends ReaderCloner>> bindCloner(
            Class<? extends T> handledClass, Class<? extends ReaderCloner<T>> clonerImplClass) {
        return typeMap.put(handledClass, new WeakReference<Class<? extends ReaderCloner>>(clonerImplClass));
    }

    /**
     * Add the association between a (handled) class and its handling {@link ReaderUnwrapper} instance.
     * @param handledClass The base class that is handled by clonerImplClass.
     *                     Using this parameter, you can further restrict the usage of a more generic cloner.
     * @param unwrapperImpl The instance of the associated unwrapper.
     * @param <T> The base handled class of the ReaderUnwrapper.
     * @return The previously associated ReaderUnwrapper instance for the handledClass.
     */
    public static <T extends Reader> WeakReference<ReaderUnwrapper> bindUnwrapper(
            Class<? extends T> handledClass, ReaderUnwrapper<T> unwrapperImpl) {
        return unwrapperTypeMap.put(handledClass, new WeakReference<ReaderUnwrapper>(unwrapperImpl));
    }

    /**
     * Static initialization registering default associations
     */
    static {
        // General purpose Reader handling
        bindCloner(Reader.class, ReaderClonerDefaultImpl.class);
        bindUnwrapper(BufferedReader.class, new BufferedReaderUnwrapper());
        bindUnwrapper(FilterReader.class, new FilterReaderUnwrapper());
        // Often used Java Readers
        bindCloner(StringReader.class, StringReaderCloner.class); // very, very used inside Lucene
        bindCloner(CharArrayReader.class, CharArrayReaderCloner.class);
        // Lucene specific handling
        ReusableStringReaderCloner.registerCloner();
    }

    /**
     * (Expert) Returns the ReaderUnwrapper associated with the exact given class.
     * @param forClass The handled class bond to the ReaderUnwrapper to return.
     * @param <T> The base handled class of the ReaderUnwrapper to return.
     * @return The bond ReaderUnwrapper, or null.
     */
    public static <T extends Reader> ReaderUnwrapper<T> getUnwrapperStrict(Class<? extends T> forClass) {
        WeakReference<ReaderUnwrapper> refUnwrapper = unwrapperTypeMap.get(forClass);
        if (refUnwrapper != null)
            return refUnwrapper.get();
        return null;
    }

    /**
     * Returns the ReaderCloner associated with the exact given class.
     * @param forClass The handled class bond to the ReaderCloner to return.
     * @param <T> The base handled class of the ReaderCloner to return.
     * @return The bond ReaderCloner, or null.
     */
    public static <T extends Reader> ReaderCloner<T> getClonerStrict(Class<? extends T> forClass) {
        WeakReference<Class<? extends ReaderCloner>> refClonerClass = typeMap.get(forClass);
        if (refClonerClass != null) {
            Class<? extends ReaderCloner> clazz = refClonerClass.get();
            if (clazz != null) {
                try {
                    ReaderCloner<T> cloner = (ReaderCloner<T>) clazz.newInstance();
                    return cloner;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /**
     * (Advanced) Returns an initialized ReaderCloner, associated with the exact class of the given Reader.
     * If the initialization fails, this function returns null.
     * @param forReader The handled class bond to the ReaderCloner to return.
     * @param <T> The base handled class of the ReaderCloner to return.
     * @return The bond, initialized ReaderCloner, or null.
     */
    public static <T extends Reader> ReaderCloner<T> getClonerStrict(T forReader) {
        ReaderCloner<T> rtn = ReaderCloneFactory.<T>getClonerStrict((Class<? extends T>) forReader.getClass());
        if (rtn != null) {
            try {
                rtn.init(forReader);
            } catch (Throwable fail) {
                return null;
            }
        }
        return rtn;
    }

    /**
     * (Advanced) Returns an initialized ReaderCloner, associated with the given base class, for the given Reader.
     * If the initialization fails, this function returns null.
     *
     * The function first tries to match the exact class of forReader, and initialize the ReaderCloner.
     * If no ReaderCloner or (tested second) ReaderUnwrapper matches, the resolution continues with the super class,
     * until the baseClass is reached, and tested.
     *
     * If this process is not successful, <code>null</code> is returned.
     *
     * @param baseClass The baseClass, above which the resolution will not try to continue with the super class.
     * @param forClass  The class to start with, should be the class of forReader (but the latter can be null, hence this parameter)
     * @param forReader The Reader instance to return and initialize a ReaderCloner for. Can be null.
     * @param <T> The base handled class of the ReaderCloner to return
     * @param <S> The class of the given Reader to handle
     * @return An initialized ReaderCloner suitable for the givenReader, or null.
     */
    public static <T extends Reader, S extends T> ReaderCloner<T> getCloner(Class<T> baseClass, Class<S> forClass, S forReader) {
        // Loop through each super class
        while (forClass != null) {
            // Try first a matching cloner
            ReaderCloner<T> cloner = ReaderCloneFactory.<T>getClonerStrict(forClass);
            if (cloner != null) {
                if (forReader != null) {
                    try {
                        cloner.init(forReader);
                    } catch (Throwable fail) {
                        cloner = null;
                    }
                }
                if (cloner != null)
                    return cloner;
            }
            // Try then a matching unwrapper, for better suitability of the used cloner
            if (forReader != null) {
                ReaderUnwrapper<T> unwrapper = ReaderCloneFactory.<T>getUnwrapperStrict(forClass);
                if (unwrapper != null)
                    try {
                        // Recursive resolution
                        Reader unwrapped = unwrapper.unwrap(forReader);
                        if (unwrapped != null)
                            return (ReaderCloner<T>)ReaderCloneFactory.<Reader,Reader>getCloner(Reader.class, (Class<Reader>)unwrapped.getClass(), unwrapped);
                    } catch (Throwable ignore) {
                        // in case of errors, simply continue the began process and forget about this failed attempt
                    }
            }
            // Continue resolution with super class...
            Class clazz = forClass.getSuperclass();
            // ... checking ancestry with the given base class
            forClass = null;
            if (baseClass.isAssignableFrom(clazz))
                forClass = clazz;
        }
        return null;
    }

    /**
     * Returns a ReaderCloner suitable for handling general <code>S</code>s instances (inheriting <code>T</code>, itself
     * inheriting {@link java.io.Reader}).
     *
     * Resolution starts on <code>forClass</code> (<code>S</code>), and does not go further than <code>baseClass</code>.
     *
     * Not all optimizations can be ran, like unwrapping and failing initialization fallback.
     * However, for standard cases, when performance is really critical,
     * using this function can reduce a possible resolution overhead
     * because ReaderCloner are reusable.
     *
     * @param baseClass The baseClass, above which the resolution will not try to continue with the super class.
     * @param forClass  The class to start with, should be the class of forReader (but the latter can be null, hence this parameter)
     * @param <T> The base handled class of the ReaderCloner to return
     * @param <S> The class of the given Reader to handle
     * @return An uninitialized ReaderCloner suitable for any T Readers, or null.
     */
    public static <T extends Reader, S extends T> ReaderCloner<T> getCloner(Class<T> baseClass, Class<S> forClass) {
        return ReaderCloneFactory.<T,S>getCloner(baseClass, forClass, null);
    }

    /**
     * Returns a ReaderCloner suitable for handling general <code>S</code>s instances (inheriting {@link java.io.Reader}).
     *
     * Calls <code>ReaderCloneFactory.<Reader,S>getCloner(Reader.class, forClass, (S)null)</code>.
     *
     * Not all optimizations can be ran, like unwrapping and failing initialization fallback.
     * However, for standard cases, when performance is really critical,
     * using this function can reduce a possible resolution overhead
     * because ReaderCloner are reusable.
     *
     * @param forClass  The class to start with, should be the class of forReader (but the latter can be null, hence this parameter)
     * @param <S> The class of the given Reader to handle
     * @return An uninitialized ReaderCloner suitable for any <code>S</code>, or null.
     */
    public static <S extends Reader> ReaderCloner<Reader> getCloner(Class<S> forClass) {
        return ReaderCloneFactory.<Reader,S>getCloner(Reader.class, forClass, (S)null);
    }

    /**
     * Returns an initialized ReaderCloner, for the given Reader.
     *
     * Calls <code>ReaderCloneFactory.<Reader, S>getCloner(Reader.class, (Class<S>)forReader.getClass(), forReader)</code>.
     * If <code>forReader</code> is <code>null</code>, works as {@link ReaderCloneFactory.getGenericCloner()}.
     *
     * @param forReader The Reader instance to return and initialize a ReaderCloner for. Can be null.
     * @param <S> The class of the given Reader
     * @return An initialized ReaderCloner suitable for given Reader, or null.
     */
    public static <S extends Reader> ReaderCloner<Reader> getCloner(S forReader) {
        if (forReader != null)
            return ReaderCloneFactory.<Reader, S>getCloner(Reader.class, (Class<S>)forReader.getClass(), forReader);
        else
            return ReaderCloneFactory.getGenericCloner();
    }

    /**
     * Returns a {@link ReaderCloner} suitable for any {@link java.io.Reader} instance.
     */
    public static ReaderCloner<Reader> getGenericCloner() {
        return ReaderCloneFactory.<Reader, Reader>getCloner(Reader.class, Reader.class, (Reader)null);
    }

}

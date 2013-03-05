package org.apache.lucene.analysis;

import java.io.IOException;
import java.io.Reader;

public class ReusableTokenStreamComponents extends Analyzer.TokenStreamComponents {

    protected TokenStream sink;

    public ReusableTokenStreamComponents() {
        super(DummyTokenizer.INSTANCE);
    }

    public void setTokenStream(TokenStream sink) {
        this.sink = sink;
    }

    @Override
    protected void setReader(Reader reader) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public TokenStream getTokenStream() {
        return sink;
    }

    protected static final class DummyTokenizer extends Tokenizer {

        public static final DummyTokenizer INSTANCE = new DummyTokenizer();

        public DummyTokenizer() {
            super(DummyReader.INSTANCE);
        }

        @Override
        public boolean incrementToken() throws IOException {
            return false;
        }

    }

    protected static class DummyReader extends Reader {

        public static final DummyReader INSTANCE = new DummyReader();

        public DummyReader() {
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            return 0;
        }

        @Override
        public void close() throws IOException {
        }

    }

}

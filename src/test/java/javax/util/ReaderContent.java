package javax.util;

import java.io.IOException;
import java.io.Reader;
import java.util.Scanner;

import static junit.framework.Assert.assertEquals;

/**
 * Utility class to extract the String content from a {@link java.io.Reader}.
 * @author ofavre
 */
public class ReaderContent {

    /**
     * Extract the content from the given {@link Reader}.
     * @param reader The {@link Reader} to consume, whose content is to be read.
     * @return The content of the reader, as a String.
     */
    public static String readWhole(Reader reader) {
        Scanner scan = new Scanner(reader);
        scan.useDelimiter("\\z"); // DO *NOT* USE CAPITAL "\\Z"! This would remove ending '\n'!
        return scan.next();
    }

    public static void assertReaderContent(Reader reader, String content) throws IOException {
        int len = content.length();
        int index = 0;
        int read;
        while (index < len && (read = reader.read()) != -1)
            assertEquals(read, content.charAt(index++));
        assertEquals(index, len);
        reader.close();
    }

}

package org.elasticsearch.common.io;

import org.testng.annotations.Test;

import javax.util.ReaderContent;
import java.io.Reader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * @author ofavre
 */
@Test
public class FastCharArrayReaderClonerTests {
    @Test
    public void test() throws Exception {
        String content = "test\n";
        FastCharArrayReader reader = new FastCharArrayReader(content.toCharArray());
        FastCharArrayReaderCloner cloner = new FastCharArrayReaderCloner();
        cloner.init(reader);

        Reader clone1 = cloner.giveAClone();
        // The original Reader can be returned with this implementation.
        // Check it actually is.
        assertThat("returns original reader", clone1, is((Reader)reader));
        assertThat("same content", ReaderContent.readWhole(clone1), equalTo(content));
        assertThat("empty after reading", clone1.read(), equalTo(-1));

        Reader clone2 = cloner.giveAClone();
        assertThat("do not return the previous clone", clone2, not(is(clone1)));
        assertThat("same content", ReaderContent.readWhole(clone2), equalTo(content));
        assertThat("empty after reading", clone2.read(), equalTo(-1));
    }

}

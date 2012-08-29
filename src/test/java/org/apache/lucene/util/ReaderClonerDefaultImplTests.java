package org.apache.lucene.util;

import org.testng.annotations.Test;

import javax.util.ReaderContent;
import java.io.Reader;
import java.io.StringReader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * @author ofavre
 */
@Test
public class ReaderClonerDefaultImplTests {

    @Test
    public void test() throws Exception {
        String content = "test\n";
        Reader reader = new StringReader(content);
        ReaderClonerDefaultImpl cloner = new ReaderClonerDefaultImpl();
        cloner.init(reader);

        Reader clone1 = cloner.giveAClone();
        // The original Reader must be consumed in order to get the underlying content.
        // Hence it cannot be returned.
        assertThat("do not return original reader", clone1, not(is(reader)));
        assertThat("same content", ReaderContent.readWhole(clone1), equalTo(content));
        assertThat("empty after reading", clone1.read(), equalTo(-1));

        Reader clone2 = cloner.giveAClone();
        // The original Reader must be consumed in order to get the underlying content.
        // Hence it cannot be returned.
        assertThat("do not return original reader", clone2, not(is(reader)));
        assertThat("do not return the previous clone", clone2, not(is(clone1)));
        assertThat("same content", ReaderContent.readWhole(clone2), equalTo(content));
        assertThat("empty after reading", clone2.read(), equalTo(-1));
    }

}

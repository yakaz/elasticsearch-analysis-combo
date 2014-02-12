package org.elasticsearch.index.analysis;

import org.testng.annotations.Test;

@Test
public class TestIntegration extends BaseESTest {

    public static final String ANALYZER = "configured_analyzer";

    @Test
    public void testAnalysis() {
        assertAnalyzesTo(ANALYZER, "just a little test",
                new String[]{"just", "just", "just a little test", "a", "littl", "little", "test", "test"},
                new int[]{ 0,  0,  0,  5,  7,  7, 14, 14},
                new int[]{ 4,  4, 18,  6, 13, 13, 18, 18},
                null,
                new int[]{ 1,  0,  0,  1,  1,  0,  1,  0});
    }

}

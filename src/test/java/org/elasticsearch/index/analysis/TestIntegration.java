package org.elasticsearch.index.analysis;

import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequest;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.plugin.analysis.combo.AnalysisComboPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestIntegration extends ESIntegTestCase {

    protected static final String INDEX = "some_index";
    protected static final String TYPE = "some_type";

    public static final String ANALYZER = "configured_analyzer";

    protected Settings nodeSettings(int nodeOrdinal) {
        Settings.Builder settings = Settings.builder()
                .put(super.nodeSettings(nodeOrdinal))
                .put("plugin.types", AnalysisComboPlugin.class.getName());
        return settings.build();
    }

    protected void assertAnalyzesTo(String analyzer, String input, String[] output, int startOffsets[], int endOffsets[], String types[], int position[]) {
        assertThat(output, notNullValue());
        AnalyzeResponse response = client().admin().indices().analyze(new AnalyzeRequest(INDEX).text(input).analyzer(analyzer)).actionGet();
        if (VERBOSE) {
            try {
                Map<String,String> params = new HashMap<String,String>();
                params.put("format", "text");
                logger.info("Tokens for \""+input+"\": " + response.toXContent(jsonBuilder().startObject(), new ToXContent.MapParams(params)).endObject().string());
            } catch (IOException e) {
                logger.error("Tokens for \""+input+"\": ERROR", e);
            }
        }
        Iterator<AnalyzeResponse.AnalyzeToken> tokens = response.iterator();
        for (int i = 0; i < output.length; i++) {
            assertTrue("token "+i+" does not exist", tokens.hasNext());
            AnalyzeResponse.AnalyzeToken token = tokens.next();
            assertThat("term "+i, token.getTerm(), equalTo(output[i]));
            if (startOffsets != null)
                assertThat("startOffset "+i, token.getStartOffset(), equalTo(startOffsets[i]));
            if (endOffsets != null)
                assertThat("endOffset "+i, token.getEndOffset(), equalTo(endOffsets[i]));
            if (types != null)
                assertThat("type "+i, token.getType(), equalTo(types[i]));
            if (position != null) {
                assertThat("position "+i, token.getPosition(), equalTo(position[i]));
            }
        }
    }

    @Test
    public void testAnalysis() throws IOException {
        prepareCreate(INDEX)
                .setSettings(XContentFactory.jsonBuilder()
                                .startObject()
                                .startObject("index")
                                .startObject("analysis")
                                .startObject("analyzer")
                                .startObject("configured_analyzer")
                                .field("type", "combo")
                                .startArray("sub_analyzers")
                                .value("whitespace")
                                .value("english")
                                .value("keyword")
                                .endArray()
                                .endObject()
                                .endObject()
                                .endObject()
                                .endObject()
                                .endObject()
                )
                .execute()
                .actionGet();
        ensureGreen(INDEX);

        assertAnalyzesTo(ANALYZER, "just a little test",
                new String[]{"just", "just", "just a little test", "a", "littl", "little", "test", "test"},
                new int[]{ 0,  0,  0,  5,  7,  7, 14, 14},
                new int[]{ 4,  4, 18,  6, 13, 13, 18, 18},
                null,
                new int[]{ 0,  0,  0,  1,  2,  2,  3,  3});
    }

}

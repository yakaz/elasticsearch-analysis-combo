package org.elasticsearch.index.analysis;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequest;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.network.NetworkUtils;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.node.Node;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.elasticsearch.client.Requests.clusterHealthRequest;
import static org.elasticsearch.client.Requests.createIndexRequest;
import static org.elasticsearch.client.Requests.deleteIndexRequest;
import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.node.NodeBuilder.nodeBuilder;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BaseESTest {

    protected boolean VERBOSE = false;

    protected static final String INDEX = "some_index";
    protected static final String TYPE = "some_type";

    private final ESLogger logger = Loggers.getLogger(getClass());

    private Node node;

    @BeforeClass
    protected void setupServer() {
        node = nodeBuilder().local(true).settings(settingsBuilder()
                .put("cluster.name", "test-cluster-" + NetworkUtils.getLocalAddress())
                .put("gateway.type", "none")
                .put("index.numberOfReplicas", 0)
                .put("index.numberOfShards", 1)
        ).node();
    }

    @AfterClass
    protected void closeServer() {
        node.close();
    }

    protected String getSettings() {
        try {
            return Streams.copyToStringFromClasspath("/" + this.getClass().getSimpleName() + "-settings.yml");
        } catch (FileNotFoundException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Exception while getting class-specific settings for " + this.getClass().getSimpleName(), e);
        }
    }

    protected String getMapping() {
        try {
            return Streams.copyToStringFromClasspath("/" + this.getClass().getSimpleName() + "-mapping.yml");
        } catch (FileNotFoundException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Exception while getting class-specific mapping for " + this.getClass().getSimpleName(), e);
        }
    }

    @BeforeMethod
    protected void createIndex() {
        logger.info("creating index [" + INDEX + "]");
        CreateIndexRequest createIndexRequest = createIndexRequest(INDEX);
        String settings = getSettings();
        if (settings != null)
            createIndexRequest.settings(getSettings());
        String mapping = getMapping();
        if (mapping != null)
            createIndexRequest.mapping(TYPE, getMapping());
        assertThat("Index creation", node.client().admin().indices().create(createIndexRequest).actionGet().isAcknowledged());
        logger.info("Running Cluster Health");
        ClusterHealthResponse clusterHealth = node.client().admin().cluster().health(clusterHealthRequest().waitForGreenStatus()).actionGet();
        logger.info("Done Cluster Health, status " + clusterHealth.getStatus());
        assertThat(clusterHealth.isTimedOut(), equalTo(false));
        assertThat(clusterHealth.getStatus(), equalTo(ClusterHealthStatus.GREEN));
    }

    @AfterMethod
    protected void deleteIndex() {
        logger.info("deleting index [" + INDEX + "]");
        node.client().admin().indices().delete(deleteIndexRequest(INDEX)).actionGet();
    }

    protected void assertAnalyzesTo(String analyzer, String input, String[] output, int startOffsets[], int endOffsets[], String types[], int posIncrements[], int[] posLength, boolean offsetsAreCorrect) {
        // Additional arguments are not supported by this ES-style assertion
        assertAnalyzesTo(analyzer, input, output, startOffsets, endOffsets, types, posIncrements);
    }
    protected void assertAnalyzesTo(String analyzer, String input, String[] output, int startOffsets[], int endOffsets[], String types[], int posIncrements[]) {
        assertThat(output, notNullValue());
        AnalyzeResponse response = node.client().admin().indices().analyze(new AnalyzeRequest(INDEX, input).analyzer(analyzer)).actionGet();
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
        int pos = 0;
        for (int i = 0; i < output.length; i++) {
            assertThat("token "+i+" does not exist", tokens.hasNext());
            AnalyzeResponse.AnalyzeToken token = tokens.next();
            assertThat("term "+i, token.getTerm(), equalTo(output[i]));
            if (startOffsets != null)
                assertThat("startOffset "+i, token.getStartOffset(), equalTo(startOffsets[i]));
            if (endOffsets != null)
                assertThat("endOffset "+i, token.getEndOffset(), equalTo(endOffsets[i]));
            if (types != null)
                assertThat("type "+i, token.getType(), equalTo(types[i]));
            if (posIncrements != null) {
                pos += posIncrements[i];
                assertThat("position "+i, token.getPosition(), equalTo(pos));
            }
        }
    }

}

/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
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

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisService;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RemoveDuplicateTokenFilterTests extends Assert {

    private String words = "Please do not state the obvious. As it is already obvious!";

    @Test
    public void testBasicUsage() throws Exception {
        Index index = new Index("test");
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("index.analysis.analyzer.default.type", "combo")
                .put("index.analysis.analyzer.default.sub_analyzers.0", "standard")
                .put("index.analysis.analyzer.default.sub_analyzers.1", "english")
                .put("index.analysis.analyzer.default.sub_analyzers.2", "german")
                .put("index.analysis.analyzer.default.tokenstream_reuse", "true")
                .put("index.analysis.analyzer.default.tokenstream_caching", "true")
                .put("index.analysis.filter.default.type", "standard")
                .build();
        AnalysisService analysisService = createAnalysisService(index, settings);
        NamedAnalyzer analyzer = analysisService.analyzer("default");
        TokenStream ts1 = analyzer.reusableTokenStream("field1", new StringReader(words));
        assertEquals(dump(ts1).toString(), "please pleas pleas do do do not state stat state the obviou obvious obvious as it is already already alreadi obvious obviou obvious ");
        TokenStream ts2 = new RemoveDuplicatesTokenFilter(analyzer.reusableTokenStream("field1", new StringReader(words)));
        assertEquals(dump(ts2).toString(), "please pleas do not state stat the obviou obvious as it is already alreadi obvious obviou ");
    }

    private AnalysisService createAnalysisService(Index index, Settings settings) {
        Injector parentInjector = new ModulesBuilder().add(new SettingsModule(settings), new EnvironmentModule(new Environment(settings)), new IndicesAnalysisModule()).createInjector();
        Injector injector = new ModulesBuilder().add(
                new IndexSettingsModule(index, settings),
                new IndexNameModule(index),
                new AnalysisModule(settings, parentInjector.getInstance(IndicesAnalysisService.class))
                .addProcessor(new ComboAnalysisBinderProcessor()))
                .createChildInjector(parentInjector);

        return injector.getInstance(AnalysisService.class);
    }

    private String dump(TokenStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
        while (stream.incrementToken()) {
            sb.append(term.toString());
            if (sb.length() > 0) {
                sb.append(" ");
            }
            term = stream.getAttribute(CharTermAttribute.class);
        }
        return sb.toString();
    }
}

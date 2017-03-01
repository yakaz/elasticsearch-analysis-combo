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

import org.apache.lucene.analysis.ComboAnalyzerWrapper;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.settings.IndexSettingsService;

public class ComboAnalyzerProvider extends AbstractIndexAnalyzerProvider<ComboAnalyzerWrapper> {

    private final Injector injector;
    private final Settings settings;
    private final String name;


    @Inject 
	public ComboAnalyzerProvider(Index index, IndexSettingsService indexSettingsService, Environment env, @Assisted String name, @Assisted Settings settings, Injector injector) {
        super(index, indexSettingsService.getSettings(), name, settings);
        // Store parameters for delegated usage inside the ComboAnalyzerWrapper itself
        // Sub-analyzer resolution must use the AnalysisService,
        // but as we're a dependency of it (and it's not a Proxy-able interface)
        // we cannot in turn rely on it. Therefore the dependency has to be used lazily.
        this.injector = injector;
        this.settings = settings;
        this.name = name;
    }

    @Override public ComboAnalyzerWrapper get() {
        // This function is also called during the AnalysisService initialization,
        // hence the following constructor also needs to to perform lazy loading by itself.
        return new ComboAnalyzerWrapper(version, name, settings, injector);
    }
    
}

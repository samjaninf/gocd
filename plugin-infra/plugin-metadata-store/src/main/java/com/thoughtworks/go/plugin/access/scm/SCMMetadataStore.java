/*
 * Copyright Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.plugin.access.scm;

import com.thoughtworks.go.plugin.access.config.PluginPreferenceStore;
import com.thoughtworks.go.plugin.api.config.Option;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SCMMetadataStore extends PluginPreferenceStore<SCMPreference> {

    private static final SCMMetadataStore scmMetadataStore = new SCMMetadataStore();

    private SCMMetadataStore() {
    }

    public static SCMMetadataStore getInstance() {
        return scmMetadataStore;
    }

    public void addMetadataFor(String pluginId, SCMConfigurations configuration, SCMView scmView) {
        SCMPreference scmPreference = new SCMPreference(configuration, scmView);
        setPreferenceFor(pluginId, scmPreference);
    }

    public SCMConfigurations getConfigurationMetadata(String pluginId) {
        if (pluginId == null || pluginId.isEmpty() || !hasPreferenceFor(pluginId)) {
            return null;
        }
        return preferenceFor(pluginId).getScmConfigurations();
    }

    public String displayValue(String pluginId) {
        if (pluginId == null || pluginId.isEmpty() || !hasPreferenceFor(pluginId)) {
            return null;
        }
        SCMView scmView = preferenceFor(pluginId).getScmView();
        if (scmView == null) {
            return null;
        }
        return scmView.displayValue();
    }

    public void removeMetadata(String pluginId) {
        if (pluginId != null && !pluginId.isEmpty()) {
            removePreferenceFor(pluginId);
        }
    }

    @Override
    public boolean hasOption(String pluginId, String key, Option<Boolean> option) {
        SCMConfigurations configurations = getConfigurationMetadata(pluginId);
        if (configurations != null) {
            SCMConfiguration scmConfiguration = configurations.get(key);
            if (scmConfiguration != null) {
                return scmConfiguration.hasOption(option);
            }
        }
        return option.getValue();
    }

    public List<String> getPlugins() {
        return new ArrayList<>(pluginIds());
    }

    public boolean hasPlugin(String pluginId) {
        return hasPreferenceFor(pluginId);
    }

    @TestOnly
    public void clear() {
        Set<String> plugins = pluginIds();
        for (String pluginId : plugins) {
            removePreferenceFor(pluginId);
        }
    }
}

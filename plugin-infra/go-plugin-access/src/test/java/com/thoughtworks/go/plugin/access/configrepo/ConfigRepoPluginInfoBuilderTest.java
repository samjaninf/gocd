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
package com.thoughtworks.go.plugin.access.configrepo;

import com.thoughtworks.go.plugin.access.common.settings.PluginSettingsConfiguration;
import com.thoughtworks.go.plugin.access.common.settings.PluginSettingsProperty;
import com.thoughtworks.go.plugin.api.config.Property;
import com.thoughtworks.go.plugin.domain.common.Metadata;
import com.thoughtworks.go.plugin.domain.common.PluggableInstanceSettings;
import com.thoughtworks.go.plugin.domain.common.PluginConfiguration;
import com.thoughtworks.go.plugin.domain.common.PluginView;
import com.thoughtworks.go.plugin.domain.configrepo.Capabilities;
import com.thoughtworks.go.plugin.domain.configrepo.ConfigRepoPluginInfo;
import com.thoughtworks.go.plugin.infra.plugininfo.GoPluginDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

public class ConfigRepoPluginInfoBuilderTest {
    private ConfigRepoExtension extension;

    @BeforeEach
    public void setUp() {
        extension = mock(ConfigRepoExtension.class);

        PluginSettingsConfiguration value = new PluginSettingsConfiguration();
        value.add(new PluginSettingsProperty("username", null).with(Property.REQUIRED, true).with(Property.SECURE, false));
        value.add(new PluginSettingsProperty("password", null).with(Property.REQUIRED, true).with(Property.SECURE, true));
        when(extension.getPluginSettingsConfiguration("plugin1")).thenReturn(value);
    }

    @Test
    public void shouldBuildPluginInfo() {
        GoPluginDescriptor descriptor = GoPluginDescriptor.builder().id("plugin1").build();
        when(extension.getPluginSettingsView("plugin1")).thenReturn("some-html");
        when(extension.getCapabilities("plugin1")).thenReturn(new Capabilities(true, true, true, true));
        ConfigRepoPluginInfo pluginInfo = new ConfigRepoPluginInfoBuilder(extension).pluginInfoFor(descriptor);

        List<PluginConfiguration> pluginConfigurations = List.of(
                new PluginConfiguration("username", new Metadata(true, false)),
                new PluginConfiguration("password", new Metadata(true, true))
        );
        PluginView pluginView = new PluginView("some-html");

        assertThat(pluginInfo.getDescriptor()).isEqualTo(descriptor);
        assertThat(pluginInfo.getExtensionName()).isEqualTo("configrepo");
        assertThat(pluginInfo.getPluginSettings()).isEqualTo(new PluggableInstanceSettings(pluginConfigurations, pluginView));
        assertThat(pluginInfo.getCapabilities()).isEqualTo(new Capabilities(true, true, true, true));
    }

    @Test
    public void shouldContinueWithBuildingPluginInfoIfPluginSettingsIsNotProvidedByPlugin() {
        GoPluginDescriptor descriptor = GoPluginDescriptor.builder().id("plugin1").build();
        doThrow(new RuntimeException("foo")).when(extension).getPluginSettingsConfiguration("plugin1");

        ConfigRepoPluginInfo pluginInfo = new ConfigRepoPluginInfoBuilder(extension).pluginInfoFor(descriptor);

        assertThat(pluginInfo.getDescriptor()).isEqualTo(descriptor);
        assertThat(pluginInfo.getExtensionName()).isEqualTo("configrepo");
        assertNull(pluginInfo.getPluginSettings());
    }
}

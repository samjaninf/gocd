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
package com.thoughtworks.go.plugin.access.analytics;


import com.thoughtworks.go.plugin.access.common.settings.PluginSettingsConfiguration;
import com.thoughtworks.go.plugin.access.common.settings.PluginSettingsProperty;
import com.thoughtworks.go.plugin.api.config.Property;
import com.thoughtworks.go.plugin.domain.analytics.AnalyticsPluginInfo;
import com.thoughtworks.go.plugin.domain.analytics.Capabilities;
import com.thoughtworks.go.plugin.domain.common.*;
import com.thoughtworks.go.plugin.infra.plugininfo.GoPluginDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

public class AnalyticsPluginInfoBuilderTest {

    private AnalyticsExtension extension;

    @BeforeEach
    public void setUp() {
        extension = mock(AnalyticsExtension.class);
        when(extension.getCapabilities(any(String.class))).thenReturn(new Capabilities(Collections.emptyList()));
    }

    @Test
    public void shouldBuildPluginInfoWithCapabilities() {
        GoPluginDescriptor descriptor = GoPluginDescriptor.builder().id("plugin1").build();
        Capabilities capabilities = new Capabilities(Collections.emptyList());

        when(extension.getCapabilities(descriptor.id())).thenReturn(capabilities);

        AnalyticsPluginInfo pluginInfo = new AnalyticsPluginInfoBuilder(extension).pluginInfoFor(descriptor);

        assertThat(pluginInfo.getCapabilities()).isEqualTo(capabilities);
    }

    @Test
    public void shouldBuildPluginInfoWithImage() {
        GoPluginDescriptor descriptor = GoPluginDescriptor.builder().id("plugin1").build();
        Image icon = new Image("content_type", "data", "hash");

        when(extension.getIcon(descriptor.id())).thenReturn(icon);

        AnalyticsPluginInfo pluginInfo = new AnalyticsPluginInfoBuilder(extension).pluginInfoFor(descriptor);

        assertThat(pluginInfo.getImage()).isEqualTo(icon);
    }

    @Test
    public void shouldBuildPluginInfoWithPluginDescriptor() {
        GoPluginDescriptor descriptor = GoPluginDescriptor.builder().id("plugin1").build();

        AnalyticsPluginInfo pluginInfo = new AnalyticsPluginInfoBuilder(extension).pluginInfoFor(descriptor);

        assertThat(pluginInfo.getDescriptor()).isEqualTo(descriptor);
    }

    @Test
    public void shouldBuildPluginInfoWithPluginSettingsConfiguration() {
        GoPluginDescriptor descriptor = GoPluginDescriptor.builder().id("plugin1").build();
        PluginSettingsConfiguration value = new PluginSettingsConfiguration();
        value.add(new PluginSettingsProperty("username", null).with(Property.REQUIRED, true).with(Property.SECURE, false));
        value.add(new PluginSettingsProperty("password", null).with(Property.REQUIRED, true).with(Property.SECURE, true));

        when(extension.getPluginSettingsConfiguration("plugin1")).thenReturn(value);
        when(extension.getPluginSettingsView("plugin1")).thenReturn("some-html");

        AnalyticsPluginInfo pluginInfo = new AnalyticsPluginInfoBuilder(extension).pluginInfoFor(descriptor);

        List<PluginConfiguration> pluginConfigurations = List.of(
                new PluginConfiguration("username", new Metadata(true, false)),
                new PluginConfiguration("password", new Metadata(true, true))
        );
        PluginView pluginView = new PluginView("some-html");

        assertThat(pluginInfo.getDescriptor()).isEqualTo(descriptor);
        assertThat(pluginInfo.getExtensionName()).isEqualTo("analytics");
        assertThat(pluginInfo.getPluginSettings()).isEqualTo(new PluggableInstanceSettings(pluginConfigurations, pluginView));
    }

    @Test
    public void shouldContinueBuildingPluginInfoIfPluginSettingsIsNotProvidedByPlugin() {
        GoPluginDescriptor descriptor = GoPluginDescriptor.builder().id("plugin1").build();

        doThrow(new RuntimeException("foo")).when(extension).getPluginSettingsConfiguration("plugin1");
        AnalyticsPluginInfo pluginInfo = new AnalyticsPluginInfoBuilder(extension).pluginInfoFor(descriptor);

        assertThat(pluginInfo.getDescriptor()).isEqualTo(descriptor);
        assertThat(pluginInfo.getExtensionName()).isEqualTo("analytics");
        assertNull(pluginInfo.getPluginSettings());
    }
}

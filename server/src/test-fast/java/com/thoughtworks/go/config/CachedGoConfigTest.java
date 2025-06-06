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
package com.thoughtworks.go.config;

import com.thoughtworks.go.config.commands.EntityConfigUpdateCommand;
import com.thoughtworks.go.config.update.FullConfigUpdateCommand;
import com.thoughtworks.go.listener.ConfigChangedListener;
import com.thoughtworks.go.listener.EntityConfigChangedListener;
import com.thoughtworks.go.server.domain.Username;
import com.thoughtworks.go.server.service.MaintenanceModeService;
import com.thoughtworks.go.serverhealth.ServerHealthService;
import com.thoughtworks.go.serverhealth.ServerHealthState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CachedGoConfigTest {
    @Mock
    private CachedGoConfig cachedGoConfig;
    @Mock
    private GoFileConfigDataSource dataSource;
    private GoConfigHolder configHolder;
    @Mock
    private ServerHealthService serverHealthService;
    @Mock
    private GoConfigMigrator goConfigMigrator;
    @Mock
    private MaintenanceModeService maintenanceModeService;

    @BeforeEach
    public void setUp() throws Exception {
        configHolder = new GoConfigHolder(new BasicCruiseConfig(), new BasicCruiseConfig());
        cachedGoConfig = new CachedGoConfig(serverHealthService, dataSource, mock(CachedGoPartials.class), goConfigMigrator, maintenanceModeService);
        lenient().when(dataSource.load()).thenReturn(configHolder);
    }

    @Test
    public void shouldNotLoadConfigXMLDuringMaintenanceMode() {
        when(maintenanceModeService.isMaintenanceMode()).thenReturn(true);
        cachedGoConfig.onTimer();

        verifyNoInteractions(dataSource);
    }

    @Test
    public void shouldDelegateWriteEntityConfigCallToDataSource() {
        EntityConfigUpdateCommand<?> saveCommand = mock(EntityConfigUpdateCommand.class);
        GoConfigHolder savedConfig = new GoConfigHolder(new BasicCruiseConfig(), new BasicCruiseConfig());
        GoConfigHolder holderBeforeUpdate = cachedGoConfig.loadConfigHolder();
        Username user = new Username(new CaseInsensitiveString("user"));
        EntityConfigSaveResult<?> entityConfigSaveResult = mock(EntityConfigSaveResult.class);
        when(entityConfigSaveResult.getConfigHolder()).thenReturn(savedConfig);
        doReturn(new PipelineConfig()).when(entityConfigSaveResult).getEntityConfig();
        doReturn(entityConfigSaveResult).when(dataSource).writeEntityWithLock(saveCommand, holderBeforeUpdate, user);

        cachedGoConfig.writeEntityWithLock(saveCommand, user);
        assertThat(cachedGoConfig.loadConfigHolder()).isEqualTo(savedConfig);
        assertThat(cachedGoConfig.currentConfig()).isEqualTo(savedConfig.config);
        assertThat(cachedGoConfig.loadForEditing()).isEqualTo(savedConfig.configForEdit);
        verify(dataSource).writeEntityWithLock(saveCommand, holderBeforeUpdate, user);
    }

    @Test
    public void shouldLoadConfigHolderIfNotAvailable() {
        cachedGoConfig.forceReload();
        assertThat(cachedGoConfig.loadConfigHolder()).isEqualTo(configHolder);
    }

    @Test
    public void shouldNotifyConfigListenersWhenConfigChanges() {
        when(dataSource.writeWithLock(any(UpdateConfigCommand.class), any(GoConfigHolder.class))).thenReturn(new GoFileConfigDataSource.GoConfigSaveResult(configHolder, ConfigSaveState.UPDATED));
        final ConfigChangedListener listener = mock(ConfigChangedListener.class);
        cachedGoConfig.registerListener(listener);
        cachedGoConfig.forceReload();

        cachedGoConfig.writeWithLock(cruiseConfig -> cruiseConfig);

        verify(listener, times(2)).onConfigChange(any(BasicCruiseConfig.class));
    }

    @Test
    public void shouldNotNotifyWhenConfigIsNullDuringRegistration() {
        final ConfigChangedListener listener = mock(ConfigChangedListener.class);
        cachedGoConfig.registerListener(listener);
        verifyNoMoreInteractions(listener);
    }

    @Test
    public void shouldNotifyConcernedListenersWhenEntityChanges() {
        final boolean[] pipelineConfigChangeListenerCalled = {false};
        final boolean[] agentConfigChangeListenerCalled = {false};
        final boolean[] cruiseConfigChangeListenerCalled = {false};
        EntityConfigChangedListener<PipelineConfig> pipelineConfigChangeListener = new EntityConfigChangedListener<>() {
            @Override
            public void onEntityConfigChange(PipelineConfig entity) {
                pipelineConfigChangeListenerCalled[0] = true;
            }
        };
        EntityConfigChangedListener<Agent> agentConfigChangeListener = new EntityConfigChangedListener<>() {
            @Override
            public void onEntityConfigChange(Agent entity) {
                agentConfigChangeListenerCalled[0] = true;
            }
        };
        EntityConfigChangedListener<CruiseConfig> cruiseConfigChangeListener = new EntityConfigChangedListener<>() {
            @Override
            public void onEntityConfigChange(CruiseConfig entity) {
                cruiseConfigChangeListenerCalled[0] = true;
            }
        };
        cachedGoConfig.registerListener(pipelineConfigChangeListener);
        cachedGoConfig.registerListener(agentConfigChangeListener);
        cachedGoConfig.registerListener(cruiseConfigChangeListener);

        EntityConfigUpdateCommand<?> configCommand = mock(EntityConfigUpdateCommand.class);
        EntityConfigSaveResult<?> entityConfigSaveResult = mock(EntityConfigSaveResult.class);
        when(entityConfigSaveResult.getConfigHolder()).thenReturn(configHolder);
        doReturn(new PipelineConfig()).when(entityConfigSaveResult).getEntityConfig();
        Username user = new Username(new CaseInsensitiveString("user"));
        doReturn(entityConfigSaveResult).when(dataSource).writeEntityWithLock(configCommand, configHolder, user);

        cachedGoConfig.loadConfigIfNull();

        cachedGoConfig.writeEntityWithLock(configCommand, user);
        assertThat(pipelineConfigChangeListenerCalled[0]).isTrue();
        assertThat(agentConfigChangeListenerCalled[0]).isFalse();
        assertThat(cruiseConfigChangeListenerCalled[0]).isFalse();
    }

    @Test
    public void shouldWriteFullConfigWithLock() {
        FullConfigUpdateCommand fullConfigUpdateCommand = mock(FullConfigUpdateCommand.class);
        when(dataSource.writeFullConfigWithLock(any(FullConfigUpdateCommand.class), any(GoConfigHolder.class))).thenReturn(new GoFileConfigDataSource.GoConfigSaveResult(null, null));

        cachedGoConfig.forceReload();
        cachedGoConfig.writeFullConfigWithLock(fullConfigUpdateCommand);

        verify(dataSource).writeFullConfigWithLock(fullConfigUpdateCommand, cachedGoConfig.loadConfigHolder());
    }

    @Test
    public void shouldUpdateCachesPostWriteFullConfigWithLock() {
        BasicCruiseConfig config = mock(BasicCruiseConfig.class);
        BasicCruiseConfig configForEdit = mock(BasicCruiseConfig.class);
        BasicCruiseConfig mergedConfigForEdit = mock(BasicCruiseConfig.class);
        GoConfigHolder goConfigHolder = new GoConfigHolder(config, configForEdit);
        goConfigHolder.mergedConfigForEdit = mergedConfigForEdit;
        ConfigSaveState configSaveState = ConfigSaveState.UPDATED;

        when(dataSource.writeFullConfigWithLock(any(FullConfigUpdateCommand.class), any(GoConfigHolder.class)))
                .thenReturn(new GoFileConfigDataSource.GoConfigSaveResult(goConfigHolder, configSaveState));

        cachedGoConfig.forceReload();
        ConfigSaveState saveState = cachedGoConfig.writeFullConfigWithLock(mock(FullConfigUpdateCommand.class));

        assertThat(saveState).isEqualTo(configSaveState);
        assertThat(cachedGoConfig.currentConfig()).isEqualTo(config);
        assertThat(cachedGoConfig.loadForEditing()).isEqualTo(configForEdit);
        assertThat(cachedGoConfig.loadConfigHolder()).isEqualTo(goConfigHolder);
        assertThat(cachedGoConfig.loadMergedForEditing()).isEqualTo(mergedConfigForEdit);
        verify(serverHealthService, times(2)).update(any(ServerHealthState.class));
    }

    @Test
    public void shouldUpgradeConfigFile() {
        cachedGoConfig.upgradeConfig();

        verify(goConfigMigrator).migrate();
    }

    @Test
    public void shouldUpdateCachesPostConfigUpgrade() {
        BasicCruiseConfig config = mock(BasicCruiseConfig.class);
        BasicCruiseConfig configForEdit = mock(BasicCruiseConfig.class);
        BasicCruiseConfig mergedConfigForEdit = mock(BasicCruiseConfig.class);
        GoConfigHolder goConfigHolder = new GoConfigHolder(config, configForEdit);
        goConfigHolder.mergedConfigForEdit = mergedConfigForEdit;

        when(goConfigMigrator.migrate()).thenReturn(goConfigHolder);

        cachedGoConfig.upgradeConfig();

        assertThat(cachedGoConfig.currentConfig()).isEqualTo(config);
        assertThat(cachedGoConfig.loadForEditing()).isEqualTo(configForEdit);
        assertThat(cachedGoConfig.loadConfigHolder()).isEqualTo(goConfigHolder);
        assertThat(cachedGoConfig.loadMergedForEditing()).isEqualTo(mergedConfigForEdit);
        verify(serverHealthService).update(any(ServerHealthState.class));
    }
}

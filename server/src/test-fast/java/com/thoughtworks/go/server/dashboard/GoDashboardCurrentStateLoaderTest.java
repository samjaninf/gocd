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
package com.thoughtworks.go.server.dashboard;

import com.thoughtworks.go.config.*;
import com.thoughtworks.go.config.remote.FileConfigOrigin;
import com.thoughtworks.go.config.remote.RepoConfigOrigin;
import com.thoughtworks.go.config.security.GoConfigPipelinePermissionsAuthority;
import com.thoughtworks.go.config.security.Permissions;
import com.thoughtworks.go.config.security.permissions.EveryonePermission;
import com.thoughtworks.go.config.security.permissions.NoOnePermission;
import com.thoughtworks.go.config.security.users.Everyone;
import com.thoughtworks.go.config.security.users.NoOne;
import com.thoughtworks.go.domain.PipelinePauseInfo;
import com.thoughtworks.go.domain.buildcause.BuildCause;
import com.thoughtworks.go.helper.GoConfigMother;
import com.thoughtworks.go.helper.PipelineConfigMother;
import com.thoughtworks.go.presentation.pipelinehistory.*;
import com.thoughtworks.go.server.dao.PipelineSqlMapDao;
import com.thoughtworks.go.server.scheduling.TriggerMonitor;
import com.thoughtworks.go.server.service.PipelineLockService;
import com.thoughtworks.go.server.service.PipelinePauseService;
import com.thoughtworks.go.server.service.PipelineUnlockApiService;
import com.thoughtworks.go.server.service.SchedulingCheckerService;
import com.thoughtworks.go.util.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.*;

import static com.thoughtworks.go.config.CaseInsensitiveString.str;
import static com.thoughtworks.go.presentation.pipelinehistory.PipelineInstanceModels.createPipelineInstanceModels;
import static com.thoughtworks.go.presentation.pipelinehistory.PreparingToScheduleInstance.PreparingToScheduleBuildCause;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GoDashboardCurrentStateLoaderTest {
    @Mock
    private PipelineSqlMapDao pipelineSqlMapDao;
    @Mock
    private TriggerMonitor triggerMonitor;
    @Mock
    private PipelinePauseService pipelinePauseService;
    @Mock
    private PipelineLockService pipelineLockService;
    @Mock
    private PipelineUnlockApiService pipelineUnlockApiService;
    @Mock
    private SchedulingCheckerService schedulingCheckerService;
    @Mock
    private GoConfigPipelinePermissionsAuthority permissionsAuthority;

    private GoConfigMother goConfigMother;
    private CruiseConfig config;
    private static final String COUNTER = "121212";

    private GoDashboardCurrentStateLoader loader;

    @BeforeEach
    public void setUp() {
        loader = new GoDashboardCurrentStateLoader(pipelineSqlMapDao, triggerMonitor, pipelinePauseService,
                pipelineLockService, pipelineUnlockApiService, schedulingCheckerService, permissionsAuthority, new TimeStampBasedCounter(mock(Clock.class)));

        goConfigMother = new GoConfigMother();
        config = GoConfigMother.defaultCruiseConfig();
    }

    @Test
    public void shouldMatchExistingPipelinesInConfigWithAllLoadedActivePipelines() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");
        PipelineConfig p2Config = goConfigMother.addPipelineWithGroup(config, "group2", "pipeline2", "stage2", "job2");
        PipelineInstanceModel pimForP1 = pim(p1Config);
        PipelineInstanceModel pimForP2 = pim(p2Config);

        when(pipelineSqlMapDao.loadHistoryForDashboard(List.of("pipeline1", "pipeline2"))).thenReturn(createPipelineInstanceModels(pimForP1, pimForP2));

        List<GoDashboardPipeline> models = loader.allPipelines(config);

        assertThat(models.size()).isEqualTo(2);
        assertModel(models.get(1), "group1", pimForP1);  /* Pipeline is actually added in reverse order. */
        assertModel(models.get(0), "group2", pimForP2);
    }

    @Test
    public void shouldIgnoreActivePipelineModelsNotInConfig() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");
        PipelineConfig pipelineWhichIsNotInConfig = PipelineConfigMother.pipelineConfig("pipelineWhichIsNotInConfig");

        PipelineInstanceModel pimForP1 = pim(p1Config);
        when(pipelineSqlMapDao.loadHistoryForDashboard(CaseInsensitiveString.toStringList(config.getAllPipelineNames()))).thenReturn(createPipelineInstanceModels(pimForP1, pim(pipelineWhichIsNotInConfig)));

        List<GoDashboardPipeline> models = loader.allPipelines(config);

        assertThat(models.size()).isEqualTo(1);
        assertModel(models.get(0), "group1", pimForP1);
    }

    @Test
    public void shouldHaveASpecialModelForAPipelineWhichIsTriggeredButNotYetActive_DueToMaterialCheckTakingTime() {
        goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");

        when(pipelineSqlMapDao.loadHistoryForDashboard(CaseInsensitiveString.toStringList(config.getAllPipelineNames()))).thenReturn(createPipelineInstanceModels());
        when(triggerMonitor.isAlreadyTriggered(new CaseInsensitiveString("pipeline1"))).thenReturn(true);

        List<GoDashboardPipeline> models = loader.allPipelines(config);
        assertThat(models.size()).isEqualTo(1);
        assertThat(models.get(0).groupName()).isEqualTo("group1");
        assertThat(models.get(0).model().getName()).isEqualTo("pipeline1");

        PipelineModel model = models.get(0).model();
        assertThat(model.getActivePipelineInstances().size()).isEqualTo(1);

        PipelineInstanceModel specialPIM = model.getLatestPipelineInstance();
        assertThat(specialPIM.getName()).isEqualTo("pipeline1");
        assertThat(specialPIM.getCanRun()).isFalse();
        assertThat(specialPIM.isPreparingToSchedule()).isTrue();
        assertThat(specialPIM.getCounter()).isEqualTo(-1);
        assertThat(specialPIM.getBuildCause()).isEqualTo(new PreparingToScheduleBuildCause());
        assertStages(specialPIM, "stage1");
    }

    @Test
    public void shouldFallBackToAnEmptyPipelineInstanceModelIfItCannotBeLoadedEvenFromHistory() {
        goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");

        when(triggerMonitor.isAlreadyTriggered(new CaseInsensitiveString("pipeline1"))).thenReturn(false);
        when(pipelineSqlMapDao.loadHistoryForDashboard(List.of("pipeline1"))).thenReturn(createPipelineInstanceModels());

        List<GoDashboardPipeline> models = loader.allPipelines(config);
        assertThat(models.size()).isEqualTo(1);
        assertThat(models.get(0).groupName()).isEqualTo("group1");
        assertThat(models.get(0).model().getName()).isEqualTo("pipeline1");

        PipelineModel model = models.get(0).model();
        assertThat(model.getActivePipelineInstances().size()).isEqualTo(1);

        PipelineInstanceModel emptyPIM = model.getLatestPipelineInstance();
        assertThat(emptyPIM.getName()).isEqualTo("pipeline1");
        assertThat(emptyPIM.hasHistoricalData()).isFalse();
        assertThat(emptyPIM.isPreparingToSchedule()).isFalse();
        assertThat(emptyPIM.getCounter()).isEqualTo(0);
        assertThat(emptyPIM.getBuildCause()).isEqualTo(BuildCause.createWithEmptyModifications());
        assertStages(emptyPIM, "stage1");
    }

    @Test
    public void shouldAllowASinglePipelineInConfigToHaveMultipleInstances() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");

        PipelineInstanceModel firstInstance = pim(p1Config);
        PipelineInstanceModel secondInstance = pim(p1Config);
        when(pipelineSqlMapDao.loadHistoryForDashboard(CaseInsensitiveString.toStringList(config.getAllPipelineNames()))).thenReturn(createPipelineInstanceModels(firstInstance, secondInstance));

        List<GoDashboardPipeline> models = loader.allPipelines(config);

        PipelineModel model = models.get(0).model();
        assertThat(model.getActivePipelineInstances().size()).isEqualTo(2);
        assertThat(model.getActivePipelineInstances().get(0)).isEqualTo(firstInstance);
        assertThat(model.getActivePipelineInstances().get(1)).isEqualTo(secondInstance);
    }

    @Test
    public void shouldAddStagesWhichHaveNotYetRunIntoEachInstanceOfAPipeline_FromConfig() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stageP1_S1", "jobP1_S1_J1");
        goConfigMother.addStageToPipeline(config, "pipeline1", "stageP1_S2", "jobP1_S2_J1");

        PipelineConfig p2Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline2", "stageP2_S1", "jobP2_S1_J1");
        goConfigMother.addStageToPipeline(config, "pipeline2", "stageP2_S2", "jobP2_S2_J1");
        goConfigMother.addStageToPipeline(config, "pipeline2", "stageP2_S3", "jobP2_S3_J1");

        PipelineInstanceModel pimForP1 = pim(p1Config);
        pimForP1.getStageHistory().add(new StageInstanceModel("stageP1_S2", COUNTER, new JobHistory()));

        PipelineInstanceModel pimForP2 = pim(p2Config);

        when(pipelineSqlMapDao.loadHistoryForDashboard(List.of("pipeline1", "pipeline2"))).thenReturn(createPipelineInstanceModels(pimForP1, pimForP2));


        loader.allPipelines(config);

        assertStages(pimForP1, "stageP1_S1", "stageP1_S2");
        assertStages(pimForP2, "stageP2_S1", "stageP2_S2", "stageP2_S3");

        assertThat(pimForP1.getStageHistory().get(0).getCounter()).isEqualTo(COUNTER);
        assertThat(pimForP1.getStageHistory().get(1).getCounter()).isEqualTo(COUNTER);

        assertThat(pimForP2.getStageHistory().get(0).getCounter()).isEqualTo(COUNTER);
        assertThat(pimForP2.getStageHistory().get(1).getCounter()).isNotEqualTo(COUNTER);
        assertThat(pimForP2.getStageHistory().get(2).getCounter()).isNotEqualTo(COUNTER);
    }

    @Test
    public void shouldAddPipelinePauseInfoAtPipelineLevel() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");
        PipelineConfig p2Config = goConfigMother.addPipelineWithGroup(config, "group2", "pipeline2", "stage2", "job2");

        PipelinePauseInfo pipeline1PauseInfo = PipelinePauseInfo.notPaused();
        PipelinePauseInfo pipeline2PauseInfo = PipelinePauseInfo.paused("Reason 1", "user1");

        when(pipelineSqlMapDao.loadHistoryForDashboard(List.of("pipeline1", "pipeline2"))).thenReturn(createPipelineInstanceModels(pim(p1Config), pim(p2Config)));
        when(pipelinePauseService.pipelinePauseInfo("pipeline1")).thenReturn(pipeline1PauseInfo);
        when(pipelinePauseService.pipelinePauseInfo("pipeline2")).thenReturn(pipeline2PauseInfo);

        List<GoDashboardPipeline> models = loader.allPipelines(config);

        assertThat(models.size()).isEqualTo(2);
        assertThat(models.get(1).model().getPausedInfo()).isEqualTo(pipeline1PauseInfo);
        assertThat(models.get(0).model().getPausedInfo()).isEqualTo(pipeline2PauseInfo);
    }

    /* TODO: Even though the test is right, the correct place for lock info is pipeline level, not PIM level */
    @Test
    public void shouldAddPipelineLockInformationAtPipelineInstanceLevel() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");
        p1Config.lockExplicitly();

        PipelineConfig p2Config = goConfigMother.addPipelineWithGroup(config, "group2", "pipeline2", "stage2", "job2");

        when(pipelineSqlMapDao.loadHistoryForDashboard(List.of("pipeline1", "pipeline2"))).thenReturn(createPipelineInstanceModels(pim(p1Config), pim(p2Config)));
        when(pipelineLockService.isLocked("pipeline1")).thenReturn(true);
        when(pipelineUnlockApiService.isUnlockable("pipeline1")).thenReturn(true);

        when(pipelineLockService.isLocked("pipeline2")).thenReturn(false);
        when(pipelineUnlockApiService.isUnlockable("pipeline2")).thenReturn(false);

        List<GoDashboardPipeline> models = loader.allPipelines(config);

        PipelineModel modelForPipeline1 = models.get(1).model();
        assertThat(modelForPipeline1.getLatestPipelineInstance().isLockable()).isTrue();
        assertThat(modelForPipeline1.getLatestPipelineInstance().isCurrentlyLocked()).isTrue();
        assertThat(modelForPipeline1.getLatestPipelineInstance().canUnlock()).isTrue();

        PipelineModel modelForPipeline2 = models.get(0).model();
        assertThat(modelForPipeline2.getLatestPipelineInstance().isLockable()).isFalse();
        assertThat(modelForPipeline2.getLatestPipelineInstance().isCurrentlyLocked()).isFalse();
        assertThat(modelForPipeline2.getLatestPipelineInstance().canUnlock()).isFalse();
    }

    @Test
    public void shouldUpdateAdministrabilityOfAPipelineBasedOnItsOrigin() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");
        p1Config.setOrigin(new FileConfigOrigin());

        PipelineConfig p2Config = goConfigMother.addPipelineWithGroup(config, "group2", "pipeline2", "stage2", "job2");
        p2Config.setOrigin(new RepoConfigOrigin());

        when(pipelineSqlMapDao.loadHistoryForDashboard(List.of("pipeline1", "pipeline2"))).thenReturn(createPipelineInstanceModels(pim(p1Config), pim(p2Config)));

        List<GoDashboardPipeline> models = loader.allPipelines(config);

        assertThat(models.get(1).model().canAdminister()).isTrue();
        assertThat(models.get(0).model().canAdminister()).isFalse();
    }

    @Test
    public void shouldAddPipelineSchedulabilityInformationAtPipelineLevel() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");
        PipelineConfig p2Config = goConfigMother.addPipelineWithGroup(config, "group2", "pipeline2", "stage2", "job2");

        when(pipelineSqlMapDao.loadHistoryForDashboard(List.of("pipeline1", "pipeline2"))).thenReturn(createPipelineInstanceModels(pim(p1Config), pim(p2Config)));
        when(schedulingCheckerService.pipelineCanBeTriggeredManually(p1Config)).thenReturn(true);
        when(schedulingCheckerService.pipelineCanBeTriggeredManually(p2Config)).thenReturn(false);

        List<GoDashboardPipeline> models = loader.allPipelines(config);

        assertThat(models.get(1).model().canForce()).isTrue();
        assertThat(models.get(0).model().canForce()).isFalse();
    }

    @Test
    public void shouldAssociateEveryPipelineWithItsPermissions() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");
        PipelineConfig p2Config = goConfigMother.addPipelineWithGroup(config, "group2", "pipeline2", "stage2", "job2");
        PipelineInstanceModel pimForP1 = pim(p1Config);
        PipelineInstanceModel pimForP2 = pim(p2Config);

        Permissions permissionsForP1 = new Permissions(Everyone.INSTANCE, Everyone.INSTANCE, Everyone.INSTANCE, EveryonePermission.INSTANCE);
        Permissions permissionsForP2 = new Permissions(NoOne.INSTANCE, NoOne.INSTANCE, Everyone.INSTANCE, NoOnePermission.INSTANCE);

        when(pipelineSqlMapDao.loadHistoryForDashboard(List.of("pipeline1", "pipeline2"))).thenReturn(createPipelineInstanceModels(pimForP1, pimForP2));
        when(permissionsAuthority.pipelinesAndTheirPermissions()).thenReturn(Map.of(p1Config.name(), permissionsForP1, p2Config.name(), permissionsForP2));

        List<GoDashboardPipeline> models = loader.allPipelines(config);

        assertThat(models.get(1).permissions()).isEqualTo(permissionsForP1);
        assertThat(models.get(0).permissions()).isEqualTo(permissionsForP2);
    }

    @Test
    public void shouldDefaultToAllowingNoOneToViewAPipelineIfItsPermissionsAreNotFound() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");
        PipelineInstanceModel pimForP1 = pim(p1Config);

        when(pipelineSqlMapDao.loadHistoryForDashboard(CaseInsensitiveString.toStringList(config.getAllPipelineNames()))).thenReturn(createPipelineInstanceModels(pimForP1));
        when(permissionsAuthority.pipelinesAndTheirPermissions()).thenReturn(Collections.emptyMap());

        List<GoDashboardPipeline> models = loader.allPipelines(config);

        Permissions permissions = models.get(0).permissions();
        assertThat(permissions.viewers()).isEqualTo(NoOne.INSTANCE);
        assertThat(permissions.operators()).isEqualTo(NoOne.INSTANCE);
        assertThat(permissions.admins()).isEqualTo(NoOne.INSTANCE);
        assertThat(permissions.pipelineOperators()).isEqualTo(NoOne.INSTANCE);
    }

    @Test
    public void shouldGetAGoDashboardPipelineGivenASinglePipelineConfigAndItsGroupConfig() {
        String pipelineNameStr = "pipeline1";
        CaseInsensitiveString pipelineName = new CaseInsensitiveString(pipelineNameStr);
        Permissions permissions = new Permissions(Everyone.INSTANCE, NoOne.INSTANCE, Everyone.INSTANCE, NoOnePermission.INSTANCE);
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", pipelineNameStr, "stage1", "job1");
        PipelineInstanceModels pipelineInstanceModels = createPipelineInstanceModels(pim(p1Config));

        when(pipelineSqlMapDao.loadHistoryForDashboard(List.of(pipelineNameStr))).thenReturn(pipelineInstanceModels);
        when(permissionsAuthority.permissionsForPipeline(pipelineName)).thenReturn(permissions);

        GoDashboardPipeline pipeline = loader.pipelineFor(p1Config, config.findGroup("group1"));

        assertThat(pipeline.name()).isEqualTo(pipelineName);
        assertThat(pipeline.permissions()).isEqualTo(permissions);
        assertThat(pipeline.model().getActivePipelineInstances()).isEqualTo(pipelineInstanceModels);

        verify(pipelineSqlMapDao).loadHistoryForDashboard(List.of(pipelineNameStr));
        verifyNoMoreInteractions(pipelineSqlMapDao);
    }

    @Test
    public void hasEverLoadedCurrentStateIsTrueAfterLoading() {
        assertThat(loader.hasEverLoadedCurrentState()).isFalse();
        loader.allPipelines(new BasicCruiseConfig());
        assertThat(loader.hasEverLoadedCurrentState()).isTrue();
    }

    @Test
    public void shouldAddTrackingToolInfoWhenLoadingAllPipelines() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");
        TrackingTool trackingTool = new TrackingTool("http://example.com/${ID}", "\\d+");
        p1Config.setTrackingTool(trackingTool);
        PipelineInstanceModel pimForP1 = pim(p1Config);
        when(pipelineSqlMapDao.loadHistoryForDashboard(CaseInsensitiveString.toStringList(config.getAllPipelineNames()))).thenReturn(createPipelineInstanceModels(pimForP1));

        List<GoDashboardPipeline> models = loader.allPipelines(config);

        assertThat(models.get(0).getTrackingTool()).isEqualTo(Optional.of(trackingTool));
    }

    @Test
    public void shouldAddTrackingToolInfoWhenLoadingAPipeline() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");
        TrackingTool trackingTool = new TrackingTool("http://example.com/${ID}", "\\d+");
        p1Config.setTrackingTool(trackingTool);
        PipelineInstanceModel pimForP1 = pim(p1Config);
        when(pipelineSqlMapDao.loadHistoryForDashboard(List.of(p1Config.getName().toString()))).thenReturn(createPipelineInstanceModels(pimForP1));

        GoDashboardPipeline model = loader.pipelineFor(p1Config, config.findGroup("group1"));

        assertThat(model.getTrackingTool()).isEqualTo(Optional.of(trackingTool));
    }

    @Test
    public void shouldNotReloadFromDBIfListOfPipelinesHasNotChanged() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");
        PipelineInstanceModel pimForP1 = pim(p1Config);

        when(pipelineSqlMapDao.loadHistoryForDashboard(CaseInsensitiveString.toStringList(p1Config.getName()))).thenReturn(createPipelineInstanceModels(pimForP1));

        loader.allPipelines(config);
        goConfigMother.addStageToPipeline(config, p1Config.getName().toString(), "someStage", "someJob");
        loader.allPipelines(config);

        verify(pipelineSqlMapDao, times(1)).loadHistoryForDashboard(CaseInsensitiveString.toStringList(p1Config.getName()));
    }

    @Test
    public void shouldLoadFromDBPipelinesThatHaveBeenAdded() {
        PipelineConfig p1Config = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");

        when(pipelineSqlMapDao.loadHistoryForDashboard(any())).thenAnswer((Answer<PipelineInstanceModels>) invocation -> {
            List<String> pipelineNames = invocation.getArgument(0);
            List<PipelineInstanceModel> models = new ArrayList<>();
            for (String pipelineName : pipelineNames) {
                PipelineInstanceModel pim = pim(config.getPipelineConfigByName(new CaseInsensitiveString(pipelineName)));
                models.add(pim);
            }
            return createPipelineInstanceModels(models);
        });

        loader.allPipelines(config.cloneForValidation());

        PipelineConfig p2Config = goConfigMother.addPipelineWithGroup(config, "group2", "pipeline2", "stage1", "job1");
        config.findGroup("group1").remove(p1Config);

        loader.allPipelines(config.cloneForValidation());
        verify(pipelineSqlMapDao, times(1)).loadHistoryForDashboard(CaseInsensitiveString.toStringList(p1Config.getName()));
        verify(pipelineSqlMapDao, times(1)).loadHistoryForDashboard(CaseInsensitiveString.toStringList(p2Config.getName()));
        verifyNoMoreInteractions(pipelineSqlMapDao);
    }

    @Test
    public void shouldHandlePipelineDeletion() {
        PipelineConfig pipeline1 = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");
        PipelineConfig pipeline2 = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline2", "stage1", "job1");
        PipelineConfig pipeline3 = goConfigMother.addPipelineWithGroup(config, "group1", "pipeline3", "stage1", "job1");
        when(pipelineSqlMapDao.loadHistoryForDashboard(any())).thenReturn(PipelineInstanceModels.createPipelineInstanceModels());
        List<GoDashboardPipeline> goDashboardPipelines = loader.allPipelines(config);
        assertThat(goDashboardPipelines).hasSize(3);

        for (CaseInsensitiveString pipelineName : List.of(pipeline1.name(), pipeline2.name(), pipeline3.name())) {
            long matches = goDashboardPipelines.stream().filter(goDashboardPipeline -> pipelineName.equals(goDashboardPipeline.name())).count();
            assertThat(matches).isEqualTo(1L);

        }

        config.deletePipeline(pipeline1);
        config.deletePipeline(pipeline2);
        config.getAllPipelineConfigs().remove(pipeline1);
        config.getAllPipelineConfigs().remove(pipeline2);
        goDashboardPipelines = loader.allPipelines(config);
        assertThat(goDashboardPipelines).hasSize(1);
        assertThat(goDashboardPipelines.get(0).name()).isEqualTo(pipeline3.name());
    }

    private void assertModel(GoDashboardPipeline pipeline, String group, PipelineInstanceModel... pims) {
        assertThat(pipeline.groupName()).isEqualTo(group);
        assertThat(pipeline.model().getName()).isEqualTo(pims[0].getName());
        assertThat(pipeline.model().getActivePipelineInstances()).isEqualTo(List.of(pims));
    }

    private void assertStages(PipelineInstanceModel pim, String... stages) {
        assertThat(pim.getStageHistory().size()).isEqualTo(stages.length);

        for (int i = 0; i < pim.getStageHistory().size(); i++) {
            StageInstanceModel stageInstanceModel = pim.getStageHistory().get(i);
            assertThat(stageInstanceModel.getName()).isEqualTo(stages[i]);
        }
    }

    private PipelineInstanceModel pim(PipelineConfig pipelineConfig) {
        StageInstanceModels stageHistory = new StageInstanceModels();
        stageHistory.add(new StageInstanceModel(str(pipelineConfig.getFirstStageConfig().name()), COUNTER, new JobHistory()));
        return PipelineInstanceModel.createPipeline(str(pipelineConfig.name()), 123, "LABEL", BuildCause.createManualForced(), stageHistory);
    }
}

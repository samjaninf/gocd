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
package com.thoughtworks.go.server.service;

import com.thoughtworks.go.config.CaseInsensitiveString;
import com.thoughtworks.go.config.GoConfigDao;
import com.thoughtworks.go.config.PartialConfigService;
import com.thoughtworks.go.config.PipelineConfig;
import com.thoughtworks.go.config.materials.Filter;
import com.thoughtworks.go.config.materials.IgnoredFiles;
import com.thoughtworks.go.config.materials.MaterialConfigs;
import com.thoughtworks.go.config.materials.SubprocessExecutionContext;
import com.thoughtworks.go.config.materials.dependency.DependencyMaterialConfig;
import com.thoughtworks.go.config.materials.git.GitMaterial;
import com.thoughtworks.go.config.materials.mercurial.HgMaterial;
import com.thoughtworks.go.config.materials.svn.SvnMaterial;
import com.thoughtworks.go.config.remote.ConfigRepoConfig;
import com.thoughtworks.go.config.remote.PartialConfig;
import com.thoughtworks.go.config.remote.RepoConfigOrigin;
import com.thoughtworks.go.config.rules.Allow;
import com.thoughtworks.go.domain.MaterialRevision;
import com.thoughtworks.go.domain.MaterialRevisions;
import com.thoughtworks.go.domain.Pipeline;
import com.thoughtworks.go.domain.buildcause.BuildCause;
import com.thoughtworks.go.domain.materials.Material;
import com.thoughtworks.go.domain.materials.svn.Subversion;
import com.thoughtworks.go.domain.materials.svn.SvnCommand;
import com.thoughtworks.go.helper.PartialConfigMother;
import com.thoughtworks.go.helper.PipelineMother;
import com.thoughtworks.go.helper.SvnTestRepo;
import com.thoughtworks.go.server.dao.DatabaseAccessHelper;
import com.thoughtworks.go.server.dao.PipelineSqlMapDao;
import com.thoughtworks.go.server.domain.PipelineTimeline;
import com.thoughtworks.go.server.domain.Username;
import com.thoughtworks.go.server.materials.MaterialDatabaseUpdater;
import com.thoughtworks.go.server.materials.MaterialUpdateStatusListener;
import com.thoughtworks.go.server.materials.MaterialUpdateStatusNotifier;
import com.thoughtworks.go.server.persistence.MaterialRepository;
import com.thoughtworks.go.server.scheduling.BuildCauseProducerService;
import com.thoughtworks.go.server.scheduling.ScheduleHelper;
import com.thoughtworks.go.server.scheduling.ScheduleOptions;
import com.thoughtworks.go.server.scheduling.TriggerMonitor;
import com.thoughtworks.go.server.service.result.HttpOperationResult;
import com.thoughtworks.go.server.service.result.OperationResult;
import com.thoughtworks.go.server.service.result.ServerHealthStateOperationResult;
import com.thoughtworks.go.server.transaction.TransactionTemplate;
import com.thoughtworks.go.serverhealth.HealthStateLevel;
import com.thoughtworks.go.serverhealth.HealthStateType;
import com.thoughtworks.go.serverhealth.ServerHealthMatcher;
import com.thoughtworks.go.serverhealth.ServerHealthService;
import com.thoughtworks.go.util.GoConfigFileHelper;
import com.thoughtworks.go.util.ReflectionUtil;
import com.thoughtworks.go.util.SystemEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import static com.thoughtworks.go.helper.MaterialConfigsMother.git;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {
        "classpath:/applicationContext-global.xml",
        "classpath:/applicationContext-dataLocalAccess.xml",
        "classpath:/testPropertyConfigurer.xml",
        "classpath:/spring-all-servlet.xml",
})
public class BuildCauseProducerServiceIntegrationTest {

    private static final GoConfigFileHelper configHelper = new GoConfigFileHelper();
    private static final String MINGLE_PIPELINE_NAME = "mingle";
    private static final String GO_PIPELINE_NAME = "go";
    private static final String GO_PIPELINE_UPSTREAM = "go-parent";
    private static final String STAGE_NAME = "dev";

    @Autowired
    private GoConfigDao goConfigDao;
    @Autowired
    private GoConfigService goConfigService;
    @Autowired
    private PipelineSqlMapDao pipelineDao;
    @Autowired
    private PipelineScheduler buildCauseProducer;
    @Autowired
    private PipelineScheduleQueue pipelineScheduleQueue;
    @Autowired
    private ServerHealthService serverHealthService;
    @Autowired
    private ScheduleHelper scheduleHelper;
    @Autowired
    private DatabaseAccessHelper dbHelper;
    @Autowired
    private MaterialDatabaseUpdater materialDatabaseUpdater;
    @Autowired
    private MaterialRepository materialRepository;
    @Autowired
    private SubprocessExecutionContext subprocessExecutionContext;
    @Autowired
    private PipelinePauseService pipelinePauseService;
    @Autowired
    private PipelineTimeline pipelineTimeline;
    @Autowired
    private BuildCauseProducerService service;
    @Autowired
    private MaterialUpdateStatusNotifier materialUpdateStatusNotifier;
    @Autowired
    private TriggerMonitor triggerMonitor;
    @Autowired
    private PartialConfigService partialConfigService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public Subversion repository;
    private Pipeline latestPipeline;

    private SvnTestRepo svnRepository;

    public DiskSpaceSimulator diskSpaceSimulator;
    private PipelineConfig goPipelineConfig;
    private MaterialRevisions svnMaterialRevs;
    private PipelineConfig mingleConfig;
    private HttpOperationResult result;
    private ScheduleOptions scheduleOptions;
    private ScheduleTestUtil u;
    private PipelineConfig manualTriggerPipeline;
    private SvnMaterial materialForManualTriggerPipeline;
    private Username username;

    @BeforeEach
    public void setup(@TempDir Path tempDir) throws Exception {
        diskSpaceSimulator = new DiskSpaceSimulator();

        svnRepository = new SvnTestRepo(tempDir);

        dbHelper.onSetUp();
        configHelper.usingCruiseConfigDao(goConfigDao);
        configHelper.onSetUp();

        repository = new SvnCommand(null, svnRepository.projectRepositoryUrl());

        configHelper.addPipeline(GO_PIPELINE_UPSTREAM, STAGE_NAME, new MaterialConfigs(git("foo-bar")), "unit");

        goPipelineConfig = configHelper.addPipeline(GO_PIPELINE_NAME, STAGE_NAME, repository, "unit");

        svnMaterialRevs = new MaterialRevisions();
        SvnMaterial svnMaterial = new SvnMaterial(repository);
        svnMaterialRevs.addRevision(svnMaterial, svnMaterial.latestModification(null, new ServerSubprocessExecutionContext(goConfigService, new SystemEnvironment())));

        final MaterialRevisions materialRevisions = new MaterialRevisions();
        SvnMaterial anotherSvnMaterial = new SvnMaterial(repository);
        materialRevisions.addRevision(anotherSvnMaterial, anotherSvnMaterial.latestModification(null, subprocessExecutionContext));

        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                materialRepository.save(svnMaterialRevs);
            }
        });

        BuildCause buildCause = BuildCause.createWithModifications(svnMaterialRevs, "");

        mingleConfig = configHelper.addPipeline(MINGLE_PIPELINE_NAME, STAGE_NAME, repository, new Filter(new IgnoredFiles("**/*.doc")), "unit", "functional");
        latestPipeline = PipelineMother.schedule(this.mingleConfig, buildCause);
        latestPipeline = pipelineDao.saveWithStages(latestPipeline);
        dbHelper.passStage(latestPipeline.getStages().first());
        pipelineScheduleQueue.clear();
        result = new HttpOperationResult();
        scheduleOptions = new ScheduleOptions();
        u = new ScheduleTestUtil(transactionTemplate, materialRepository, dbHelper, configHelper);
        materialForManualTriggerPipeline = u.wf(new SvnMaterial("svn", "username", "password", false), "folder1");
        u.checkinInOrder(materialForManualTriggerPipeline, u.d(1), "s1");
        manualTriggerPipeline = configHelper.addPipeline(UUID.randomUUID().toString(), STAGE_NAME, materialForManualTriggerPipeline.config(), "build");
        username = Username.ANONYMOUS;
    }

    @AfterEach
    public void teardown() throws Exception {
        diskSpaceSimulator.onTearDown();
        dbHelper.onTearDown();
        pipelineScheduleQueue.clear();
        configHelper.onTearDown();
    }

    @Test
    public void manualSchedulePipeline_canProduceShouldNotGetIntoCyclicLoopWithTriggerMonitor() {
        OperationResult operationResult = new ServerHealthStateOperationResult();
        buildCauseProducer.manualProduceBuildCauseAndSave(MINGLE_PIPELINE_NAME, Username.ANONYMOUS, new ScheduleOptions(), operationResult);
        scheduleHelper.waitForAnyScheduled(5);
        assertThat(operationResult.canContinue()).isTrue();
    }


    @Test
    public void shouldNotSchedulePipelineIfTheChangesAreIgnored() throws Exception {
        String ignoredFile = "a.doc";
        svnRepository.checkInOneFile(ignoredFile);
        scheduleHelper.autoSchedulePipelinesWithRealMaterials(MINGLE_PIPELINE_NAME);
        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).doesNotContain(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
    }

    @Test
    public void shouldSchedulePipeline() throws Exception {
        checkinFile(new SvnMaterial(repository), "a.java", svnRepository);
        scheduleHelper.autoSchedulePipelinesWithRealMaterials(MINGLE_PIPELINE_NAME);
        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).contains(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
    }

    @Test
    public void should_NOT_schedulePipeline_whenOneOfTheMaterialsHasNoModificationsPresent() throws Exception {
        Pipeline latestGoInstance = PipelineMother.schedule(goPipelineConfig, BuildCause.createManualForced(svnMaterialRevs, new Username(new CaseInsensitiveString("loser"))));
        latestGoInstance = pipelineDao.saveWithStages(latestGoInstance);
        dbHelper.passStage(latestGoInstance.getStages().first());
        configHelper.addMaterialToPipeline(GO_PIPELINE_NAME, new DependencyMaterialConfig(new CaseInsensitiveString(GO_PIPELINE_UPSTREAM), new CaseInsensitiveString(STAGE_NAME)));
        svnRepository.checkInOneFile("a.java");
        scheduleHelper.autoSchedulePipelinesWithRealMaterials(GO_PIPELINE_NAME);
        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).doesNotContain(new CaseInsensitiveString(GO_PIPELINE_NAME));
    }


    @Test
    public void shouldNotSchedulePipelineWithManualFirstStageForAutomaticBuild() throws Exception {
        configHelper.configureStageAsManualApproval(MINGLE_PIPELINE_NAME, STAGE_NAME);
        svnRepository.checkInOneFile("a.java");
        scheduleHelper.autoSchedulePipelinesWithRealMaterials(MINGLE_PIPELINE_NAME);
        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).doesNotContain(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
    }

    @Test
    public void shouldSchedulePipelineWithManualFirstStageWhenManuallyTriggered() throws Exception {
        configHelper.configureStageAsManualApproval(MINGLE_PIPELINE_NAME, STAGE_NAME);

        svnRepository.checkInOneFile("a.java");
        materialDatabaseUpdater.updateMaterial(svnRepository.material());

        final Map<String, String> revisions = new HashMap<>();
        final Map<String, String> environmentVariables = new HashMap<>();
        buildCauseProducer.manualProduceBuildCauseAndSave(MINGLE_PIPELINE_NAME, Username.ANONYMOUS, new ScheduleOptions(revisions, environmentVariables, new HashMap<>()), new ServerHealthStateOperationResult());

        Map<CaseInsensitiveString, BuildCause> afterLoad = scheduleHelper.waitForAnyScheduled(5);
        assertThat(afterLoad.keySet()).contains(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        BuildCause cause = afterLoad.get(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        assertThat(cause.getBuildCauseMessage()).contains("Forced by anonymous");
    }

    @Test
    public void shouldScheduleANewPipelineWhenManuallyTriggeredWithNoChanges() {
        final Map<String, String> revisions = new HashMap<>();
        final Map<String, String> environmentVariables = new HashMap<>();
        buildCauseProducer.manualProduceBuildCauseAndSave(MINGLE_PIPELINE_NAME, Username.ANONYMOUS, new ScheduleOptions(revisions, environmentVariables, new HashMap<>()),
                new ServerHealthStateOperationResult());
        assertThat(scheduleHelper.waitForAnyScheduled(5).keySet()).contains(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
    }

    @Test
    public void shouldStopAutoSchedulingIfDiskSpaceIsLessThanMinimum() throws Exception {
        diskSpaceSimulator.simulateDiskFull();

        scheduleHelper.autoSchedulePipelinesWithRealMaterials();

        assertThat(serverHealthService).satisfies(ServerHealthMatcher.containsState(HealthStateType.artifactsDiskFull(), HealthStateLevel.ERROR, "GoCD Server has run out of artifacts disk space. Scheduling has been stopped"));
        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).doesNotContain(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
    }

    @Test
    public void shouldStopManualSchedulingIfDiskSpaceIsLessThanMinimum() {
        diskSpaceSimulator.simulateDiskFull();

        final Map<String, String> revisions = new HashMap<>();
        final Map<String, String> environmentVariables = new HashMap<>();
        buildCauseProducer.manualProduceBuildCauseAndSave(MINGLE_PIPELINE_NAME, Username.ANONYMOUS, new ScheduleOptions(revisions, environmentVariables, new HashMap<>()),
                new ServerHealthStateOperationResult());

        assertThat(serverHealthService).satisfies(ServerHealthMatcher.containsState(HealthStateType.artifactsDiskFull(), HealthStateLevel.ERROR, "GoCD Server has run out of artifacts disk space. Scheduling has been stopped"));
        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).doesNotContain(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
    }

    @Test
    public void shouldUnderstandChangedMaterial_forCompatibleRevisionsBeingSelectedForChangedMaterials_whenTriggeringTheFirstTime() throws Exception {
        DependencyMaterialConfig mingleMaterialConfig = new DependencyMaterialConfig(new CaseInsensitiveString(MINGLE_PIPELINE_NAME), new CaseInsensitiveString(STAGE_NAME));
        String mingleDownstreamPipelineName = "down_of_mingle";
        SvnMaterial svn = new SvnMaterial(repository);

        runAndPassWith(svn, "foo.c", svnRepository);

        svnRepository.checkInOneFile("bar.c");
        materialDatabaseUpdater.updateMaterial(svn);

        configHelper.addPipeline(mingleDownstreamPipelineName, STAGE_NAME, new MaterialConfigs(svn.config(), mingleMaterialConfig), "unit");

        pipelineTimeline.update();
        scheduleHelper.autoSchedulePipelinesWithRealMaterials(mingleDownstreamPipelineName);

        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).contains(new CaseInsensitiveString(mingleDownstreamPipelineName));
        BuildCause downstreamBuildCause = pipelineScheduleQueue.toBeScheduled().get(new CaseInsensitiveString(mingleDownstreamPipelineName));
        for (MaterialRevision materialRevision : downstreamBuildCause.getMaterialRevisions()) {
            assertThat(materialRevision.isChanged()).isTrue();
        }
        assertThat(downstreamBuildCause.getMaterialRevisions().getRevisions().size()).isEqualTo(2);
    }

    @Test
    public void shouldUnderstandChangedMaterial_forBisectAfterBisect() throws Exception {
        SvnMaterial svn = new SvnMaterial(repository);

        runAndPassWith(svn, "foo.c", svnRepository);
        MaterialRevisions revsAfterFoo = checkinFile(svn, "foo_other.c", svnRepository);
        runAndPassWith(svn, "bar.c", revsAfterFoo, svnRepository);
        MaterialRevisions revsAfterBar = checkinFile(svn, "bar_other.c", svnRepository);
        runAndPassWith(svn, "baz.c", revsAfterBar, svnRepository);

        runAndPass(revsAfterFoo);
        String revisionForFingerPrint = revsAfterBar.findRevisionForFingerPrint(svn.getFingerprint()).getRevision().getRevision();
        scheduleHelper.manuallySchedulePipelineWithRealMaterials(MINGLE_PIPELINE_NAME, new Username(new CaseInsensitiveString("loser")), Map.of(mingleConfig.materialConfigs().get(0).getPipelineUniqueFingerprint(), revisionForFingerPrint));

        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).contains(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        BuildCause bisectAfterBisectBuildCause = pipelineScheduleQueue.toBeScheduled().get(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        for (MaterialRevision materialRevision : bisectAfterBisectBuildCause.getMaterialRevisions()) {
            assertThat(materialRevision.isChanged()).isFalse();
        }
        assertThat(bisectAfterBisectBuildCause.getMaterialRevisions().getRevisions().size()).isEqualTo(1);
    }

    @Test
    public void shouldUnderstandChangedMaterial_forManual_triggerWithOptions_DoneWithANewRevision() throws Exception {
        SvnMaterial svn = new SvnMaterial(repository);

        MaterialRevisions revsAfterFoo = checkinFile(svn, "foo.c", svnRepository);

        String revisionForFingerPrint = revsAfterFoo.findRevisionForFingerPrint(svn.getFingerprint()).getRevision().getRevision();
        scheduleHelper.manuallySchedulePipelineWithRealMaterials(MINGLE_PIPELINE_NAME, new Username(new CaseInsensitiveString("loser")), Map.of(new MaterialConfigConverter().toMaterial(mingleConfig.materialConfigs().get(0)).getPipelineUniqueFingerprint(), revisionForFingerPrint));

        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).contains(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        BuildCause bisectAfterBisectBuildCause = pipelineScheduleQueue.toBeScheduled().get(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        for (MaterialRevision materialRevision : bisectAfterBisectBuildCause.getMaterialRevisions()) {
            assertThat(materialRevision.isChanged()).isTrue();
        }
        assertThat(bisectAfterBisectBuildCause.getMaterialRevisions().getRevisions().size()).isEqualTo(1);
    }

    @Test
    public void should_NOT_markAsChangedWhenMaterialIsReIntroducedWithSameRevisionsToPipeline(@TempDir Path tempDir) throws Exception {
        SvnMaterial svn1 = new SvnMaterial(repository);
        svn1.setFolder("another_repo");
        mingleConfig = configHelper.replaceMaterialForPipeline(MINGLE_PIPELINE_NAME, svn1.config());
        runAndPassWith(svn1, "foo.c", svnRepository);

        SvnTestRepo svn2Repository = new SvnTestRepo(tempDir);
        Subversion repository2 = new SvnCommand(null, svn2Repository.projectRepositoryUrl());
        SvnMaterial svn2 = new SvnMaterial(repository2);
        svn2.setFolder("boulder");

        checkinFile(svn2, "bar.c", svn2Repository);

        mingleConfig = configHelper.addMaterialToPipeline(MINGLE_PIPELINE_NAME, svn2.config());

        scheduleHelper.autoSchedulePipelinesWithRealMaterials(MINGLE_PIPELINE_NAME);

        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).contains(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        BuildCause mingleBuildCause = pipelineScheduleQueue.toBeScheduled().get(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        verifyChanged(svn2, mingleBuildCause, true);
        verifyChanged(svn1, mingleBuildCause, false);//this should not have changed, as foo.c was already built in the previous instance

        runAndPass(mingleBuildCause.getMaterialRevisions());

        mingleConfig = configHelper.replaceMaterialForPipeline(MINGLE_PIPELINE_NAME, svn1.config());
        scheduleHelper.autoSchedulePipelinesWithRealMaterials(MINGLE_PIPELINE_NAME);

        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).contains(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        mingleBuildCause = pipelineScheduleQueue.toBeScheduled().get(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        verifyChanged(svn1, mingleBuildCause, false);//this should not have changed, as foo.c was already built in the previous instance
        runAndPassWith(svn1, "baz.c", svnRepository);

        mingleConfig = configHelper.addMaterialToPipeline(MINGLE_PIPELINE_NAME, svn2.config());

        checkinFile(svn1, "quux.c", svnRepository);

        scheduleHelper.autoSchedulePipelinesWithRealMaterials(MINGLE_PIPELINE_NAME);

        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).contains(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        mingleBuildCause = pipelineScheduleQueue.toBeScheduled().get(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        verifyChanged(svn2, mingleBuildCause, false);
        verifyChanged(svn1, mingleBuildCause, true);
    }

    @Test
    public void should_produceBuildCause_whenMaterialConfigurationChanges() throws Exception {
        SvnMaterial svn1 = new SvnMaterial(repository);
        mingleConfig = configHelper.replaceMaterialForPipeline(MINGLE_PIPELINE_NAME, svn1.config());
        runAndPassWith(svn1, "foo.c", svnRepository);

        scheduleHelper.autoSchedulePipelinesWithRealMaterials(MINGLE_PIPELINE_NAME);

        BuildCause mingleBuildCause = pipelineScheduleQueue.toBeScheduled().get(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        assertThat(mingleBuildCause).isNull();

        svn1.setFolder("another_repo");
        mingleConfig = configHelper.replaceMaterialForPipeline(MINGLE_PIPELINE_NAME, svn1.config());

        scheduleHelper.autoSchedulePipelinesWithRealMaterials(MINGLE_PIPELINE_NAME);

        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).contains(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        mingleBuildCause = pipelineScheduleQueue.toBeScheduled().get(new CaseInsensitiveString(MINGLE_PIPELINE_NAME));
        verifyChanged(svn1, mingleBuildCause, false);//because material configuration changed, and not actual revisions
    }

    @Test
    public void shouldNotAutoSchedulePausedPipeline() {
        ScheduleTestUtil u = new ScheduleTestUtil(transactionTemplate, materialRepository, dbHelper, configHelper);
        HgMaterial hg = new HgMaterial("url", null);
        String[] hg_revs = {"h1", "h2"};
        u.checkinInOrder(hg, hg_revs);

        ScheduleTestUtil.AddedPipeline p1 = u.saveConfigWith("p1", u.m(hg));
        ScheduleTestUtil.AddedPipeline p2 = u.saveConfigWith("p2", u.m(hg));
        pipelinePauseService.pause(p1.config.name().toString(), "pausing p1", Username.ANONYMOUS);
        ServerHealthStateOperationResult p1Result = new ServerHealthStateOperationResult();
        service.autoSchedulePipeline(p1.config.name().toString(), p1Result, 1234);
        assertThat(p1Result.canContinue()).isFalse();

        ServerHealthStateOperationResult p2Result = new ServerHealthStateOperationResult();
        service.autoSchedulePipeline(p2.config.name().toString(), p2Result, 1234);
        assertThat(p2Result.canContinue()).isTrue();
    }


    @Test
    public void shouldNotTriggerMDUOfMaterialsForManualTriggerOfPipelineIfMDUOptionIsTurnedOFFInRequest() {
        scheduleOptions.shouldPerformMDUBeforeScheduling(false);
        service.manualSchedulePipeline(username, manualTriggerPipeline.name(), scheduleOptions, result);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.message()).isEqualTo(String.format("Request to schedule pipeline %s accepted", manualTriggerPipeline.name()));
        assertThat(materialUpdateStatusNotifier.hasListenerFor(manualTriggerPipeline)).isFalse();
        assertThat(triggerMonitor.isAlreadyTriggered(manualTriggerPipeline.name())).isFalse();

        BuildCause buildCause = pipelineScheduleQueue.toBeScheduled().get(manualTriggerPipeline.name());
        assertNotNull(buildCause);
        assertThat(buildCause.getApprover()).isEqualTo(username.getDisplayName());
        assertThat(buildCause.getMaterialRevisions().numberOfRevisions()).isEqualTo(1);
        assertThat(buildCause.getMaterialRevisions().getModifications(materialForManualTriggerPipeline).getRevision()).isEqualTo("s1");
    }

    @Test
    public void shouldTriggerMDUOfConfigRepoMaterialIfThePipelineIsDefinedRemotelyInAConfigRepo_ManualTriggerOfPipeline_EvenIfMDUOptionIsTurnedOFFInRequest() {
        ConfigRepoConfig repoConfig = ConfigRepoConfig.createConfigRepoConfig(git("url2"), "plugin", "id-2");
        repoConfig.getRules().add(new Allow("refer", "*", "*"));
        configHelper.addConfigRepo(repoConfig);
        PartialConfig partialConfig = PartialConfigMother.withPipelineMultipleMaterials("remote_pipeline", new RepoConfigOrigin(repoConfig, "4567"));
        PipelineConfig remotePipeline = partialConfig.getGroups().first().getPipelines().get(0);
        GitMaterial git = u.wf((GitMaterial) new MaterialConfigConverter().toMaterial(remotePipeline.materialConfigs().getGitMaterial()), "git");
        u.checkinInOrder(git, u.d(1), "g1r1");
        SvnMaterial svn = u.wf((SvnMaterial) new MaterialConfigConverter().toMaterial(remotePipeline.materialConfigs().getSvnMaterial()), "svn");
        u.checkinInOrder(svn, u.d(1), "svn1r11");
        GitMaterial configRepoMaterial = u.wf((GitMaterial) new MaterialConfigConverter().toMaterial(repoConfig.getRepo()), "git");
        u.checkinInOrder(configRepoMaterial, u.d(1), "s1");
        partialConfigService.onSuccessPartialConfig(repoConfig, partialConfig);
        assertTrue(goConfigService.hasPipelineNamed(remotePipeline.name()));
        scheduleOptions.shouldPerformMDUBeforeScheduling(false);

        service.manualSchedulePipeline(username, remotePipeline.name(), scheduleOptions, result);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.message()).isEqualTo("Request to schedule pipeline remote_pipeline accepted");
        assertThat(materialUpdateStatusNotifier.hasListenerFor(remotePipeline)).isTrue();
        assertMDUPendingForMaterial(remotePipeline, configRepoMaterial);
        assertMDUNotPendingForMaterial(remotePipeline, svn);
        assertMDUNotPendingForMaterial(remotePipeline, git);
        assertThat(triggerMonitor.isAlreadyTriggered(remotePipeline.name())).isTrue();
        BuildCause buildCause = pipelineScheduleQueue.toBeScheduled().get(new CaseInsensitiveString(remotePipeline.name().toString()));
        assertNull(buildCause);
    }

    @Test
    public void shouldTriggerMDUOfMaterialsForManualTriggerOfPipelineIfMDUOptionIsTurnedONInRequest() {
        scheduleOptions.shouldPerformMDUBeforeScheduling(true);
        service.manualSchedulePipeline(username, manualTriggerPipeline.name(), scheduleOptions, result);

        assertThat(materialUpdateStatusNotifier.hasListenerFor(manualTriggerPipeline)).isTrue();
        assertMDUPendingForMaterial(manualTriggerPipeline, materialForManualTriggerPipeline);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.message()).isEqualTo(String.format("Request to schedule pipeline %s accepted", manualTriggerPipeline.name()));
        assertThat(triggerMonitor.isAlreadyTriggered(manualTriggerPipeline.name())).isTrue();
        BuildCause buildCause = pipelineScheduleQueue.toBeScheduled().get(new CaseInsensitiveString(manualTriggerPipeline.name().toString()));
        assertNull(buildCause);
    }

    private void assertMDUPendingForMaterial(PipelineConfig remotePipeline, Material material) {
        assertMDUPending(remotePipeline, material, true);
    }

    private void assertMDUNotPendingForMaterial(PipelineConfig remotePipeline, Material material) {
        assertMDUPending(remotePipeline, material, false);
    }

    private void assertMDUPending(PipelineConfig remotePipeline, Material material, boolean pending) {
        ConcurrentMap<String, MaterialUpdateStatusListener> pendingListeners = ReflectionUtil.getField(materialUpdateStatusNotifier, "pending");
        assertThat(pendingListeners.get(CaseInsensitiveString.str(remotePipeline.name())).isListeningFor(material)).isEqualTo(pending);
    }

    private void verifyChanged(Material material, BuildCause bc, final boolean changed) {
        MaterialRevision svn2MaterialRevision = bc.getMaterialRevisions().findRevisionForFingerPrint(material.getFingerprint());
        assertThat(svn2MaterialRevision.isChanged()).describedAs("material revision " + svn2MaterialRevision + " was marked as" + (changed ? " not" : "") + " changed").isEqualTo(changed);
    }

    @SuppressWarnings("UnusedReturnValue")
    private MaterialRevisions runAndPassWith(SvnMaterial svn, final String checkinFile, final SvnTestRepo svnRepository) throws Exception {
        return runAndPassWith(svn, checkinFile, null, svnRepository);
    }

    private MaterialRevisions runAndPassWith(SvnMaterial svn, String checkinFile, MaterialRevisions revsAfterFoo, final SvnTestRepo svnRepository) throws Exception {
        MaterialRevisions newRevs = checkinFile(svn, checkinFile, svnRepository);
        if (revsAfterFoo != null) {
            for (MaterialRevision newRev : newRevs) {
                newRev.addModifications(revsAfterFoo.getModifications(newRev.getMaterial()));
            }
        }
        runAndPass(newRevs);
        return newRevs;
    }

    private void runAndPass(MaterialRevisions mingleRev) {
        BuildCause buildCause = BuildCause.createWithModifications(mingleRev, "boozer");
        latestPipeline = PipelineMother.schedule(mingleConfig, buildCause);
        latestPipeline = pipelineDao.saveWithStages(latestPipeline);
        dbHelper.passStage(latestPipeline.getStages().first());
    }

    private MaterialRevisions checkinFile(SvnMaterial svn, String checkinFile, final SvnTestRepo svnRepository) throws Exception {
        svnRepository.checkInOneFile(checkinFile);
        materialDatabaseUpdater.updateMaterial(svn);
        return materialRepository.findLatestModification(svn);
    }

}

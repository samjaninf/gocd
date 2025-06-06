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

import com.thoughtworks.go.config.*;
import com.thoughtworks.go.domain.*;
import com.thoughtworks.go.domain.buildcause.BuildCause;
import com.thoughtworks.go.domain.materials.Material;
import com.thoughtworks.go.domain.materials.Modification;
import com.thoughtworks.go.domain.materials.svn.Subversion;
import com.thoughtworks.go.domain.materials.svn.SvnCommand;
import com.thoughtworks.go.helper.ModificationsMother;
import com.thoughtworks.go.helper.PipelineMother;
import com.thoughtworks.go.helper.SvnTestRepo;
import com.thoughtworks.go.helper.TestRepo;
import com.thoughtworks.go.server.cache.GoCache;
import com.thoughtworks.go.server.dao.DatabaseAccessHelper;
import com.thoughtworks.go.server.dao.JobInstanceDao;
import com.thoughtworks.go.server.dao.PipelineDao;
import com.thoughtworks.go.server.dao.StageDao;
import com.thoughtworks.go.server.domain.Username;
import com.thoughtworks.go.server.persistence.MaterialRepository;
import com.thoughtworks.go.server.transaction.TransactionTemplate;
import com.thoughtworks.go.util.GoConfigFileHelper;
import com.thoughtworks.go.util.GoConstants;
import com.thoughtworks.go.util.TimeProvider;
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
import java.util.Date;

import static com.thoughtworks.go.helper.ModificationsMother.modifySomeFiles;
import static com.thoughtworks.go.server.dao.DatabaseAccessHelper.AGENT_UUID;
import static com.thoughtworks.go.util.GoConstants.DEFAULT_APPROVED_BY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {
        "classpath:/applicationContext-global.xml",
        "classpath:/applicationContext-dataLocalAccess.xml",
        "classpath:/testPropertyConfigurer.xml",
        "classpath:/spring-all-servlet.xml",
})
public class BuildRepositoryServiceIntegrationTest {
    private static final GoConfigFileHelper config = new GoConfigFileHelper();
    private static final String HOSTNAME = "10.18.0.1";
    private static final String PIPELINE_NAME = "mingle";
    private static final String DEV_STAGE = "dev";
    private static final String FT_STAGE = "ft";
    private static final String MD5 = "md5-test";

    @Autowired
    private BuildRepositoryService buildRepositoryService;
    @Autowired
    private StageService stageService;
    @Autowired
    private PipelineService pipelineService;
    @Autowired
    private GoConfigDao goConfigDao;
    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private PipelineDao pipelineDao;
    @Autowired
    private JobInstanceDao jobInstanceDao;
    @Autowired
    private JobInstanceService jobInstanceService;
    @Autowired
    private GoConfigService goConfigService;
    @Autowired
    private PipelineScheduleQueue pipelineScheduleQueue;
    @Autowired
    private DatabaseAccessHelper dbHelper;
    @Autowired
    private MaterialRepository materialRepository;
    @Autowired
    private GoCache goCache;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private StageDao stageDao;
    @Autowired
    private InstanceFactory instanceFactory;
    @Autowired
    private AgentService agentService;

    private PipelineConfig mingle;
    private Pipeline pipeline;
    private TestRepo svnTestRepo;
    private Subversion svnRepo;


    @BeforeEach
    public void setUp(@TempDir Path tempDir) throws Exception {

        dbHelper.onSetUp();
        config.onSetUp();
        config.usingCruiseConfigDao(goConfigDao);
        goConfigService.forceNotifyListeners();

        svnTestRepo = new SvnTestRepo(tempDir);

        svnRepo = new SvnCommand(null, svnTestRepo.projectRepositoryUrl());
        config.addPipeline(PIPELINE_NAME, DEV_STAGE, svnRepo, "foo");
        mingle = config.addStageToPipeline(PIPELINE_NAME, FT_STAGE, "bar");
        agentService.saveOrUpdate(new Agent(AGENT_UUID, HOSTNAME, "127.0.0.1", "cookie"));
        pipeline = dbHelper.newPipelineWithAllStagesPassed(mingle);
        goCache.clear();
    }

    @AfterEach
    public void teardown() throws Exception {
        dbHelper.onTearDown();
        config.onTearDown();
        pipelineScheduleQueue.clear();
    }

    @Test
    public void shouldScheduleNextStageAndPipelineWhenStagePassed() throws Exception {
        createPipelineWithFirstStageCompletedAndNextStageBuilding(StageState.Passed);

        Stage stage1 = stageDao.mostRecentWithBuilds(PIPELINE_NAME, mingle.findBy(new CaseInsensitiveString(DEV_STAGE)));
        assertThat(stage1.isApproved()).isTrue();
        assertThat(stage1.getApprovedBy()).isEqualTo(DEFAULT_APPROVED_BY);

        Stage stage2 = stageDao.mostRecentWithBuilds(PIPELINE_NAME, mingle.findBy(new CaseInsensitiveString(FT_STAGE)));
        assertThat(stage2.stageState()).isEqualTo(StageState.Building);
        assertThat(stage2.getApprovedBy()).isEqualTo(DEFAULT_APPROVED_BY);

        assertThat(stage1.getPipelineId()).isEqualTo(stage2.getPipelineId());
    }

    @Test
    public void shouldNotTriggerPipelineWhenCurrentStageNotSuccessful() throws Exception {
        PipelineConfig product = config.addPipeline("product", "dev", svnRepo, "build");
        config.setDependencyOn(product, PIPELINE_NAME, DEV_STAGE);

        int oldSize = pipelineScheduleQueue.toBeScheduled().size();
        createPipelineWithFirstStageCompletedAndNextStageBuilding(StageState.Failed);

        assertThat(pipelineScheduleQueue.toBeScheduled().size()).isEqualTo(oldSize);
    }

    @Test
    public void shouldNotSkipBuildingStateWhenTransitionFromPreparingToCompleting() {
        //Bug Fix for #1630
        PipelineConfig pipelineConfig = config.addPipeline("product", "dev", svnRepo, "build");
        Pipeline pipelineInPreparingState = PipelineMother.preparing(pipelineConfig);
        dbHelper.savePipelineWithStagesAndMaterials(pipelineInPreparingState);
        Pipeline reloadedPipeline = pipelineDao.mostRecentPipeline("product");
        Stage stage = reloadedPipeline.getFirstStage();
        JobInstance job = stage.getJobInstances().first();
        String agentUuid = job.getAgentUuid();
        long buildId = job.getId();

        buildRepositoryService.completing(new JobIdentifier(reloadedPipeline, stage, job), JobResult.Failed,
                agentUuid);

        JobInstance reloadedJob = jobInstanceDao.buildByIdWithTransitions(buildId);
        assertThat(reloadedJob.getTransitions().byState(JobState.Building)).isNotNull();
    }

    @Test
    public void shouldNotScheduleDuplicatedStage() throws Exception {
        Pipeline oldPipeline = dbHelper.newPipelineWithFirstStagePassed(mingle);
        Pipeline latestPipeline = dbHelper.newPipelineWithAllStagesPassed(mingle);

        int oldSize = latestPipeline.getStages().size();

        Stage stage = oldPipeline.getStages().first();
        scheduleService.automaticallyTriggerRelevantStagesFollowingCompletionOf(stage);

        oldPipeline = pipelineDao.loadPipeline(oldPipeline.getId());
        Stage ftStage = oldPipeline.getStages().byName(FT_STAGE);
        JobInstance secondJob = ftStage.getJobInstances().first();
        secondJob.setIdentifier(new JobIdentifier(oldPipeline, ftStage, secondJob));
        secondJob.setAgentUuid(AGENT_UUID);

        jobInstanceDao.updateAssignedInfo(secondJob);

        reportJobPassed(secondJob);

        latestPipeline = pipelineDao.loadPipeline(latestPipeline.getId());
        assertThat(latestPipeline.getStages().size()).isEqualTo(oldSize);
    }

    @Test
    public void shouldNotUpdateIgnoredBuildStatus() throws Exception {
        Stage stage = dbHelper.saveBuildingStage("studios", "dev");
        JobInstance job = stage.getJobInstances().get(0);
        scheduleService.rescheduleJob(job);
        reportJobPassed(job);
        JobInstance reloaded = jobInstanceDao.buildByIdWithTransitions(job.getId());
        assertThat(reloaded.getState()).isEqualTo(JobState.Rescheduled);
    }

    @Test
    public void shouldNotUpdateIgnoredBuildResult() {
        Stage stage = dbHelper.saveBuildingStage("studios", "dev");
        JobInstance job = stage.getJobInstances().get(0);
        scheduleService.rescheduleJob(job);
        buildRepositoryService.completing(job.getIdentifier(), JobResult.Passed, AGENT_UUID);
        JobInstance reloaded = jobInstanceDao.buildByIdWithTransitions(job.getId());
        assertThat(reloaded.getResult()).isEqualTo(JobResult.Unknown);
    }

    @Test
    public void shouldNotScheduleNextStageWhenApprovalRequiredAndStagePassed() throws Exception {
        config.requireApproval(PIPELINE_NAME, FT_STAGE);
        Pipeline newPipeline = createPipelineWithFirstStageBuilding(mingle);
        completeStageAndTrigger(newPipeline.getFirstStage());

        Stage stage1 = stageDao.mostRecentWithBuilds(PIPELINE_NAME, mingle.findBy(new CaseInsensitiveString(DEV_STAGE)));
        assertThat(stage1.isApproved()).isTrue();
        assertThat(stage1.getApprovedBy()).isEqualTo(GoConstants.DEFAULT_APPROVED_BY);
        assertThat(stage1.getJobInstances().first().getResult()).isEqualTo(JobResult.Passed);

        Stage stage2 = stageDao.mostRecentWithBuilds(PIPELINE_NAME, mingle.findBy(new CaseInsensitiveString(FT_STAGE)));
        assertThat(stage2.getPipelineId()).isEqualTo(pipeline.getId());
        assertThat(stage2.stageState()).isEqualTo(StageState.Passed);

        assertThat(stage1.getPipelineId()).isNotEqualTo(stage2.getPipelineId());
    }

    @Test
    public void shouldNotScheduleNextStageIfItContainsNoJobs() throws Exception {
        config.setRunOnAllAgents(PIPELINE_NAME, FT_STAGE, "bar", true);
        config.addResourcesFor(PIPELINE_NAME, FT_STAGE, "bar", "non-existent");
        Pipeline newPipeline = createPipelineWithFirstStageBuilding(mingle);
        try {
            completeStageAndTrigger(newPipeline.getFirstStage());
            fail("should not have scheduled the stage");
        } catch (CannotScheduleException e) {
            // ok
        }

        Stage stage1 = stageDao.mostRecentWithBuilds(PIPELINE_NAME, mingle.findBy(new CaseInsensitiveString(DEV_STAGE)));
        assertThat(stage1.stageState()).isEqualTo(StageState.Passed);

        Stage stage2 = stageService.findStageWithIdentifier(new StageIdentifier(newPipeline.getIdentifier(), FT_STAGE, "1"));
        assertThat(stage2).isInstanceOf(NullStage.class);
    }

    @Test
    public void shouldScheduleCurrentStageInNextEligiblePipeline() throws Exception {
        Stage oldFtStage = createPipelineWithFirstStageCompletedAndNextStageBuilding(StageState.Passed);
        createNewPipelineWithFirstStagePassed();
        Stage mostRecentPassedStage = createNewPipelineWithFirstStagePassed();
        createNewPipelineWithFirstStageFailed();

        dbHelper.passStage(oldFtStage);
        reportJobPassed(oldFtStage.getJobInstances().get(0));

        Stage mingleFt = stageDao.mostRecentWithBuilds(PIPELINE_NAME, mingle.findBy(new CaseInsensitiveString(FT_STAGE)));
        assertThat(mingleFt.getPipelineId()).isEqualTo(mostRecentPassedStage.getPipelineId());
    }

    @Test
    public void shouldUpdateStageStatusWhenAllJobsPass() throws Exception {
        Stage stage1 = createPipelineWithFirstStageCompletedAndNextStageBuilding(StageState.Passed);
        assertThat(stage1.getResult()).isEqualTo(StageResult.Unknown);
        JobInstances jobs = stage1.getJobInstances();
        for (JobInstance job : jobs) {
            buildRepositoryService.completing(job.getIdentifier(), JobResult.Passed, AGENT_UUID);
            reportJobPassed(job);
        }

        Stage after = stageService.stageById(stage1.getId());
        JobInstances instances = after.getJobInstances();
        assertThat(instances.size()).isEqualTo(1);
        assertThat(instances.get(0).getResult()).isEqualTo(JobResult.Passed);
        assertThat(instances.get(0).getState()).isEqualTo(JobState.Completed);

        assertThat(after.getResult()).isEqualTo(StageResult.Passed);
    }


    @Test
    public void shouldNotScheduleCurrentStageIfAlreadyMostRecentPipeline() throws Exception {
        Stage mostRecent = createPipelineWithFirstStageCompletedAndNextStageBuilding(StageState.Passed);

        reportJobPassed(mostRecent.getJobInstances().get(0));

        Stage mingleFt = stageDao.mostRecentWithBuilds(PIPELINE_NAME, mingle.findBy(new CaseInsensitiveString(FT_STAGE)));
        assertThat(mingleFt.getPipelineId()).isEqualTo(mostRecent.getPipelineId());
    }

    @Test
    public void shouldNotScheduleNextStageWhenStageAlreadyActive() throws Exception {
        createPipelineWithFirstStageCompletedAndNextStageBuilding(StageState.Passed);
        Stage originalFt = stageDao.mostRecentWithBuilds(PIPELINE_NAME, mingle.findBy(new CaseInsensitiveString(FT_STAGE)));
        createPipelineWithFirstStageCompletedAndNextStageBuilding(StageState.Passed);
        Stage mingleFt = stageDao.mostRecentWithBuilds(PIPELINE_NAME, mingle.findBy(new CaseInsensitiveString(FT_STAGE)));
        assertThat(mingleFt.getId()).isEqualTo(originalFt.getId());
    }

    @Test
    public void shouldTriggerCurrentStageInNextEligiblePipelineIfPrevStageIsAutoApproval() throws Exception {
        Stage oldFtStage = createPipelineWithFirstStageCompletedAndNextStageBuilding(StageState.Passed);
        createPipelineWithFirstStageCompleted(mingle);
        completeStageAndTrigger(oldFtStage);
        Stage mostRecent = stageDao.mostRecentWithBuilds(PIPELINE_NAME, mingle.findBy(new CaseInsensitiveString(FT_STAGE)));
        assertThat(mostRecent.getPipelineId()).isEqualTo(oldFtStage.getPipelineId() + 1);
    }

    @Test
    public void shouldNotTriggerCurrentStageInAnyNewerPipelineIfCurrentStageIsManualApproval() throws Exception {
        config.configureStageAsManualApproval(PIPELINE_NAME, FT_STAGE);
        Stage oldFtStage = createPipelineWithFirstStageCompletedAndNextStageBuilding(StageState.Passed);
        createPipelineWithFirstStageCompleted(mingle);
        Stage mostRecent = stageDao.mostRecentWithBuilds(PIPELINE_NAME, mingle.findBy(new CaseInsensitiveString(FT_STAGE)));
        assertThat(mostRecent.getId()).isEqualTo(oldFtStage.getId());
    }

    @SuppressWarnings("UnusedReturnValue")
    private JobInstance completeStageAndTrigger(Stage oldFtStage) throws Exception {
        JobInstance job = oldFtStage.getJobInstances().first();
        buildRepositoryService.completing(job.getIdentifier(), JobResult.Passed, AGENT_UUID);
        reportJobPassed(job);
        return jobInstanceService.buildByIdWithTransitions(job.getId());
    }

    @Test
    public void shouldCancelAllJobs() {
        mingle = PipelineMother.twoBuildPlansWithResourcesAndSvnMaterialsAtUrl(PIPELINE_NAME, DEV_STAGE,
                svnTestRepo.projectRepositoryUrl());
        StageConfig devStage = mingle.get(0);
        schedulePipeline(mingle);
        final Stage stage = stageDao.mostRecentWithBuilds(CaseInsensitiveString.str(mingle.name()), devStage);
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status) {
                stageService.cancelStage(stage, null);
            }
        });

        final Stage cancelled = stageService.stageById(stage.getId());
        assertThat(cancelled.stageState()).isEqualTo(StageState.Cancelled);
        final JobInstances builds = cancelled.getJobInstances();
        for (JobInstance job : builds) {
            assertThat(job.currentStatus()).isEqualTo(JobState.Completed);
            assertThat(job.getResult()).isEqualTo(JobResult.Cancelled);
            assertThat(job.displayStatusWithResult()).isEqualTo("cancelled");
        }
    }

    private Stage createPipelineWithFirstStageCompletedAndNextStageBuilding(StageState stageState) throws Exception {
        Pipeline newPipeline = createPipelineWithFirstStageBuilding(mingle);
        Stage mostRecent = newPipeline.getFirstStage();
        if (stageState.equals(StageState.Failed)) {
            dbHelper.failStage(mostRecent);
        } else {
            dbHelper.passStage(mostRecent);
        }
        reportJobPassed(mostRecent.getJobInstances().first());
        Stage nextStage = stageDao.mostRecentWithBuilds(PIPELINE_NAME, mingle.findBy(new CaseInsensitiveString(FT_STAGE)));
        dbHelper.buildingBuildInstance(nextStage);
        return nextStage;
    }

    private void reportJobPassed(JobInstance jobInstance) throws Exception {
        buildRepositoryService.updateStatusFromAgent(jobInstance.getIdentifier(), JobState.Completed, AGENT_UUID);
    }

    private void reportJobCompleting(JobInstance jobInstance) {
        buildRepositoryService.completing(jobInstance.getIdentifier(), JobResult.Passed, AGENT_UUID);
    }

    @SuppressWarnings("UnusedReturnValue")
    private Pipeline createPipelineWithFirstStageCompleted(PipelineConfig pipeline) throws Exception {
        Stage firstStage = createPipelineWithFirstStageBuilding(pipeline).getFirstStage();
        reportJobCompleting(firstStage.getJobInstances().first());
        reportJobPassed(firstStage.getJobInstances().first());
        return pipelineDao.mostRecentPipeline(CaseInsensitiveString.str(pipeline.name()));
    }

    private Pipeline createPipelineWithFirstStageBuilding(PipelineConfig pipeline) {
        Pipeline scheduledPipeline = schedulePipeline(pipeline);
        dbHelper.saveBuildingStage(scheduledPipeline.getFirstStage());

        return pipelineDao.mostRecentPipeline(CaseInsensitiveString.str(pipeline.name()));
    }

    private Pipeline schedulePipeline(final PipelineConfig pipeline) {
        return transactionTemplate.execute(status -> {
            MaterialRevisions materialRevisions = new MaterialRevisions();
            for (Material material : new MaterialConfigConverter().toMaterials(pipeline.materialConfigs())) {
                materialRevisions.addRevision(material, new Modification("user", "comment", null, new Date(), ModificationsMother.nextRevision()));
            }
            materialRepository.save(materialRevisions);
            Pipeline scheduledPipeline = instanceFactory.createPipelineInstance(pipeline, BuildCause.createManualForced(materialRevisions, Username.ANONYMOUS),
                    new DefaultSchedulingContext(DEFAULT_APPROVED_BY), MD5, new TimeProvider());
            pipelineService.save(scheduledPipeline);
            return scheduledPipeline;
        });
    }

    @SuppressWarnings("UnusedReturnValue")
    private Stage createNewPipelineWithFirstStageFailed() {
        return transactionTemplate.execute(status -> {
            Pipeline forcedPipeline = instanceFactory.createPipelineInstance(mingle, modifySomeFiles(mingle), new DefaultSchedulingContext(DEFAULT_APPROVED_BY), MD5, new TimeProvider());
            materialRepository.save(forcedPipeline.getBuildCause().getMaterialRevisions());
            pipelineService.save(forcedPipeline);
            Stage stage = forcedPipeline.getFirstStage();
            dbHelper.failStage(stage);
            return stage;
        });
    }

    private Stage createNewPipelineWithFirstStagePassed() {
        Pipeline forcedPipeline = schedulePipeline(mingle);
        dbHelper.passStage(forcedPipeline.getFirstStage());
        return forcedPipeline.getFirstStage();
    }
}

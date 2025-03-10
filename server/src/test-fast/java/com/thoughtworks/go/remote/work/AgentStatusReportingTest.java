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
package com.thoughtworks.go.remote.work;

import com.thoughtworks.go.domain.AgentRuntimeStatus;
import com.thoughtworks.go.remote.AgentIdentifier;
import com.thoughtworks.go.server.service.AgentBuildingInfo;
import com.thoughtworks.go.server.service.AgentRuntimeInfo;
import com.thoughtworks.go.util.command.EnvironmentVariableContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.thoughtworks.go.util.SystemUtil.currentWorkingDirectory;
import static org.assertj.core.api.Assertions.assertThat;

public class AgentStatusReportingTest {

    private AgentIdentifier agentIdentifier;
    private EnvironmentVariableContext environmentVariableContext;
    private GoArtifactsManipulatorStub artifactManipulator;
    private BuildRepositoryRemoteStub buildRepository;
    private AgentRuntimeInfo agentRuntimeInfo;

    @BeforeEach
    public void before() {
        agentIdentifier = new AgentIdentifier("localhost", "127.0.0.1", "uuid");
        environmentVariableContext = new EnvironmentVariableContext();
        artifactManipulator = new GoArtifactsManipulatorStub();
        buildRepository = new com.thoughtworks.go.remote.work.BuildRepositoryRemoteStub();
        this.agentRuntimeInfo = new AgentRuntimeInfo(agentIdentifier, AgentRuntimeStatus.Idle, currentWorkingDirectory(), "cookie");
    }

    @Test
    public void shouldReportBuildingWhenAgentRunningBuildWork() throws Exception {
        Work work = BuildWorkTest.getWork(WILL_PASS, BuildWorkTest.PIPELINE_NAME);
        work.doWork(environmentVariableContext, new AgentWorkContext(agentIdentifier, buildRepository, artifactManipulator, agentRuntimeInfo, null, null, null, null));
        AgentRuntimeInfo agentRuntimeInfo1 = new AgentRuntimeInfo(agentIdentifier, AgentRuntimeStatus.Idle, currentWorkingDirectory(), "cookie");
        agentRuntimeInfo1.busy(new AgentBuildingInfo("pipeline1/100/mingle/100/run-ant", "pipeline1/100/mingle/100/run-ant"));
        assertThat(agentRuntimeInfo).isEqualTo(agentRuntimeInfo1);
    }

    @Test
    public void shouldReportCancelledWhenAgentCancelledBuildWork() throws Exception {
        Work work = BuildWorkTest.getWork(WILL_PASS, BuildWorkTest.PIPELINE_NAME);
        work.doWork(environmentVariableContext, new AgentWorkContext(agentIdentifier, buildRepository, artifactManipulator, agentRuntimeInfo, null, null, null, null));
        work.cancel(environmentVariableContext, agentRuntimeInfo);

        assertThat(agentRuntimeInfo).isEqualTo(expectedAgentRuntimeInfo());
    }

    private AgentRuntimeInfo expectedAgentRuntimeInfo() {
        AgentRuntimeInfo info = new AgentRuntimeInfo(agentIdentifier, AgentRuntimeStatus.Idle, currentWorkingDirectory(), "cookie");
        info.setBuildingInfo(new AgentBuildingInfo("pipeline1/100/mingle/100/run-ant", "pipeline1/100/mingle/100/run-ant"));
        info.cancel();
        return info;
    }

    private static final String WILL_PASS = """
            <job name="run-ant">
              <tasks>
                <ant target="--help" />
              </tasks>
            </job>""";
}

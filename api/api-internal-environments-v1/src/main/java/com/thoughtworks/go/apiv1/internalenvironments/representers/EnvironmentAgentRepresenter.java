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
package com.thoughtworks.go.apiv1.internalenvironments.representers;

import com.thoughtworks.go.api.base.OutputWriter;
import com.thoughtworks.go.config.EnvironmentAgentConfig;
import com.thoughtworks.go.config.EnvironmentConfig;
import com.thoughtworks.go.config.remote.ConfigOrigin;
import com.thoughtworks.go.config.remote.FileConfigOrigin;

class EnvironmentAgentRepresenter {
    private static final FileConfigOrigin FILE_CONFIG_ORIGIN = new FileConfigOrigin();

    static void toJSON(OutputWriter writer, EnvironmentAgentConfig agent, EnvironmentConfig environmentConfig) {
        writer.add("uuid", agent.getUuid());
        ConfigOrigin origin = environmentConfig.isLocal()
                ? FILE_CONFIG_ORIGIN
                : environmentConfig.originForAgent(agent.getUuid()).orElse(null);
        writer.addChild("origin", originWriter -> EntityConfigOriginRepresenter.toJSON(originWriter, origin));
    }
}

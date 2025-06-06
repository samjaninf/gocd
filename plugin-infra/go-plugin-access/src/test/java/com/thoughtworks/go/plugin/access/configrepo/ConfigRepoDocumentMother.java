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

import com.bazaarvoice.jolt.JsonUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import static com.bazaarvoice.jolt.utils.JoltUtils.remove;
import static com.bazaarvoice.jolt.utils.JoltUtils.store;

class ConfigRepoDocumentMother {
    String versionOneWithLockingSetTo(boolean enablePipelineLockingValue) {
        Map<String, Object> map = getJSONFor("/v1_simple.json");
        store(map, "1", "target_version");
        store(map, enablePipelineLockingValue, "pipelines", 0, "enable_pipeline_locking");
        return JsonUtils.toJsonString(map);
    }

    String versionOneComprehensiveWithNoLocking() {
        Map<String, Object> map = getJSONFor("/v1_comprehensive.json");
        store(map, "1", "target_version");
        remove(map, "pipelines", 0, "enable_pipeline_locking");
        remove(map, "pipelines", 1, "enable_pipeline_locking");
        return JsonUtils.toJsonString(map);
    }

    private Map<String, Object> getJSONFor(String fileName) {
        try (InputStream stream = Objects.requireNonNull(this.getClass().getResourceAsStream(fileName))) {
            return JsonUtils.jsonToMap(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    String versionTwoComprehensive() {
        return JsonUtils.toJsonString(getJSONFor("/v2_comprehensive.json"));
    }

    String v2WithFetchTask() {
        return JsonUtils.toJsonString(getJSONFor("/v2_with_fetch_tasks.json"));
    }

    String v2WithFetchExternalArtifactTask() {
        return JsonUtils.toJsonString(getJSONFor("/v2_with_fetch_external_artifact_task.json"));
    }

    String v3Comprehensive() {
        return JsonUtils.toJsonString(getJSONFor("/v3_comprehensive.json"));
    }

    String v3WithFetchTask() {
        return JsonUtils.toJsonString(getJSONFor("/v3_with_fetch_tasks.json"));
    }

    String v3WithFetchExternalArtifactTask() {
        return JsonUtils.toJsonString(getJSONFor("/v3_with_fetch_external_artifact_task.json"));
    }

    String v3ComprehensiveWithDisplayOrderWeightsOf10AndNull() {
        return JsonUtils.toJsonString(getJSONFor("/v3_comprehensive_with_display_order_weight_which_was_introduced_in_v4_for_one_pipeline.json"));
    }

    String v4ComprehensiveWithDisplayOrderWeightOfMinusOneForBothPipelines() {
        return JsonUtils.toJsonString(getJSONFor("/v4_comprehensive_with_display_order_weight_of_minus_one_for_both_pipelines.json"));
    }

    String v4ComprehensiveWithDisplayOrderWeightsOf10AndMinusOne() {
        return JsonUtils.toJsonString(getJSONFor("/v4_comprehensive_with_display_order_weights_of_10_and_minus_one.json"));
    }

    String v4Simple() {
        return JsonUtils.toJsonString(getJSONFor("/v4_simple.json"));
    }

    String v5Simple() {
        return JsonUtils.toJsonString(getJSONFor("/v5_simple.json"));
    }

    String v5Pipeline() {
        return JsonUtils.toJsonString(getJSONFor("/v5_pipeline.json"));
    }

    String v6Pipeline() {
        return JsonUtils.toJsonString(getJSONFor("/v6_pipeline.json"));
    }

    String v7Pipeline() {
        return JsonUtils.toJsonString(getJSONFor("/v7_pipeline.json"));
    }

    String v8Pipeline() {
        return JsonUtils.toJsonString(getJSONFor("/v8_pipeline.json"));
    }

    String v8PipelineWithDependencyMaterial() {
        return JsonUtils.toJsonString(getJSONFor("/v8_pipeline_with_dependency_material.json"));
    }

    String v9Pipeline() {
        return JsonUtils.toJsonString(getJSONFor("/v9_pipeline.json"));
    }

    public String v9WithWhitelist() {
        return JsonUtils.toJsonString(getJSONFor("/v9_pipeline_with_whitelist.json"));
    }

    public String v10WithIncludes() {
        return JsonUtils.toJsonString(getJSONFor("/v10_pipeline_with_includes.json"));
    }

    public String v10WithoutTasks() {
        return JsonUtils.toJsonString(getJSONFor("/v10_pipeline_with_no_tasks.json"));
    }

    public String v11WithSleepTasks() {
        return JsonUtils.toJsonString(getJSONFor("/v11_pipeline_with_default_task.json"));
    }

    public String v10WithoutPipelines() {
        return JsonUtils.toJsonString(getJSONFor("/v10_no_pipelines.json"));
    }

    public String v11WithoutPipelines() {
        return JsonUtils.toJsonString(getJSONFor("/v11_no_pipelines.json"));
    }
}

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
package com.thoughtworks.go.apiv1.internalmaterials.representers.materials

import com.thoughtworks.go.config.remote.ConfigRepoConfig
import com.thoughtworks.go.config.remote.RepoConfigOrigin
import com.thoughtworks.go.helper.MaterialConfigsMother
import org.junit.jupiter.api.Test

import static com.thoughtworks.go.api.base.JsonUtils.toObjectString
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson

class PluggableScmMaterialRepresenterTest {

  @Test
  void "should represent a pluggable scm material"() {
    def pluggableSCMMaterial = MaterialConfigsMother.pluggableSCMMaterialConfig()
    def actualJson = toObjectString(MaterialsRepresenter.toJSON(pluggableSCMMaterial))

    assertThatJson(actualJson).isEqualTo([
      type       : 'plugin',
      fingerprint: pluggableSCMMaterial.fingerprint,
      attributes : [
        ref        : "scm-id",
        auto_update: true,
        scm_name   : "scm-scm-id",
        origin     : [
          type: "gocd"
        ]
      ]
    ])
  }

  @Test
  void 'should render config repo origin'() {
    def pluggableSCMMaterial = MaterialConfigsMother.pluggableSCMMaterialConfig()
    def repoConfig = new ConfigRepoConfig()
    repoConfig.setId("config-repo-id")
    pluggableSCMMaterial.getSCMConfig().setOrigins(new RepoConfigOrigin(repoConfig, "some-rev"))
    def actualJson = toObjectString(MaterialsRepresenter.toJSON(pluggableSCMMaterial))

    assertThatJson(actualJson).isEqualTo([
      type       : 'plugin',
      fingerprint: pluggableSCMMaterial.fingerprint,
      attributes : [
        ref        : "scm-id",
        auto_update: true,
        scm_name   : "scm-scm-id",
        origin     : [
          type: "config_repo",
          id  : "config-repo-id"
        ]
      ]
    ])
  }
}

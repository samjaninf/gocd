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
package com.thoughtworks.go.apiv4.configrepos.representers

import com.thoughtworks.go.apiv4.configrepos.ConfigRepoWithResult
import com.thoughtworks.go.config.PartialConfigParseResult
import com.thoughtworks.go.config.materials.mercurial.HgMaterialConfig
import com.thoughtworks.go.config.remote.ConfigRepoConfig
import com.thoughtworks.go.config.remote.PartialConfig
import com.thoughtworks.go.domain.materials.Modification
import com.thoughtworks.go.spark.Routes
import org.junit.jupiter.api.Test

import static com.thoughtworks.go.api.base.JsonUtils.toObjectString
import static com.thoughtworks.go.helper.MaterialConfigsMother.hg
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson

class ConfigRepoWithResultListRepresenterTest {
  private static final String TEST_PLUGIN_ID = "test.configrepo.plugin"
  private static final String TEST_REPO_URL = "https://fakeurl.com"

  @Test
  void toJSON() {
    List<ConfigRepoWithResult> repos = [repo("foo"), repo("bar")]
    def autoSuggestions = new HashMap<String, List<String>>()
    autoSuggestions.put("key1", List.of("val1", "val2"))
    autoSuggestions.put("key2", List.of("val3", "val3"))
    String json = toObjectString({ w -> ConfigRepoWithResultListRepresenter.toJSON(w, repos, autoSuggestions) })

    assertThatJson(json).isEqualTo([
      _links         : [
        self: [href: "http://test.host/go$Routes.ConfigRepos.BASE".toString()]
      ],
      _embedded      : [
        config_repos: [
          expectedRepoJson("foo"),
          expectedRepoJson("bar")
        ]
      ],
      auto_completion: [
        [
          key  : "key1",
          value: ["val1", "val2"]
        ],
        [
          key  : "key2",
          value: ["val3", "val3"]
        ]
      ]
    ])
  }

  static Map expectedRepoJson(String id) {
    return [
      _links                     : [
        self: [href: "http://test.host/go${Routes.ConfigRepos.id(id)}".toString()],
        doc : [href: Routes.ConfigRepos.DOC],
        find: [href: "http://test.host/go${Routes.ConfigRepos.find()}".toString()],
      ],

      id                         : id,
      plugin_id                  : TEST_PLUGIN_ID,
      material                   : [
        type      : "hg",
        attributes: [
          name       : null,
          url        : "${TEST_REPO_URL}/$id".toString(),
          auto_update: true
        ]
      ],
      configuration              : [],
      rules                      : [],
      material_update_in_progress: false,
      parse_info                 : [
        error                     : null,
        good_modification         : [
          "username"     : null,
          "email_address": null,
          "revision"     : "${id}-123".toString(),
          "comment"      : null,
          "modified_time": null
        ],
        latest_parsed_modification: [
          "username"     : null,
          "email_address": null,
          "revision"     : "${id}-123".toString(),
          "comment"      : null,
          "modified_time": null
        ]
      ]
    ]
  }

  static ConfigRepoWithResult repo(String id) {
    HgMaterialConfig materialConfig = hg("$TEST_REPO_URL/$id", "")

    Modification modification = new Modification()
    modification.setRevision("${id}-123")

    PartialConfig partialConfig = new PartialConfig()
    PartialConfigParseResult expectedParseResult = PartialConfigParseResult.parseSuccess(modification, partialConfig)
    return new ConfigRepoWithResult(ConfigRepoConfig.createConfigRepoConfig(materialConfig, TEST_PLUGIN_ID, id), expectedParseResult, false)
  }
}

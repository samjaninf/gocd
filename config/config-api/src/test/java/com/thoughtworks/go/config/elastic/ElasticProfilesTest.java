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
package com.thoughtworks.go.config.elastic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ElasticProfilesTest {

    @Test
    public void shouldFindProfileById() {
        assertThat(new ElasticProfiles().find("foo")).isNull();
        ElasticProfile profile = new ElasticProfile("foo", "prod-cluster");
        assertThat(new ElasticProfiles(profile).find("foo")).isEqualTo(profile);
    }

    @Test
    public void shouldNotAllowMultipleProfilesWithSameId() {
        ElasticProfile profile1 = new ElasticProfile("foo", "prod-cluster");
        ElasticProfile profile2 = new ElasticProfile("foo", "prod-cluster");
        ElasticProfiles profiles = new ElasticProfiles(profile1, profile2);
        profiles.validate(null);

        assertThat(profile1.errors().size()).isEqualTo(1);
        assertThat(profile1.errors().asString()).isEqualTo("Elastic agent profile id 'foo' is not unique");

        assertThat(profile2.errors().size()).isEqualTo(1);
        assertThat(profile2.errors().asString()).isEqualTo("Elastic agent profile id 'foo' is not unique");
    }
}

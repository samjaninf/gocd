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
package com.thoughtworks.go.server.materials;

import com.thoughtworks.go.config.CaseInsensitiveString;
import com.thoughtworks.go.config.materials.dependency.DependencyMaterial;
import com.thoughtworks.go.domain.MaterialRevision;
import com.thoughtworks.go.domain.MaterialRevisions;
import com.thoughtworks.go.domain.materials.MaterialConfig;
import com.thoughtworks.go.domain.materials.Modification;
import com.thoughtworks.go.server.service.GoConfigService;
import com.thoughtworks.go.server.service.MaterialConfigConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpecificMaterialRevisionFactoryTest {
    private SpecificMaterialRevisionFactory specificMaterialRevisionFactory;
    private MaterialChecker mockMaterialChecker;
    private GoConfigService mockGoConfigService;
    private MaterialConfigConverter materialConfigConverter;

    @BeforeEach
    public void setUp() {
        mockMaterialChecker = mock(MaterialChecker.class);
        mockGoConfigService = mock(GoConfigService.class);
        materialConfigConverter = mock(MaterialConfigConverter.class);
        specificMaterialRevisionFactory = new SpecificMaterialRevisionFactory(mockMaterialChecker, mockGoConfigService, materialConfigConverter);
    }

    @Test
    public void shouldCreateDependencyMaterialForAPipeline() {
        DependencyMaterial dependencyMaterial = new DependencyMaterial(new CaseInsensitiveString("upstream"), new CaseInsensitiveString("blah-stage"));
        MaterialConfig dependencyMaterialConfig = dependencyMaterial.config();
        MaterialRevision expected = new MaterialRevision(dependencyMaterial, new Modification(new Date(), "upstream/4/blah-stage/2", "MOCK_LABEL-12", null));

        String upstreamFingerprint = "234fa4";
        when(mockGoConfigService.findMaterial(new CaseInsensitiveString("blahPipeline"), upstreamFingerprint)).thenReturn(dependencyMaterialConfig);
        when(materialConfigConverter.toMaterial(dependencyMaterialConfig)).thenReturn(dependencyMaterial);
        when(mockMaterialChecker.findSpecificRevision(dependencyMaterial, "upstream/4/blah-stage/2")).thenReturn(expected);

        MaterialRevisions materialRevisions = specificMaterialRevisionFactory.create("blahPipeline", Map.of(upstreamFingerprint, "upstream/4/blah-stage/2"));

        assertThat(materialRevisions).isEqualTo(new MaterialRevisions(expected));
    }

    @Test
    public void shouldThrowExceptionWhenSpecifiedMaterialDoesNotExist() {
        when(mockGoConfigService.findMaterial(new CaseInsensitiveString("blahPipeline"), "not-exist")).thenReturn(null);

        try {
            specificMaterialRevisionFactory.create("blahPipeline", Map.of("not-exist", "upstream/500/blah-stage/2"));
            fail("Should not be able to find material");
        } catch (Exception expected) {
            assertThat(expected.getMessage()).isEqualTo("Material with fingerprint [not-exist] for pipeline [blahPipeline] does not exist");
        }
    }

    @Test
    public void shouldThrowExceptionWhenSpecifiedRevisionDoesNotExist() {
        DependencyMaterial dependencyMaterial = new DependencyMaterial(new CaseInsensitiveString("upstream-pipeline"), new CaseInsensitiveString("blah-stage"));
        when(mockGoConfigService.findMaterial(new CaseInsensitiveString("blahPipeline"), dependencyMaterial.getPipelineUniqueFingerprint())).thenReturn(dependencyMaterial.config());
        when(materialConfigConverter.toMaterial(dependencyMaterial.config())).thenReturn(dependencyMaterial);
        when(mockMaterialChecker.findSpecificRevision(dependencyMaterial, "upstream-pipeline/500/blah-stage/2")).thenThrow(new RuntimeException("revision not found"));
        try {
            specificMaterialRevisionFactory.create("blahPipeline", Map.of(dependencyMaterial.getPipelineUniqueFingerprint(), "upstream-pipeline/500/blah-stage/2"));
            fail("Should not be able to find revision");
        } catch (Exception expected) {
            assertThat(expected.getMessage()).isEqualTo("revision not found");
        }
    }

}

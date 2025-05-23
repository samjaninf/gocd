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
package com.thoughtworks.go.config.update;

import com.thoughtworks.go.config.BasicCruiseConfig;
import com.thoughtworks.go.config.CaseInsensitiveString;
import com.thoughtworks.go.config.PipelineConfig;
import com.thoughtworks.go.config.PipelineConfigs;
import com.thoughtworks.go.config.exceptions.EntityType;
import com.thoughtworks.go.config.materials.MaterialConfigs;
import com.thoughtworks.go.config.materials.PackageMaterialConfig;
import com.thoughtworks.go.domain.config.Configuration;
import com.thoughtworks.go.domain.config.ConfigurationKey;
import com.thoughtworks.go.domain.config.ConfigurationProperty;
import com.thoughtworks.go.domain.config.ConfigurationValue;
import com.thoughtworks.go.domain.packagerepository.PackageDefinition;
import com.thoughtworks.go.domain.packagerepository.PackageRepositories;
import com.thoughtworks.go.domain.packagerepository.PackageRepository;
import com.thoughtworks.go.helper.GoConfigMother;
import com.thoughtworks.go.helper.PipelineConfigMother;
import com.thoughtworks.go.server.domain.Username;
import com.thoughtworks.go.server.service.GoConfigService;
import com.thoughtworks.go.server.service.result.HttpLocalizedOperationResult;
import com.thoughtworks.go.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.thoughtworks.go.i18n.LocalizedMessage.cannotDeleteResourceBecauseOfDependentPipelines;
import static com.thoughtworks.go.serverhealth.HealthStateType.forbidden;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeletePackageConfigCommandTest {
    private Username currentUser;
    private BasicCruiseConfig cruiseConfig;
    private PackageDefinition packageDefinition;
    private String packageUuid;
    private Configuration configuration;
    private HttpLocalizedOperationResult result;

    @Mock
    private GoConfigService goConfigService;

    @BeforeEach
    public void setup() {
        currentUser = new Username(new CaseInsensitiveString("user"));
        cruiseConfig = GoConfigMother.defaultCruiseConfig();
        packageUuid = "random-uuid";
        configuration = new Configuration(new ConfigurationProperty(new ConfigurationKey("PACKAGE_ID"), new ConfigurationValue("prettyjson")));
        packageDefinition = new PackageDefinition(packageUuid, "prettyjson", configuration);

        result = new HttpLocalizedOperationResult();
        PackageRepositories repositories = cruiseConfig.getPackageRepositories();
        PackageRepository repository = new PackageRepository();
        repository.addPackage(packageDefinition);
        repositories.add(repository);
        cruiseConfig.setPackageRepositories(repositories);
    }

    @Test
    public void shouldDeleteTheSpecifiedPackage() {
        DeletePackageConfigCommand command = new DeletePackageConfigCommand(goConfigService, packageDefinition, currentUser, result);
        assertThat(cruiseConfig.getPackageRepositories().first().getPackages().size()).isEqualTo(1);
        assertThat(cruiseConfig.getPackageRepositories().first().getPackages().find(packageUuid)).isEqualTo(packageDefinition);
        command.update(cruiseConfig);
        assertThat(cruiseConfig.getPackageRepositories().first().getPackages().size()).isEqualTo(0);
        assertNull(cruiseConfig.getPackageRepositories().first().getPackages().find(packageUuid));
    }

    @Test
    public void shouldNotDeletePackageIfItIsUsedAsAMaterialInPipeline() {
        MaterialConfigs materialConfigs = new MaterialConfigs(new PackageMaterialConfig(new CaseInsensitiveString("fooPackage"), packageUuid, packageDefinition));
        Map<String, List<Pair<PipelineConfig, PipelineConfigs>>> pipelinesUsingPackages = new HashMap<>();
        Pair<PipelineConfig, PipelineConfigs> pair = new Pair<>(PipelineConfigMother.pipelineConfig("some-pipeline", "stage", materialConfigs), null);
        List<Pair<PipelineConfig, PipelineConfigs>> pairs = new ArrayList<>();
        pairs.add(pair);
        pipelinesUsingPackages.put(packageUuid, pairs);
        List<String> pipelines = new ArrayList<>();
        pipelines.add("some-pipeline");
        when(goConfigService.getPackageUsageInPipelines()).thenReturn(pipelinesUsingPackages);

        DeletePackageConfigCommand command = new DeletePackageConfigCommand(goConfigService, packageDefinition, currentUser, result);
        command.update(cruiseConfig);

        assertFalse(command.isValid(cruiseConfig));
        HttpLocalizedOperationResult expectedResult = new HttpLocalizedOperationResult();
        expectedResult.unprocessableEntity(cannotDeleteResourceBecauseOfDependentPipelines("package definition", packageUuid, pipelines));
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void shouldNotContinueIfTheUserDontHavePermissionsToOperateOnPackages() {
        DeletePackageConfigCommand command = new DeletePackageConfigCommand(goConfigService, packageDefinition, currentUser, result);
        when(goConfigService.isUserAdmin(currentUser)).thenReturn(false);
        assertThat(command.canContinue(cruiseConfig)).isFalse();

        HttpLocalizedOperationResult expectedResult = new HttpLocalizedOperationResult();
        expectedResult.forbidden(EntityType.PackageDefinition.forbiddenToDelete(packageDefinition.getId(), currentUser.getUsername()), forbidden());
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void shouldContinueWithConfigSaveIfUserIsAdmin() {
        when(goConfigService.isUserAdmin(currentUser)).thenReturn(true);
        lenient().when(goConfigService.isGroupAdministrator(currentUser.getUsername())).thenReturn(false);

        DeletePackageConfigCommand command = new DeletePackageConfigCommand(goConfigService, packageDefinition, currentUser, result);

        assertThat(command.canContinue(cruiseConfig)).isTrue();
    }

    @Test
    public void shouldContinueWithConfigSaveIfUserIsGroupAdmin() {
        when(goConfigService.isUserAdmin(currentUser)).thenReturn(false);
        when(goConfigService.isGroupAdministrator(currentUser.getUsername())).thenReturn(true);

        DeletePackageConfigCommand command = new DeletePackageConfigCommand(goConfigService, packageDefinition, currentUser, result);

        assertThat(command.canContinue(cruiseConfig)).isTrue();
    }
}

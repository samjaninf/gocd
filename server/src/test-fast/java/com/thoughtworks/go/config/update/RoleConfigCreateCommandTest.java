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
import com.thoughtworks.go.config.PluginRoleConfig;
import com.thoughtworks.go.config.RoleConfig;
import com.thoughtworks.go.helper.GoConfigMother;
import com.thoughtworks.go.server.domain.Username;
import com.thoughtworks.go.server.service.GoConfigService;
import com.thoughtworks.go.server.service.result.HttpLocalizedOperationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RoleConfigCreateCommandTest {
    @Test
    public void currentUserShouldBeAnAdminToAddRole() {
        GoConfigService goConfigService = mock(GoConfigService.class);
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        Username viewUser = new Username("view");

        when(goConfigService.isUserAdmin(viewUser)).thenReturn(false);

        RoleConfigCreateCommand command = new RoleConfigCreateCommand(goConfigService, new RoleConfig("some-role"), viewUser, result);

        assertFalse(command.canContinue(null));
        assertFalse(result.isSuccessful());
        assertThat(result.httpCode()).isEqualTo(403);
    }

    @Test
    public void update_shouldAddPluginRoleConfigToRoles() {
        BasicCruiseConfig cruiseConfig = GoConfigMother.defaultCruiseConfig();
        PluginRoleConfig role = new PluginRoleConfig("blackbird", "ldap");
        RoleConfigCreateCommand command = new RoleConfigCreateCommand(null, role, null, null);

        command.update(cruiseConfig);

        assertThat(cruiseConfig.server().security().getRoles().findByName(new CaseInsensitiveString("blackbird"))).isEqualTo(role);
    }

    @Test
    public void isValid_shouldSkipUpdatedRoleValidation() {
        PluginRoleConfig pluginRoleConfig = new PluginRoleConfig(null, "ldap");
        RoleConfigCreateCommand command = new RoleConfigCreateCommand(null, pluginRoleConfig, null, null);

        assertFalse(command.isValid(GoConfigMother.defaultCruiseConfig()));
        assertThat(pluginRoleConfig.errors().size()).isEqualTo(2);
        assertThat(pluginRoleConfig.errors().get("name").get(0)).isEqualTo("Invalid role name name 'null'. This must be " +
                "alphanumeric and can contain underscores, hyphens and periods (however, it cannot start with a period). The maximum allowed length is 255 characters.");
        assertThat(pluginRoleConfig.errors().get("authConfigId").get(0)).isEqualTo("No such security auth configuration present for id: `ldap`");
    }
}

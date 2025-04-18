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
package com.thoughtworks.go.config.materials.perforce;

import com.thoughtworks.go.config.Validatable;
import com.thoughtworks.go.config.ValidationContext;
import com.thoughtworks.go.domain.ConfigErrors;

import java.io.Serializable;

import static com.thoughtworks.go.util.ExceptionUtils.bombIfNull;

public class P4MaterialView implements Serializable, Validatable {
    private String value;
    private static final String CLIENT_RENAME_REGEX = "//(.+?)\\s+(\"?)//(.+?)/(.+)";
    private static final String SPACE_BEFORE_DEPOT_REGEX = "\\s*(\"?[+\\-]?//.+?//)";
    private ConfigErrors configErrors = new ConfigErrors();

    public P4MaterialView() { }

    public P4MaterialView(String view) {
        bombIfNull(view, "null view");
        this.value = view;
    }

    public String viewUsing(String clientName) {
        String fromClientName = value.replaceAll(CLIENT_RENAME_REGEX, "//$1 $2//" + clientName + "/$4");
        return fromClientName.replaceAll(SPACE_BEFORE_DEPOT_REGEX, "\n\t$1");
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        P4MaterialView view = (P4MaterialView) o;

        if (value != null ? !value.equals(view.value) : view.value != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return (value != null ? value.hashCode() : 0);
    }

    @Override
    public void validate(ValidationContext validationContext) {
    }

    @Override
    public ConfigErrors errors() {
        return configErrors;
    }

    @Override
    public void addError(String fieldName, String message) {
        configErrors.add(fieldName, message);
    }
}

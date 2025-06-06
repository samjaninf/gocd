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
package com.thoughtworks.go.config;

import com.thoughtworks.go.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static com.thoughtworks.go.util.ExceptionUtils.bomb;

abstract class IdFileService {
    protected final File file;

    public IdFileService(File file) {
        this.file = file;
    }

    public void store(String data) {
        try {
            delete();
            FileUtil.mkdirsParentQuietly(file);
            Files.writeString(file.toPath(), data, StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw bomb(String.format("Couldn't save %s to filesystem", file.getName()), ioe);
        }
    }

    public String load() {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
        } catch (IOException ioe) {
            throw bomb(String.format("Couldn't load %s from filesystem", file.getName()), ioe);
        }
    }

    public void delete() {
        file.delete();
    }

    public boolean dataPresent() {
        try {
            String id = load();
            return id != null && !id.isBlank();
        } catch (Exception ioe) {
            return false;
        }
    }
}

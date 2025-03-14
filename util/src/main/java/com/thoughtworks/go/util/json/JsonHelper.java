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
package com.thoughtworks.go.util.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Type;
import java.util.Map;

public class JsonHelper {

    private static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    public static void addDeveloperErrorMessage(Map<String, Object> jsonMap, Exception e) {
        addFriendlyErrorMessage(jsonMap, e.getMessage() + ": " + e.getCause() + " at " + e.getStackTrace()[0]);
    }

    public static void addFriendlyErrorMessage(Map<String, Object> jsonMap, String e) {
        jsonMap.put("error", e);
    }

    public static String toJsonString(Object object) {
        return GSON.toJson(object);
    }

    public static <T> T fromJson(final String jsonString, final Class<T> clazz) {
        return GSON.fromJson(jsonString, clazz);
    }

    public static <T> T fromJson(final String jsonString, Type type) {
        return GSON.fromJson(jsonString, type);
    }

    public static <T> T safeFromJson(final String jsonString, final Class<T> clazz) {
        try {
            return fromJson(jsonString, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T safeFromJson(final String jsonString, Type type) {
        try {
            return fromJson(jsonString, type);
        } catch (Exception e) {
            return null;
        }
    }
}

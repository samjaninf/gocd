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
package com.thoughtworks.go.spark.spa;

import com.thoughtworks.go.spark.Routes;
import com.thoughtworks.go.spark.SparkController;
import com.thoughtworks.go.spark.spring.SPAAuthenticationHelper;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.TemplateEngine;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.*;

public class AgentJobRunHistoryController implements SparkController {
    private final SPAAuthenticationHelper authenticationHelper;
    private final TemplateEngine engine;

    public AgentJobRunHistoryController(SPAAuthenticationHelper authenticationHelper, TemplateEngine engine) {
        this.authenticationHelper = authenticationHelper;
        this.engine = engine;
    }

    @Override
    public String controllerBasePath() {
        return Routes.AgentsSPA.BASE;
    }

    @Override
    public void setupRoutes() {
        path(controllerBasePath(), () -> {
            before(String.format("%s/job_run_history", Routes.AgentsSPA.AGENT_UUID), authenticationHelper::checkAdminUserAnd403);
            get(String.format("%s/job_run_history", Routes.AgentsSPA.AGENT_UUID), this::index, engine);
        });
    }

    public ModelAndView index(Request request, Response response) {
        Map<String, Object> object = Map.of(
            "viewTitle", "Agent Job History",
            "meta", meta(request)
        );

        return new ModelAndView(object, null);
    }

    private Map<String, String> meta(Request request) {
        Map<String, String> meta = new HashMap<>();
        meta.put("uuid", request.params("uuid"));
        return meta;
    }
}

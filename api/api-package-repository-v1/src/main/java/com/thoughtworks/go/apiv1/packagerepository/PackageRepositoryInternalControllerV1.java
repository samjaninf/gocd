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
package com.thoughtworks.go.apiv1.packagerepository;

import com.thoughtworks.go.api.ApiController;
import com.thoughtworks.go.api.ApiVersion;
import com.thoughtworks.go.api.CrudController;
import com.thoughtworks.go.api.base.OutputWriter;
import com.thoughtworks.go.api.representers.JsonReader;
import com.thoughtworks.go.api.spring.ApiAuthenticationHelper;
import com.thoughtworks.go.api.util.GsonTransformer;
import com.thoughtworks.go.apiv1.packagerepository.representers.PackageRepositoryRepresenter;
import com.thoughtworks.go.apiv1.packagerepository.representers.VerifyConnectionRepresenter;
import com.thoughtworks.go.config.exceptions.EntityType;
import com.thoughtworks.go.domain.packagerepository.PackageRepository;
import com.thoughtworks.go.server.service.EntityHashingService;
import com.thoughtworks.go.server.service.materials.PackageRepositoryService;
import com.thoughtworks.go.server.service.result.HttpLocalizedOperationResult;
import com.thoughtworks.go.spark.Routes;
import com.thoughtworks.go.spark.spring.SparkSpringController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.function.Consumer;

import static spark.Spark.*;

@Component
public class PackageRepositoryInternalControllerV1 extends ApiController implements SparkSpringController, CrudController<PackageRepository> {

    private final ApiAuthenticationHelper apiAuthenticationHelper;
    private final EntityHashingService entityHashingService;
    private final PackageRepositoryService packageRepositoryService;

    @Autowired
    public PackageRepositoryInternalControllerV1(ApiAuthenticationHelper apiAuthenticationHelper, EntityHashingService entityHashingService, PackageRepositoryService packageRepositoryService) {
        super(ApiVersion.v1);
        this.apiAuthenticationHelper = apiAuthenticationHelper;
        this.entityHashingService = entityHashingService;
        this.packageRepositoryService = packageRepositoryService;
    }

    @Override
    public String controllerBasePath() {
        return Routes.PackageRepository.INTERNAL_BASE;
    }

    @Override
    public void setupRoutes() {
        path(controllerBasePath(), () -> {
            before(Routes.PackageRepository.VERIFY_CONNECTION, mimeType, this::setContentType);
            before(Routes.PackageRepository.VERIFY_CONNECTION, mimeType, this.apiAuthenticationHelper::checkAdminUserOrGroupAdminUserAnd403);

            post(Routes.PackageRepository.VERIFY_CONNECTION, mimeType, this::verifyConnection);
        });
    }

    public String verifyConnection(Request request, Response response) throws IOException {
        PackageRepository packageRepository = buildEntityFromRequestBody(request);
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        packageRepositoryService.checkConnection(packageRepository, result);
        response.status(result.httpCode());
        return writerForTopLevelObject(request, response, writer -> VerifyConnectionRepresenter.toJSON(writer, result, packageRepository));
    }

    @Override
    public String etagFor(PackageRepository entityFromServer) {
        return entityHashingService.hashForEntity(entityFromServer);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.PackageRepository;
    }

    @Override
    public PackageRepository doFetchEntityFromConfig(String name) {
        return packageRepositoryService.getPackageRepository(name);
    }

    @Override
    public PackageRepository buildEntityFromRequestBody(Request req) {
        JsonReader jsonReader = GsonTransformer.getInstance().jsonReaderFrom(req.body());
        return PackageRepositoryRepresenter.fromJSON(jsonReader);
    }

    @Override
    public Consumer<OutputWriter> jsonWriter(PackageRepository packageRepository) {
        return outputWriter -> PackageRepositoryRepresenter.toJSON(outputWriter, packageRepository);
    }
}

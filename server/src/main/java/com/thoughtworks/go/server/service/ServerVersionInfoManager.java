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
package com.thoughtworks.go.server.service;

import com.thoughtworks.go.domain.GoVersion;
import com.thoughtworks.go.domain.VersionInfo;
import com.thoughtworks.go.server.cache.GoCache;
import com.thoughtworks.go.server.dao.VersionInfoDao;
import com.thoughtworks.go.util.Clock;
import com.thoughtworks.go.util.SystemEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static com.thoughtworks.go.util.Dates.isToday;

@Component
public class ServerVersionInfoManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerVersionInfoManager.class.getName());
    private static final String GO_UPDATE = "GOUpdate";
    private static final Object VERSION_INFO_MUTEX = new Object();

    private final ServerVersionInfoBuilder builder;
    private final VersionInfoDao versionInfoDao;
    private final Clock clock;
    private final GoCache goCache;
    private final SystemEnvironment systemEnvironment;

    private VersionInfo serverVersionInfo;
    private Instant versionInfoUpdatingFrom;

    @Autowired
    public ServerVersionInfoManager(ServerVersionInfoBuilder builder, VersionInfoDao versionInfoDao, Clock clock, GoCache goCache, SystemEnvironment systemEnvironment) {
        this.builder = builder;
        this.versionInfoDao = versionInfoDao;
        this.clock = clock;
        this.goCache = goCache;
        this.systemEnvironment = systemEnvironment;
    }

    public void initialize() {
        this.serverVersionInfo = builder.getServerVersionInfo();

        if (!systemEnvironment.isGOUpdateCheckEnabled()) {
            LOGGER.info("[Go Update Check] Update check disabled.");
        }

        addGoUpdateToCacheIfAvailable();
    }

    public VersionInfo versionInfoForUpdate() {
        synchronized (VERSION_INFO_MUTEX) {
            if (isDevelopmentServer() || isVersionInfoUpdatedToday() || isUpdateInProgress()) return null;

            versionInfoUpdatingFrom = clock.currentTime();
            LOGGER.info("[Go Update Check] Starting update check at: {}", new Date());

            return this.serverVersionInfo;
        }
    }

    public VersionInfo updateLatestVersion(String latestVersion) {
        synchronized (VERSION_INFO_MUTEX) {
            serverVersionInfo.setLatestVersion(new GoVersion(latestVersion));
            serverVersionInfo.setLatestVersionUpdatedAt(clock.currentUtilDate());
            versionInfoDao.saveOrUpdate(serverVersionInfo);

            versionInfoUpdatingFrom = null;
            addGoUpdateToCacheIfAvailable();

            LOGGER.info("[Go Update Check] Update check done at: {}, latest available version: {}", new Date(), latestVersion);
            return serverVersionInfo;
        }
    }

    public String getGoUpdate() {
        return goCache.get(GO_UPDATE);
    }

    public boolean isUpdateCheckEnabled() {
        return !isDevelopmentServer() && systemEnvironment.isGOUpdateCheckEnabled();
    }

    private boolean isDevelopmentServer() {
        return !systemEnvironment.isProductionMode() || (serverVersionInfo == null);
    }

    private boolean isVersionInfoUpdatedToday() {
        Date latestVersionUpdatedAt = serverVersionInfo.getLatestVersionUpdatedAt();

        if (latestVersionUpdatedAt == null) return false;

        return isToday(latestVersionUpdatedAt);
    }

    private boolean isUpdateInProgress() {
        if (versionInfoUpdatingFrom == null) return false;

        Instant halfHourAgo = clock.currentTime().minus(30, ChronoUnit.MINUTES);
        return versionInfoUpdatingFrom.isAfter(halfHourAgo);
    }

    private void addGoUpdateToCacheIfAvailable() {
        if (this.serverVersionInfo == null) return;

        GoVersion latestVersion = serverVersionInfo.getLatestVersion();
        if (latestVersion == null) return;

        if (latestVersion.isGreaterThan(serverVersionInfo.getInstalledVersion())) {
            goCache.put(GO_UPDATE, latestVersion.toString());
        }
    }
}

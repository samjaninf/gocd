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
package com.thoughtworks.go.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

public class RandomPort {

    public static final Logger LOG = LoggerFactory.getLogger(RandomPort.class);

    public int currentPort = 10000;

    private static RandomPort instance;

    public static int find(String name) {
        return instance().currentPort(name);
    }

    private int currentPort(String name) {
        while (!isAvailable(currentPort)) {
            currentPort++;
        }
        int allocated = currentPort;
        currentPort++;
        LOG.info("RandomPort: Allocating port {} for '{}'", allocated, name);
        return allocated;
    }

    @SuppressWarnings("try")
    private boolean isAvailable(int port) {
        try (Socket ignored = new Socket("127.0.0.1", port)) {
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static RandomPort instance() {
        if (instance == null) {
            instance = new RandomPort();
        }
        return instance;
    }

    public static void waitForPort(int port) {
        waitForPort(port, 10000L);
    }

    public static void waitForPort(int port, long timeout) {
        instance().waitForPortToBeUsed(port, timeout);
    }

    public void waitForPortToBeUsed(int port, long timeout) {
        long start = System.currentTimeMillis();
        while (isAvailable(port)) {
            if ((System.currentTimeMillis() - start) > timeout) {
                throw new RuntimeException("Timed out waiting for port " + port);
            }
            try {
                //noinspection BusyWait
                Thread.sleep(100L);
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

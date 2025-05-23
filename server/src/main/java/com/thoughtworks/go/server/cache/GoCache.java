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
package com.thoughtworks.go.server.cache;

import com.thoughtworks.go.domain.NullUser;
import com.thoughtworks.go.domain.PersistentObject;
import com.thoughtworks.go.server.transaction.TransactionSynchronizationManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.event.CacheEventListener;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;

import javax.annotation.PreDestroy;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.thoughtworks.go.util.ExceptionUtils.bomb;

/**
 * Understands storing and retrieving objects from an underlying LRU cache
 */
public class GoCache {
    public static final String SUB_KEY_DELIMITER = "!_#$#_!";
    private static final Logger LOGGER = LoggerFactory.getLogger(GoCache.class);
    private final ThreadLocal<Boolean> doNotServeForTransaction = new ThreadLocal<>();

    private final Ehcache ehCache;
    private final TransactionSynchronizationManager transactionSynchronizationManager;

    private final Set<Class<? extends PersistentObject>> nullObjectClasses;

    static class KeyList extends HashSet<String> {
    }

    @TestOnly
    public GoCache(GoCache goCache) {
        this(goCache.ehCache, goCache.transactionSynchronizationManager);
    }

    public GoCache(Ehcache cache, TransactionSynchronizationManager transactionSynchronizationManager) {
        this.ehCache = cache;
        this.transactionSynchronizationManager = transactionSynchronizationManager;
        this.nullObjectClasses = new HashSet<>();
        nullObjectClasses.add(NullUser.class);
        registerAsCacheEvictionListener();
    }

    @PreDestroy
    public void destroy() {
        clear();
        Optional.ofNullable(ehCache.getCacheManager())
            .ifPresent(cm -> cm.removeCache(ehCache.getName()));
    }

    public void addListener(CacheEventListener listener) {
        ehCache.getCacheEventNotificationService().registerListener(listener);
    }

    protected void registerAsCacheEvictionListener() {
        ehCache.getCacheEventNotificationService().registerListener(new CacheEvictionListener(this));
    }

    public void stopServingForTransaction() {
        if (transactionSynchronizationManager.isTransactionBodyExecuting() && !doNotServeForTransaction()) {
            doNotServeForTransaction.set(true);
            transactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void beforeCompletion() {
                    doNotServeForTransaction.set(false);
                }
            });
        }
    }

    public void put(String key, Object value) {
        logUnsavedPersistentObjectInteraction(value, "PersistentObject {} added to cache without an id.");
        if (transactionSynchronizationManager.isActualTransactionActive()) {
            LOGGER.debug("transaction active during cache put for {} = {}", key, value, new IllegalStateException());
            return;
        }
        ehCache.put(new Element(key, value));
    }

    @SuppressWarnings("unchecked")
    public List<String> getKeys() {
        return ehCache.getKeys();
    }

    private void logUnsavedPersistentObjectInteraction(Object value, String message) {
        if (value instanceof PersistentObject persistentObject) {
            for (Class<? extends PersistentObject> nullObjectClass : nullObjectClasses) {
                if (value.getClass().equals(nullObjectClass)) {
                    return;
                }
            }
            if (!persistentObject.hasId()) {
                String msg = String.format(message, persistentObject);
                IllegalStateException exception = new IllegalStateException();
                LOGGER.error(msg, exception);
                throw bomb(msg, exception);
            }
        }
    }

    public <T> T get(String key) {
        if (doNotServeForTransaction()) {
            return null;
        }
        return getWithoutTransactionCheck(key);
    }

    private <T> T getWithoutTransactionCheck(String key) {
        Element element = ehCache.get(key);
        if (element == null) {
            return null;
        }
        @SuppressWarnings("unchecked") T value = (T) element.getObjectValue();
        logUnsavedPersistentObjectInteraction(value, "PersistentObject {} without an id served out of cache.");
        return value;
    }

    private boolean doNotServeForTransaction() {
        return doNotServeForTransaction.get() != null && doNotServeForTransaction.get();
    }

    public void clear() {
        ehCache.removeAll();
    }

    public boolean remove(String key) {
        synchronized (key.intern()) {
            Object value = getWithoutTransactionCheck(key);
            if (value instanceof KeyList) {
                for (String subKey : (KeyList) value) {
                    ehCache.remove(compositeKey(key, subKey));
                }
            }
            return ehCache.remove(key);
        }
    }

    public Object get(String key, String subKey) {
        return get(compositeKey(key, subKey));
    }

    public void put(String key, String subKey, Object value) {
        KeyList subKeys;
        synchronized (key.intern()) {
            subKeys = subKeyFamily(key);
            if (subKeys == null) {
                subKeys = new KeyList();
                put(key, subKeys);
            }
            subKeys.add(subKey);
        }
        put(compositeKey(key, subKey), value);
    }

    public void removeAll(List<String> keys) {
        for (String key : keys) {
            remove(key);
        }
    }

    public void removeAssociations(String key, Element element) {
        if (element.getObjectValue() instanceof KeyList) {
            synchronized (key.intern()) {
                for (String subkey : (KeyList) element.getObjectValue()) {
                    remove(compositeKey(key, subkey));
                }
            }
        } else if (key.contains(SUB_KEY_DELIMITER)) {
            String[] parts = StringUtils.splitByWholeSeparator(key, SUB_KEY_DELIMITER);
            String parentKey = parts[0];
            String childKey = parts[1];
            synchronized (parentKey.intern()) {
                Element parent = ehCache.get(parentKey);
                if (parent == null) {
                    return;
                }
                KeyList subKeys = (KeyList) parent.getObjectValue();
                subKeys.remove(childKey);
            }
        }
    }

    public boolean isKeyInCache(Object key) {
        return ehCache.isKeyInCache(key);
    }

    private KeyList subKeyFamily(String parentKey) {
        return get(parentKey);
    }

    private String compositeKey(String key, String subKey) {
        String concat = key + subKey;
        if (concat.contains(SUB_KEY_DELIMITER)) {
            bomb(String.format("Base and sub key concatenation(key = %s, subkey = %s) must not have pattern %s", key, subKey, SUB_KEY_DELIMITER));
        }
        return key + SUB_KEY_DELIMITER + subKey;
    }

    public void remove(String key, String subKey) {
        synchronized (key.intern()) {
            KeyList subKeys = subKeyFamily(key);
            if (subKeys == null) {
                return;
            }
            subKeys.remove(subKey);
            remove(compositeKey(key, subKey));
        }
    }

    public CacheConfiguration configuration() {
        return ehCache.getCacheConfiguration();
    }
}

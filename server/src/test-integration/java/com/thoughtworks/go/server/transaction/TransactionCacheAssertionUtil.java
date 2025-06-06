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
package com.thoughtworks.go.server.transaction;

import com.thoughtworks.go.server.cache.GoCache;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionCacheAssertionUtil {
    private GoCache goCache;
    private TransactionTemplate transactionTemplate;

    public TransactionCacheAssertionUtil(GoCache goCache, TransactionTemplate transactionTemplate) {
        this.goCache = goCache;
        this.transactionTemplate = transactionTemplate;
    }

    public String doInTxnWithCachePut(final DoInTxn inTxn) {
        goCache.put("loser", "boozer");

        final String[] cachedValueBeforeAndAfter = new String[2];
        transactionTemplate.execute(new org.springframework.transaction.support.TransactionCallbackWithoutResult() {
            @Override protected void doInTransactionWithoutResult(TransactionStatus status) {
                cachedValueBeforeAndAfter[0] = goCache.get("loser");
                inTxn.invoke();
                cachedValueBeforeAndAfter[1] = goCache.get("loser");
            }
        });

        assertThat(goCache.<Object>get("loser")).isEqualTo("boozer");
        assertThat(cachedValueBeforeAndAfter[0]).isEqualTo("boozer");
        return cachedValueBeforeAndAfter[1];
    }

    public void assertCacheBehaviourInTxn(final DoInTxn inTxn) {
        assertThat(doInTxnWithCachePut(inTxn)).isNull();
    }

    public interface DoInTxn {
        void invoke();
    }
}

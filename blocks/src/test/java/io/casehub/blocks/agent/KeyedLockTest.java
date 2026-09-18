/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.blocks.agent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyedLockTest {

    private final KeyedLock lock = new KeyedLock();

    @Test
    void withLockSupplierReturnsValue() {
        var result = lock.withLock("key", () -> 42);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void withLockRunnableExecutes() {
        var counter = new AtomicInteger();
        lock.withLock("key", counter::incrementAndGet);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void differentKeysDoNotBlock() throws Exception {
        var barrier = new CyclicBarrier(2);
        var completed = new AtomicInteger();

        var t1 = Thread.ofVirtual().start(() -> lock.withLock("a", () -> {
            try { barrier.await(); } catch (Exception e) { throw new RuntimeException(e); }
            completed.incrementAndGet();
        }));

        var t2 = Thread.ofVirtual().start(() -> lock.withLock("b", () -> {
            try { barrier.await(); } catch (Exception e) { throw new RuntimeException(e); }
            completed.incrementAndGet();
        }));

        t1.join(5000);
        t2.join(5000);
        assertThat(completed.get()).isEqualTo(2);
    }

    @Test
    void sameKeySerialises() throws Exception {
        var concurrency = new AtomicInteger();
        var maxConcurrency = new AtomicInteger();
        var latch = new CountDownLatch(2);

        Runnable task = () -> lock.withLock("same", () -> {
            int current = concurrency.incrementAndGet();
            maxConcurrency.accumulateAndGet(current, Math::max);
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            concurrency.decrementAndGet();
            latch.countDown();
        });

        Thread.ofVirtual().start(task);
        Thread.ofVirtual().start(task);

        latch.await();
        assertThat(maxConcurrency.get()).isEqualTo(1);
    }

    @Test
    void lockReleasedOnException() {
        assertThatThrownBy(() -> lock.withLock("key", () -> {
            throw new RuntimeException("boom");
        })).isInstanceOf(RuntimeException.class).hasMessage("boom");

        var result = lock.withLock("key", () -> "recovered");
        assertThat(result).isEqualTo("recovered");
    }

    @Test
    void supplierVersionReleasesOnException() {
        assertThatThrownBy(() -> lock.withLock("key", () -> {
            if (true) throw new RuntimeException("fail");
            return "never";
        })).isInstanceOf(RuntimeException.class);

        var result = lock.withLock("key", () -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void stateKeyHelper() {
        assertThat(KeyedLock.stateKey("agent-1", "tenant-x")).isEqualTo("agent-1:tenant-x");
    }
}

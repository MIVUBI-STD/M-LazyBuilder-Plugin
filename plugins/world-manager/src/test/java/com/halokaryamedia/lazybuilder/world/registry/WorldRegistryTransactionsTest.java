package com.halokaryamedia.lazybuilder.world.registry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRegistryTransactionsTest {
    @Test
    void failedRegisterRestoresInMemoryRegistry() {
        WorldRegistry registry = new WorldRegistry();
        FailingPersistence persistence = new FailingPersistence();

        WorldRecord world = world("Build");
        assertThrows(IOException.class,
                () -> WorldRegistryTransactions.register(registry, persistence, world));

        assertTrue(registry.find(world.id()).isEmpty());
    }

    @Test
    void failedMetadataUpdateRestoresPreviousRecord() throws Exception {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord original = world("Build");
        registry.register(original);
        FailingPersistence persistence = new FailingPersistence();

        assertThrows(IOException.class, () -> WorldRegistryTransactions.updateMetadata(
                registry,
                persistence,
                original.withLifecycle(WorldLifecycle.ARCHIVED)));

        assertEquals(original, registry.find(original.id()).orElseThrow());
    }

    @Test
    void failedRemovalRestoresRemovedRecord() throws Exception {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord original = world("Build");
        registry.register(original);
        FailingPersistence persistence = new FailingPersistence();

        assertThrows(IOException.class,
                () -> WorldRegistryTransactions.remove(registry, persistence, original.id()));

        assertEquals(original, registry.find(original.id()).orElseThrow());
    }

    @Test
    void concurrentTransactionsCannotPublishStaleSnapshotsOutOfOrder() throws Exception {
        WorldRegistry registry = new WorldRegistry();
        BlockingPersistence persistence = new BlockingPersistence();
        WorldRecord first = world("First");
        WorldRecord second = world("Second");

        Thread firstThread = Thread.ofPlatform().start(() -> {
            try {
                WorldRegistryTransactions.register(registry, persistence, first);
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
        });

        assertTrue(persistence.firstSaveEntered.await(1, TimeUnit.SECONDS));

        Thread secondThread = Thread.ofPlatform().start(() -> {
            try {
                WorldRegistryTransactions.register(registry, persistence, second);
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
        });

        Thread.sleep(25);
        assertEquals(1, persistence.activeSaves.get(),
                "a second registry transaction must not overlap the first durable publication");

        persistence.releaseFirstSave.countDown();
        firstThread.join(1_000);
        secondThread.join(1_000);

        assertFalse(firstThread.isAlive());
        assertFalse(secondThread.isAlive());
        assertEquals(1, persistence.maxConcurrentSaves.get());
        assertEquals(List.of(first, second), persistence.saved);
    }

    private static WorldRecord world(String name) {
        return new WorldRecord(
                WorldId.create(),
                name,
                name,
                WorldKind.FLAT,
                WorldLifecycle.ACTIVE
        );
    }

    private static final class FailingPersistence implements WorldRegistryPersistence {
        @Override public List<WorldRecord> load() { return List.of(); }
        @Override public void save(List<WorldRecord> worlds) throws IOException {
            throw new IOException("test failure");
        }
    }

    private static final class BlockingPersistence implements WorldRegistryPersistence {
        private final CountDownLatch firstSaveEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirstSave = new CountDownLatch(1);
        private final AtomicInteger activeSaves = new AtomicInteger();
        private final AtomicInteger maxConcurrentSaves = new AtomicInteger();
        private volatile List<WorldRecord> saved = List.of();
        private boolean first = true;

        @Override public List<WorldRecord> load() { return saved; }

        @Override
        public void save(List<WorldRecord> worlds) throws IOException {
            int active = activeSaves.incrementAndGet();
            maxConcurrentSaves.accumulateAndGet(active, Math::max);
            try {
                if (first) {
                    first = false;
                    firstSaveEntered.countDown();
                    try {
                        if (!releaseFirstSave.await(1, TimeUnit.SECONDS)) {
                            throw new IOException("timed out waiting to release first save");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted", exception);
                    }
                }
                saved = List.copyOf(worlds);
            } finally {
                activeSaves.decrementAndGet();
            }
        }
    }
}

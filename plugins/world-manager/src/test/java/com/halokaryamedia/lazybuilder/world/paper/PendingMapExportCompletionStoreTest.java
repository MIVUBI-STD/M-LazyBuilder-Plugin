package com.halokaryamedia.lazybuilder.world.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingMapExportCompletionStoreTest {
    @TempDir Path tempDir;

    @Test
    void pendingCompletionSurvivesStoreRecreationAndIsConsumedOnce() throws Exception {
        Path root = tempDir.resolve("pending-map-completions");
        UUID owner = UUID.randomUUID();
        byte[] payload = {1, 2, 3, 4};

        new PendingMapExportCompletionStore(root).put(owner, payload);

        PendingMapExportCompletionStore restarted = new PendingMapExportCompletionStore(root);
        assertArrayEquals(payload, restarted.take(owner));
        assertNull(restarted.take(owner));
    }

    @Test
    void latestPendingCompletionReplacesOlderCompletionForSameOwner() throws Exception {
        Path root = tempDir.resolve("pending-map-completions");
        UUID owner = UUID.randomUUID();
        PendingMapExportCompletionStore store = new PendingMapExportCompletionStore(root);

        store.put(owner, new byte[]{1});
        store.put(owner, new byte[]{9, 8});

        assertArrayEquals(new byte[]{9, 8}, store.take(owner));
    }

    @Test
    void startupRecoveryRemovesOnlyInterruptedTempPayloads() throws Exception {
        Path root = tempDir.resolve("pending-map-completions");
        Files.createDirectories(root);
        UUID committedOwner = UUID.randomUUID();
        UUID interruptedOwner = UUID.randomUUID();
        Path committed = root.resolve(committedOwner + ".bin");
        Path interrupted = root.resolve(interruptedOwner + ".tmp");
        Files.write(committed, new byte[]{7});
        Files.write(interrupted, new byte[]{3});

        PendingMapExportCompletionStore store = new PendingMapExportCompletionStore(root);
        assertEquals(1, store.recoverTemps());

        assertFalse(Files.exists(interrupted));
        assertTrue(Files.exists(committed));
        assertArrayEquals(new byte[]{7}, store.take(committedOwner));
    }

    @Test
    void capacityEvictsOldestCompletionWithoutAgeExpiry() throws Exception {
        Path root = tempDir.resolve("pending-map-completions");
        PendingMapExportCompletionStore store = new PendingMapExportCompletionStore(root, 2);
        UUID oldest = UUID.randomUUID();
        UUID middle = UUID.randomUUID();
        UUID newest = UUID.randomUUID();

        store.put(oldest, new byte[]{1});
        Files.setLastModifiedTime(root.resolve(oldest + ".bin"), FileTime.fromMillis(1_000L));
        store.put(middle, new byte[]{2});
        Files.setLastModifiedTime(root.resolve(middle + ".bin"), FileTime.fromMillis(2_000L));
        store.put(newest, new byte[]{3});

        assertNull(store.take(oldest));
        assertArrayEquals(new byte[]{2}, store.take(middle));
        assertArrayEquals(new byte[]{3}, store.take(newest));
    }

    @Test
    void configurablePayloadLimitSupportsLargerTransportCompletions() throws Exception {
        Path root = tempDir.resolve("pending-world-completions");
        UUID owner = UUID.randomUUID();
        byte[] payload = new byte[8 * 1024];
        payload[0] = 7;
        payload[payload.length - 1] = 9;

        PendingMapExportCompletionStore larger = new PendingMapExportCompletionStore(root, 64 * 1024, 8);
        larger.put(owner, payload);
        assertArrayEquals(payload, larger.take(owner));

        PendingMapExportCompletionStore mapSized = new PendingMapExportCompletionStore(root, 4 * 1024, 8);
        assertThrows(java.io.IOException.class, () -> mapSized.put(owner, payload));
    }
}

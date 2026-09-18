package com.halokaryamedia.lazybuilder.builder.history;

import com.halokaryamedia.lazybuilder.builder.mutation.ReconciliationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryRecoveryManagerTest {
    @TempDir Path tempDir;

    @Test
    void discoversAndClassifiesCommittedBlockHistory() throws Exception {
        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        StoredChangeSet initiallyCommitted;
        try (ChangeSetWriter writer = storage.begin("recover-me")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0}, new int[]{1}
            ));
            initiallyCommitted = writer.commit();
        }
        // Simulate process loss: leave the durable file in place without closing/deleting it.
        assertEquals(1, storage.listCommitted().size());

        HistoryRecoveryManager recovery = new HistoryRecoveryManager(storage);
        List<RecoveredHistoryEntry> entries =
                recovery.discover((x, y, z) -> "minecraft:air");

        assertEquals(1, entries.size());
        RecoveredHistoryEntry entry = entries.get(0);
        assertTrue(entry.blockOnly());
        assertEquals(ReconciliationState.FULLY_APPLIED, entry.reconciliation().state());

        try (HistoryTimeline timeline = new HistoryTimeline(4)) {
            entry.transferFullyAppliedTo(timeline);
            assertEquals(1, timeline.undoSize());
        }
        assertTrue(storage.listCommitted().isEmpty());
        // Keep compiler aware ownership was intentionally not closed before recovery.
        assertNotNull(initiallyCommitted);
    }

    @Test
    void partialHistoryCanTransferIntoResumeMutation() throws Exception {
        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        StoredChangeSet initiallyCommitted;
        try (ChangeSetWriter writer = storage.begin("partial")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:air"),
                    new long[]{
                            LocalBlockPosition.pack(0, 64, 0),
                            LocalBlockPosition.pack(1, 64, 0)
                    },
                    new int[]{0, 0}, new int[]{1, 1}
            ));
            initiallyCommitted = writer.commit();
        }

        HistoryRecoveryManager recovery = new HistoryRecoveryManager(storage);
        RecoveredHistoryEntry entry = recovery.discover(
                (x, y, z) -> x == 0 ? "minecraft:air" : "minecraft:stone").get(0);
        assertEquals(ReconciliationState.PARTIALLY_APPLIED, entry.reconciliation().state());
        var resumed = entry.transferForResume();
        assertEquals(2, resumed.plannedChanges());
        resumed.close();
        assertTrue(storage.listCommitted().isEmpty());
        assertNotNull(initiallyCommitted);
    }
    @Test
    void recoveredGuardDoesNotInflatePlannedChangesAndCanBeReclaimed() throws Exception {
        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        StoredChangeSet initiallyCommitted;
        try (ChangeSetWriter writer = storage.begin("guard-only")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:chest"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0}, new int[]{0}
            ));
            initiallyCommitted = writer.commit();
        }

        HistoryRecoveryManager recovery = new HistoryRecoveryManager(storage);
        RecoveredHistoryEntry entry =
                recovery.discover((x, y, z) -> "minecraft:chest").get(0);

        assertEquals(ReconciliationState.EMPTY, entry.reconciliation().state());

        var firstAttempt = entry.transferForResume();
        assertEquals(0, firstAttempt.plannedChanges());

        entry.reclaimAfterFailedStart();

        var retry = entry.transferForResume();
        assertEquals(0, retry.plannedChanges());
        retry.close();

        assertTrue(storage.listCommitted().isEmpty());
        assertNotNull(initiallyCommitted);
    }

}

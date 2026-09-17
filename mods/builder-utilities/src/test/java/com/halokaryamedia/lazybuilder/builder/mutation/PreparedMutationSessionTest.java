package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.*;
import com.halokaryamedia.lazybuilder.builder.material.PreparedMaterialMutation;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PreparedMutationSessionTest {
    @Test
    void recordsHistoryOnlyAfterFullWorldReconciliation() throws Exception {
        StoredChangeSet stored = prepared(1, "session-op");
        try (HistoryTimeline timeline = new HistoryTimeline(8);
             PreparedMutationSession session = new PreparedMutationSession(new PreparedMaterialMutation(stored, 1), timeline)) {
            session.startDispatch();
            assertEquals(ReconciliationState.NOT_APPLIED,
                    session.reconcile((x, y, z) -> "minecraft:stone").state());
            assertEquals(0, timeline.undoSize());
            assertEquals(ReconciliationState.FULLY_APPLIED,
                    session.reconcile((x, y, z) -> "minecraft:dirt").state());
            assertEquals(OperationState.COMPLETED, session.lifecycle().state());
            assertEquals(1, session.lifecycle().processedWork());
            assertEquals(1.0, session.lifecycle().progressFraction());
            assertEquals(1, timeline.undoSize());
        }
    }

    @Test
    void keepChangesCancellationPublishesOnlyCompactedSubset() throws Exception {
        StoredChangeSet original = prepared(2, "original");
        StoredChangeSet subset = prepared(1, "original-partial");
        try (HistoryTimeline timeline = new HistoryTimeline(8);
             PreparedMutationSession session = new PreparedMutationSession(new PreparedMaterialMutation(original, 2), timeline)) {
            session.startDispatch();
            session.noteCancellationRequested();
            session.finalizeKeepChanges(AppliedMutationCompaction.compacted(subset));
            assertEquals(OperationState.CANCELLED, session.lifecycle().state());
            assertEquals(1, session.lifecycle().processedWork());
            assertEquals(2, session.lifecycle().totalWork());
            assertEquals(1, timeline.undoSize());
            assertEquals("original-partial", timeline.nextUndoOperationId().orElseThrow());
        }
    }

    @Test
    void emptyCancellationPublishesNoUndoEntry() throws Exception {
        StoredChangeSet original = prepared(1, "empty-original");
        try (HistoryTimeline timeline = new HistoryTimeline(8);
             PreparedMutationSession session = new PreparedMutationSession(new PreparedMaterialMutation(original, 1), timeline)) {
            session.startDispatch();
            session.noteCancellationRequested();
            session.finalizeKeepChanges(AppliedMutationCompaction.empty());
            assertEquals(OperationState.CANCELLED, session.lifecycle().state());
            assertEquals(0, timeline.undoSize());
        }
    }

    @Test
    void conflictFailsWithoutPublishingUndoEntry() throws Exception {
        StoredChangeSet stored = prepared(1, "conflict");
        try (HistoryTimeline timeline = new HistoryTimeline(8);
             PreparedMutationSession session = new PreparedMutationSession(new PreparedMaterialMutation(stored, 1), timeline)) {
            session.startDispatch();
            assertEquals(ReconciliationState.CONFLICT,
                    session.reconcile((x, y, z) -> "minecraft:gold_block").state());
            assertEquals(OperationState.FAILED, session.lifecycle().state());
            assertEquals(0, timeline.undoSize());
        }
    }

    private static StoredChangeSet prepared(int changes, String id) throws Exception {
        ChangeSetWriter writer = new MemoryChangeSetStorage().begin(id);
        long[] positions = new long[changes];
        int[] before = new int[changes];
        int[] after = new int[changes];
        for (int i = 0; i < changes; i++) {
            positions[i] = LocalBlockPosition.pack(i, 64, 0);
            after[i] = 1;
        }
        writer.append(new ChunkChangeSet(0, 0, List.of("minecraft:stone", "minecraft:dirt"), positions, before, after));
        return writer.commit();
    }
}

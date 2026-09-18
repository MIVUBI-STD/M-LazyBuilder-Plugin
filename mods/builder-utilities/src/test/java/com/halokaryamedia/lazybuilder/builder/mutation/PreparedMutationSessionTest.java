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
            assertEquals(ReconciliationState.FULLY_APPLIED,
                    session.reconcile((x, y, z) -> "minecraft:dirt").state());
            assertEquals(OperationState.COMPLETED, session.lifecycle().state());
            assertEquals(1, timeline.undoSize());
        }
    }

    @Test
    void rollbackCompletionCancelsWithoutPublishingUndo() throws Exception {
        StoredChangeSet original = prepared(2, "rollback-original");
        try (HistoryTimeline timeline = new HistoryTimeline(8);
             PreparedMutationSession session = new PreparedMutationSession(new PreparedMaterialMutation(original, 2), timeline)) {
            session.startDispatch();
            session.noteCancellationRequested();
            session.completeRollbackCancellation();
            assertEquals(OperationState.CANCELLED, session.lifecycle().state());
            assertEquals(0, session.lifecycle().processedWork());
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

    @Test
    void axiomStyleBlockOnlySessionCanCompleteWithoutInternalTimelineEntry() throws Exception {
        StoredChangeSet stored = prepared(1, "axiom-owned");
        try (HistoryTimeline timeline = new HistoryTimeline(8);
             PreparedMutationSession session = new PreparedMutationSession(
                     new PreparedMaterialMutation(stored, 1),
                     timeline,
                     false)) {
            session.startDispatch();
            assertEquals(ReconciliationState.FULLY_APPLIED,
                    session.reconcile((x, y, z) -> "minecraft:dirt").state());
            assertEquals(OperationState.COMPLETED, session.lifecycle().state());
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

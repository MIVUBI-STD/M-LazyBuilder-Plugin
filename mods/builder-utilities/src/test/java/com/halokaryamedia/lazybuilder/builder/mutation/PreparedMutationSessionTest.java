package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.HistoryTimeline;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.material.PreparedMaterialMutation;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparedMutationSessionTest {
    @Test
    void recordsHistoryOnlyAfterFullWorldReconciliation() throws Exception {
        StoredChangeSet stored = prepared();
        try (HistoryTimeline timeline = new HistoryTimeline(8);
             PreparedMutationSession session = new PreparedMutationSession(
                     new PreparedMaterialMutation(stored, 1), timeline)) {
            session.startDispatch();

            PreparedReconciliationReport pending = session.reconcile((x, y, z) -> "minecraft:stone");
            assertEquals(ReconciliationState.NOT_APPLIED, pending.state());
            assertEquals(OperationState.RUNNING, session.lifecycle().state());
            assertEquals(0, timeline.undoSize());

            PreparedReconciliationReport complete = session.reconcile((x, y, z) -> "minecraft:dirt");
            assertEquals(ReconciliationState.FULLY_APPLIED, complete.state());
            assertEquals(OperationState.COMPLETED, session.lifecycle().state());
            assertEquals(1, timeline.undoSize());
            assertTrue(timeline.nextUndoOperationId().isPresent());
        }
    }

    @Test
    void conflictFailsWithoutPublishingUndoEntry() throws Exception {
        StoredChangeSet stored = prepared();
        try (HistoryTimeline timeline = new HistoryTimeline(8);
             PreparedMutationSession session = new PreparedMutationSession(
                     new PreparedMaterialMutation(stored, 1), timeline)) {
            session.startDispatch();
            PreparedReconciliationReport report = session.reconcile((x, y, z) -> "minecraft:gold_block");
            assertEquals(ReconciliationState.CONFLICT, report.state());
            assertEquals(OperationState.FAILED, session.lifecycle().state());
            assertEquals(0, timeline.undoSize());
        }
    }

    private static StoredChangeSet prepared() throws Exception {
        ChangeSetWriter writer = new MemoryChangeSetStorage().begin("session-op");
        writer.append(new ChunkChangeSet(
                0, 0,
                List.of("minecraft:stone", "minecraft:dirt"),
                new long[]{LocalBlockPosition.pack(0, 64, 0)},
                new int[]{0},
                new int[]{1}
        ));
        return writer.commit();
    }
}

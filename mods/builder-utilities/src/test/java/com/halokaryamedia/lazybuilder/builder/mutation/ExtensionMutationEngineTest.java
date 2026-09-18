package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionMutationEngineTest {
    @Test
    void executesAndReconcilesOpaqueExtensionFrames() throws Exception {
        StoredChangeSet stored = stored();
        FakeTarget target = new FakeTarget();
        target.payload = new byte[]{1};

        HistoryExtensionTargetRegistry registry =
                new HistoryExtensionTargetRegistry(Map.of("lazybuilder:block_entity", target));

        ExtensionMutationExecution execution = PreparedExtensionMutationExecutor.execute(
                stored, registry, new CancellationSource().token());

        assertEquals(MutationExecutionState.COMPLETED, execution.state());
        assertArrayEquals(new byte[]{2}, target.payload);

        ExtensionReconciliationReport report =
                PreparedExtensionMutationReconciler.reconcile(stored, registry);
        assertEquals(ReconciliationState.FULLY_APPLIED, report.state());
    }

    @Test
    void thirdPayloadStateIsConflict() throws Exception {
        StoredChangeSet stored = stored();
        FakeTarget target = new FakeTarget();
        target.payload = new byte[]{9};

        HistoryExtensionTargetRegistry registry =
                new HistoryExtensionTargetRegistry(Map.of("lazybuilder:block_entity", target));

        ExtensionMutationExecution execution = PreparedExtensionMutationExecutor.execute(
                stored, registry, new CancellationSource().token());

        assertEquals(MutationExecutionState.CONFLICT, execution.state());
        assertEquals("lazybuilder:block_entity", execution.conflictTypeId());
    }

    private static StoredChangeSet stored() throws IOException {
        try (ChangeSetWriter writer = new MemoryChangeSetStorage().begin("extension-engine")) {
            writer.appendExtension(new HistoryExtensionFrame(
                    "lazybuilder:block_entity", 0, 0, 42L,
                    new byte[]{1}, new byte[]{2}));
            return writer.commit();
        }
    }

    private static final class FakeTarget implements HistoryExtensionMutationTarget {
        byte[] payload = new byte[0];

        @Override
        public byte[] read(HistoryExtensionFrame frame) {
            return payload.clone();
        }

        @Override
        public void write(HistoryExtensionFrame frame, byte[] payload) {
            this.payload = payload.clone();
        }
    }
}

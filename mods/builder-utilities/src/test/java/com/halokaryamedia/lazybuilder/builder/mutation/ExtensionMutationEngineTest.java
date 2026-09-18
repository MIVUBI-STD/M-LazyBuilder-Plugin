package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtensionMutationEngineTest {
    @Test
    void reconcilesFullyAppliedOpaqueExtensionFrames() throws Exception {
        StoredChangeSet stored = stored();
        FakeTarget target = new FakeTarget(new byte[]{2});

        HistoryExtensionTargetRegistry registry =
                new HistoryExtensionTargetRegistry(Map.of("lazybuilder:block_entity", target));

        ExtensionReconciliationReport report =
                PreparedExtensionMutationReconciler.reconcile(stored, registry);
        assertEquals(ReconciliationState.FULLY_APPLIED, report.state());
        stored.close();
    }

    @Test
    void thirdPayloadStateIsConflict() throws Exception {
        StoredChangeSet stored = stored();
        FakeTarget target = new FakeTarget(new byte[]{9});

        HistoryExtensionTargetRegistry registry =
                new HistoryExtensionTargetRegistry(Map.of("lazybuilder:block_entity", target));

        ExtensionReconciliationReport report =
                PreparedExtensionMutationReconciler.reconcile(stored, registry);
        assertEquals(ReconciliationState.CONFLICT, report.state());
        stored.close();
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
        private final byte[] payload;

        private FakeTarget(byte[] payload) {
            this.payload = payload.clone();
        }

        @Override
        public byte[] read(HistoryExtensionFrame frame) {
            return payload.clone();
        }
    }
}

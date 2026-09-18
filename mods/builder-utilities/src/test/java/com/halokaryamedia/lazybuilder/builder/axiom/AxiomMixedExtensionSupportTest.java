package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.structure.EntityExtensionPayload;
import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AxiomMixedExtensionSupportTest {
    @Test
    void rejectsBlockEntityPayloadThatCannotFitServerBridge() throws Exception {
        byte[] tooLarge =
                new byte[BuilderExtensionWireProtocol.MAX_BLOCK_ENTITY_NBT_BYTES + 1];
        try (ChangeSetWriter writer = new MemoryChangeSetStorage().begin("too-large-be")) {
            writer.appendExtension(new HistoryExtensionFrame(
                    HistoryExtensionTypes.BLOCK_ENTITY,
                    0, 0, 1L,
                    new byte[0],
                    tooLarge
            ));
            var stored = writer.commit();
            try (stored) {
                assertThrows(java.io.IOException.class, () ->
                        AxiomMixedExtensionSupport.inspect(stored));
            }
        }
    }

    @Test
    void rejectsMalformedEntityToggleBeforeDispatch() throws Exception {
        byte[] absent = EntityExtensionPayload.absent(0, 64, 0).encode();
        try (ChangeSetWriter writer = new MemoryChangeSetStorage().begin("bad-entity")) {
            writer.appendExtension(new HistoryExtensionFrame(
                    HistoryExtensionTypes.ENTITY,
                    0, 0, 1L,
                    absent,
                    absent
            ));
            var stored = writer.commit();
            try (stored) {
                assertThrows(java.io.IOException.class, () ->
                        AxiomMixedExtensionSupport.inspect(stored));
            }
        }
    }
}

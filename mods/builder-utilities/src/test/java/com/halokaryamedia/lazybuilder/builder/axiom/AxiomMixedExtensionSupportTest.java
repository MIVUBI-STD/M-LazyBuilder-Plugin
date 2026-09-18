package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AxiomMixedExtensionSupportTest {
    @Test
    void countsAllAuthoritativeExtensionTypes() throws Exception {
        StoredChangeSet stored;
        try (ChangeSetWriter writer = new MemoryChangeSetStorage().begin("mixed-count")) {
            writer.appendExtension(new HistoryExtensionFrame(
                    HistoryExtensionTypes.BLOCK_ENTITY, 0, 0, 1, new byte[0], new byte[]{1}));
            writer.appendExtension(new HistoryExtensionFrame(
                    HistoryExtensionTypes.BIOME, 0, 0, 2, new byte[]{1}, new byte[]{2}));
            writer.appendExtension(new HistoryExtensionFrame(
                    HistoryExtensionTypes.ENTITY, 0, 0, 3, new byte[0], new byte[]{3}));
            stored = writer.commit();
        }

        try (stored) {
            var plan = AxiomMixedExtensionSupport.inspect(stored);
            assertEquals(1, plan.blockEntities());
            assertEquals(1, plan.biomes());
            assertEquals(1, plan.entities());
            assertEquals(3, plan.total());
        }
    }
}

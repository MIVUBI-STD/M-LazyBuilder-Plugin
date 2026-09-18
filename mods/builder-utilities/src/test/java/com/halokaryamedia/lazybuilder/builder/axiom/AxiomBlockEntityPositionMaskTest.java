package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AxiomBlockEntityPositionMaskTest {
    @Test
    void removesOnlyBlockEntityOwnedCoordinatesFromAxiomChunk() throws Exception {
        long chest = LocalBlockPosition.pack(1, 64, 2);
        long stone = LocalBlockPosition.pack(2, 64, 2);
        StoredChangeSet stored;
        try (ChangeSetWriter writer = new MemoryChangeSetStorage().begin("be-mask")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:air", "minecraft:chest", "minecraft:stone"),
                    new long[]{chest, stone},
                    new int[]{0, 0},
                    new int[]{1, 2}));
            writer.appendExtension(new HistoryExtensionFrame(
                    HistoryExtensionTypes.BLOCK_ENTITY,
                    0, 0, chest, new byte[0], new byte[]{1}));
            stored = writer.commit();
        }

        try (stored) {
            AxiomBlockEntityPositionMask mask = AxiomBlockEntityPositionMask.from(stored);
            assertTrue(mask.contains(0, 0, chest));
            ChunkChangeSet filtered = mask.withoutOwnedPositions(
                    new ChunkChangeSet(
                            0, 0,
                            List.of("minecraft:air", "minecraft:chest", "minecraft:stone"),
                            new long[]{chest, stone},
                            new int[]{0, 0},
                            new int[]{1, 2}));
            assertEquals(1, filtered.size());
            assertEquals(stone, filtered.positions()[0]);
            assertEquals("minecraft:stone", filtered.afterState(0));
        }
    }
}

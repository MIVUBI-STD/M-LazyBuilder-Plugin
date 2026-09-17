package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoredChangeSetReverserTest {
    @Test
    void reversedRedoRestoresOriginalBeforeStates() throws Exception {
        MemoryChangeSetStorage storage = new MemoryChangeSetStorage();
        StoredChangeSet source;
        try (ChangeSetWriter writer = storage.begin("source")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:dirt", "minecraft:grass_block"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0), LocalBlockPosition.pack(1, 64, 0)},
                    new int[]{0, 2},
                    new int[]{1, 1}
            ));
            source = writer.commit();
        }

        HistoryStorageRouter router = new HistoryStorageRouter(
                new HistorySizingPolicy(1024, 2048), new MemoryChangeSetStorage());
        try (source; StoredChangeSet reversed = StoredChangeSetReverser.reverse(source, router, 128, "rollback")) {
            List<String> redo = new ArrayList<>();
            reversed.replay(ReplayDirection.REDO,
                    (chunkX, chunkZ, localX, y, localZ, state) -> redo.add(state));
            assertEquals(List.of("minecraft:stone", "minecraft:grass_block"), redo);

            List<String> undo = new ArrayList<>();
            reversed.replay(ReplayDirection.UNDO,
                    (chunkX, chunkZ, localX, y, localZ, state) -> undo.add(state));
            assertEquals(List.of("minecraft:dirt", "minecraft:dirt"), undo);
        }
    }
}

package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoredChangeSetChunkVisitTest {
    @TempDir Path tempDir;

    @Test
    void streamsRawChunksAcrossEveryStorageTier() throws Exception {
        List<ChangeSetStorage> storages = List.of(
                new MemoryChangeSetStorage(),
                new CompressedMemoryChangeSetStorage(),
                new DiskChangeSetStorage(tempDir.resolve("disk"))
        );

        for (ChangeSetStorage storage : storages) {
            try (ChangeSetWriter writer = storage.begin("stream-" + storage.tier())) {
                writer.append(chunk(0, 0, 64));
                writer.append(chunk(-2, 3, 70));
                try (StoredChangeSet stored = writer.commit()) {
                    List<String> seen = new ArrayList<>();
                    stored.visitChunks(chunk -> {
                        seen.add(chunk.chunkX() + "," + chunk.chunkZ() + ":" + chunk.size());
                        return true;
                    });
                    assertEquals(List.of("0,0:1", "-2,3:1"), seen);
                }
            }
        }
    }

    @Test
    void stoppingCallbacksStillFinishesStreamValidation() throws Exception {
        ChangeSetStorage storage = new MemoryChangeSetStorage();
        try (ChangeSetWriter writer = storage.begin("bounded-visitor")) {
            writer.append(chunk(0, 0, 64));
            writer.append(chunk(1, 0, 64));
            try (StoredChangeSet stored = writer.commit()) {
                List<Integer> seen = new ArrayList<>();
                stored.visitChunks(chunk -> {
                    seen.add(chunk.chunkX());
                    return false;
                });
                assertEquals(List.of(0), seen);
            }
        }
    }

    private static ChunkChangeSet chunk(int chunkX, int chunkZ, int y) {
        return new ChunkChangeSet(
                chunkX,
                chunkZ,
                List.of("minecraft:stone", "minecraft:air"),
                new long[]{LocalBlockPosition.pack(0, y, 0)},
                new int[]{0},
                new int[]{1}
        );
    }
}

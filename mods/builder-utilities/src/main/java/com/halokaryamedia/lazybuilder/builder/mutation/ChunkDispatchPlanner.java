package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Plans only changes whose current world state still matches the prepared before-state. */
public final class ChunkDispatchPlanner {
    private ChunkDispatchPlanner() {
    }

    public static ChunkDispatchPlan plan(ChunkChangeSet chunk, WorldBlockStateSource world) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(world, "world");
        long[] positions = chunk.positions();
        List<ChunkDispatchEntry> entries = new ArrayList<>();
        for (int i = 0; i < positions.length; i++) {
            long packed = positions[i];
            int localX = LocalBlockPosition.localX(packed);
            int y = LocalBlockPosition.y(packed);
            int localZ = LocalBlockPosition.localZ(packed);
            int worldX = Math.addExact(Math.multiplyExact(chunk.chunkX(), 16), localX);
            int worldZ = Math.addExact(Math.multiplyExact(chunk.chunkZ(), 16), localZ);
            String before = chunk.beforeState(i);
            String after = chunk.afterState(i);
            String actual = world.readBlockState(worldX, y, worldZ);
            if (after.equals(actual)) continue;
            if (!before.equals(actual)) return ChunkDispatchPlan.conflict(worldX, y, worldZ);
            entries.add(new ChunkDispatchEntry(worldX, y, worldZ, after));
        }
        return entries.isEmpty() ? ChunkDispatchPlan.alreadyApplied() : ChunkDispatchPlan.ready(entries);
    }
}

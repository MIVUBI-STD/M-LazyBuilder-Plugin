package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import com.halokaryamedia.lazybuilder.builder.region.BoxRegion;
import com.halokaryamedia.lazybuilder.builder.region.BuilderRegion;
import com.halokaryamedia.lazybuilder.builder.region.ChunkWorkUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Resolves one chunk work unit into an exact mutation/history delta. */
public final class MaterialMutationPlanner {
    private MaterialMutationPlanner() { }

    public static ChunkChangeSet plan(ChunkWorkUnit unit, BuilderMaterial material, MaterialMask mask,
                                      OperationSeed seed, BlockStateSource source) {
        return plan(unit, new BoxRegion(unit.candidateBounds()), material, mask, seed, source);
    }

    public static ChunkChangeSet plan(ChunkWorkUnit unit, BuilderRegion region, BuilderMaterial material,
                                      MaterialMask mask, OperationSeed seed, BlockStateSource source) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(source, "source");

        Accumulator changes = new Accumulator(unit.chunkX(), unit.chunkZ(), initialCapacity(unit.candidateBlockCount()));
        BlockBounds bounds = unit.candidateBounds();
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            for (long z = bounds.minZ(); z <= (long) bounds.maxZ(); z++) {
                for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                    int worldX = (int) x, worldY = (int) y, worldZ = (int) z;
                    if (!region.contains(worldX, worldY, worldZ)) continue;
                    String before = requireState(source.stateAt(worldX, worldY, worldZ), "source state");
                    MaterialContext context = new MaterialContext(worldX, worldY, worldZ, before, seed);
                    if (!mask.test(context)) continue;
                    String after = requireState(material.resolve(context), "resolved material state");
                    if (before.equals(after)) continue;
                    changes.add(worldX, worldY, worldZ, before, after);
                }
            }
        }
        return changes.build();
    }

    private static int initialCapacity(long candidateCount) { return (int) Math.max(1L, Math.min(candidateCount, 4096L)); }
    private static String requireState(String state, String label) {
        if (state == null || state.isBlank()) throw new IllegalArgumentException(label + " must be non-blank");
        return state;
    }

    private static final class Accumulator {
        private final int chunkX, chunkZ;
        private final Map<String, Integer> palette = new LinkedHashMap<>();
        private long[] positions; private int[] before; private int[] after; private int size;
        private Accumulator(int chunkX, int chunkZ, int capacity) {
            this.chunkX = chunkX; this.chunkZ = chunkZ;
            positions = new long[capacity]; before = new int[capacity]; after = new int[capacity];
        }
        private void add(int x, int y, int z, String oldState, String newState) {
            ensureCapacity(size + 1);
            positions[size] = LocalBlockPosition.pack(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
            before[size] = paletteIndex(oldState); after[size] = paletteIndex(newState); size++;
        }
        private int paletteIndex(String state) { return palette.computeIfAbsent(state, ignored -> palette.size()); }
        private void ensureCapacity(int required) {
            if (required <= positions.length) return;
            int grown = Math.max(required, Math.multiplyExact(positions.length, 2));
            positions = Arrays.copyOf(positions, grown); before = Arrays.copyOf(before, grown); after = Arrays.copyOf(after, grown);
        }
        private ChunkChangeSet build() {
            String[] orderedPalette = new String[palette.size()];
            palette.forEach((state, index) -> orderedPalette[index] = state);
            return new ChunkChangeSet(chunkX, chunkZ, Arrays.asList(orderedPalette),
                    Arrays.copyOf(positions, size), Arrays.copyOf(before, size), Arrays.copyOf(after, size));
        }
    }
}

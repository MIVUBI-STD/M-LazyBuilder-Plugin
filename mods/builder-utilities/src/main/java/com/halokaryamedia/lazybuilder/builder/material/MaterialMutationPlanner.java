package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSetBuilder;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import com.halokaryamedia.lazybuilder.builder.region.BoxRegion;
import com.halokaryamedia.lazybuilder.builder.region.BuilderRegion;
import com.halokaryamedia.lazybuilder.builder.region.ChunkWorkUnit;
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

        ChunkChangeSetBuilder changes = new ChunkChangeSetBuilder(unit.chunkX(), unit.chunkZ());
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
                    changes.addWorld(worldX, worldY, worldZ, before, after);
                }
            }
        }
        return changes.build();
    }

    private static String requireState(String state, String label) {
        if (state == null || state.isBlank()) throw new IllegalArgumentException(label + " must be non-blank");
        return state;
    }
}

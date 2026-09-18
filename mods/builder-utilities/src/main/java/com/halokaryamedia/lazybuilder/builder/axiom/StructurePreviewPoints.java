package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.structure.StructureBlock;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import com.halokaryamedia.lazybuilder.builder.structure.StructureSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministically decimated structure preview points after placement transforms. */
public final class StructurePreviewPoints {
    public static final int MAX_PREVIEW_POINTS = 250_000;

    private StructurePreviewPoints() {}

    public static List<PlacementPoint> create(
            StructureSnapshot snapshot,
            StructurePlacement placement
    ) {
        return createMany(List.of(new Instance(snapshot, placement)), MAX_PREVIEW_POINTS);
    }

    public static List<PlacementPoint> createMany(
            List<Instance> instances,
            int maxPoints
    ) {
        Objects.requireNonNull(instances, "instances");
        if (maxPoints <= 0) throw new IllegalArgumentException("maxPoints must be > 0");

        long total = 0L;
        for (Instance instance : instances) {
            Objects.requireNonNull(instance, "instance");
            total = Math.addExact(total, instance.snapshot().blockCount());
        }
        if (total == 0) return List.of();

        long stride = Math.max(1L, 1L + (total - 1L) / maxPoints);
        List<PlacementPoint> result = new ArrayList<>((int) Math.min(total, maxPoints));
        long global = 0L;
        int ordinal = 0;

        for (Instance instance : instances) {
            for (StructureBlock block : instance.snapshot().blocks()) {
                boolean include = global % stride == 0L;
                global++;
                if (!include) continue;
                StructurePlacement.WorldPosition world =
                        instance.placement().transform(block.x(), block.y(), block.z());
                result.add(new PlacementPoint(world.x(), world.y(), world.z(), ordinal++));
                if (result.size() >= maxPoints) return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }

    public static boolean isDecimated(long totalBlocks) {
        if (totalBlocks < 0) throw new IllegalArgumentException("totalBlocks must be >= 0");
        return totalBlocks > MAX_PREVIEW_POINTS;
    }

    public record Instance(StructureSnapshot snapshot, StructurePlacement placement) {
        public Instance {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(placement, "placement");
        }
    }
}

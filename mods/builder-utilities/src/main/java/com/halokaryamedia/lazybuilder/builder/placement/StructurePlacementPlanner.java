package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Placement planner for heterogeneous structure sources. Collision checks use each
 * resolved source footprint after scale/yaw, avoiding the single-footprint shortcut.
 */
public final class StructurePlacementPlanner {
    private StructurePlacementPlanner() {}

    public static List<PlacementPlanEntry> plan(
            BlockBounds bounds,
            SurfaceHeightSource surface,
            OperationSeed seed,
            PlacementDistribution distribution,
            PlacementSource source,
            PlacementVariation variation,
            PlacementConstraint constraint,
            StructureFootprintSource footprints,
            boolean rejectCollisions
    ) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(distribution, "distribution");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(variation, "variation");
        Objects.requireNonNull(constraint, "constraint");
        Objects.requireNonNull(footprints, "footprints");

        List<PlacementPlanEntry> accepted = new ArrayList<>();
        List<StructureFootprint.Resolved> occupied = new ArrayList<>();

        for (PlacementPoint point : distribution.generate(bounds, surface, seed)) {
            if (!constraint.test(point)) continue;
            String sourceId = source.resolve(point, seed);
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalArgumentException("placement source returned blank source id");
            }
            PlacementTransform transform = variation.resolve(point, seed);
            StructureFootprint footprint = Objects.requireNonNull(
                    footprints.footprintFor(sourceId), "structure footprint");
            StructureFootprint.Resolved resolved = footprint.resolve(point, transform);
            if (rejectCollisions && overlapsAny(resolved, occupied)) continue;

            accepted.add(new PlacementPlanEntry(point, sourceId, transform));
            occupied.add(resolved);
        }
        return List.copyOf(accepted);
    }

    private static boolean overlapsAny(
            StructureFootprint.Resolved candidate,
            List<StructureFootprint.Resolved> occupied
    ) {
        for (StructureFootprint.Resolved existing : occupied) {
            if (candidate.overlaps(existing)) return true;
        }
        return false;
    }
}

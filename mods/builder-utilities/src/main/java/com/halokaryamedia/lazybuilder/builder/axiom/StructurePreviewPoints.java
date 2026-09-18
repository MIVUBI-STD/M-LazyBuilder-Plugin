package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.structure.StructureBlock;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import com.halokaryamedia.lazybuilder.builder.structure.StructureSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer structure preview points after mirror/quarter-turn placement. */
public final class StructurePreviewPoints {
    public static final int MAX_PREVIEW_POINTS = 250_000;

    private StructurePreviewPoints() {}

    public static List<PlacementPoint> create(
            StructureSnapshot snapshot,
            StructurePlacement placement
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(placement, "placement");

        if (snapshot.blockCount() > MAX_PREVIEW_POINTS) {
            throw new IllegalArgumentException(
                    "structure preview exceeds " + MAX_PREVIEW_POINTS + " blocks");
        }

        List<PlacementPoint> result = new ArrayList<>(snapshot.blockCount());
        int ordinal = 0;
        for (StructureBlock block : snapshot.blocks()) {
            StructurePlacement.WorldPosition world =
                    placement.transform(block.x(), block.y(), block.z());
            result.add(new PlacementPoint(world.x(), world.y(), world.z(), ordinal++));
        }
        return List.copyOf(result);
    }
}

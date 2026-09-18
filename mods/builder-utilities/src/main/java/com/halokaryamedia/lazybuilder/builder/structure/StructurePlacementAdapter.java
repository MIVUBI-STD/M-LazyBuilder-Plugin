package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;

import java.util.Objects;

/** Converts abstract placement transforms to integer-safe structure transforms. */
public final class StructurePlacementAdapter {
    private static final double EPSILON = 1.0e-6;

    private StructurePlacementAdapter() {}

    public static StructurePlacement from(PlacementPlanEntry entry) {
        Objects.requireNonNull(entry, "entry");
        PlacementTransform transform = entry.transform();
        if (Math.abs(transform.scale() - 1.0) > EPSILON) {
            throw new IllegalArgumentException(
                    "block structures require unit scale; requested " + transform.scale());
        }

        double quarter = transform.yawDegrees() / 90.0;
        long rounded = Math.round(quarter);
        if (Math.abs(quarter - rounded) > EPSILON) {
            throw new IllegalArgumentException(
                    "block structures require yaw in 90-degree increments; requested "
                            + transform.yawDegrees());
        }

        return new StructurePlacement(
                entry.point().x(),
                entry.point().y(),
                entry.point().z(),
                Math.toIntExact(rounded),
                transform.mirrorX(),
                transform.mirrorZ()
        );
    }
}

package com.halokaryamedia.lazybuilder.builder.placement;

public record PlacementPlanEntry(PlacementPoint point, String sourceId, PlacementTransform transform) {
    public PlacementPlanEntry {
        if (point == null) throw new NullPointerException("point");
        if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("sourceId must be non-blank");
        if (transform == null) throw new NullPointerException("transform");
    }
}

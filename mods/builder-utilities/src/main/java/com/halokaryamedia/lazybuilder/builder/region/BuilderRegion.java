package com.halokaryamedia.lazybuilder.builder.region;

/**
 * Spatial contract consumed by the Builder operation planner.
 *
 * <p>The bounding box is used only to identify candidate chunks. Executors must
 * still call {@link #contains(int, int, int)} for non-box regions.</p>
 */
public interface BuilderRegion {
    BlockBounds bounds();

    boolean contains(int x, int y, int z);
}

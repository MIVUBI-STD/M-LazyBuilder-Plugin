package com.halokaryamedia.lazybuilder.terraform;

/**
 * Platform-neutral scalar field describing one terrain shape.
 * Values less than or equal to zero are inside the solid shape.
 */
@FunctionalInterface
public interface ShapeField {
    double sample(double x, double y, double z);

    default boolean contains(double x, double y, double z) {
        return sample(x, y, z) <= 0.0;
    }
}

package com.halokaryamedia.lazybuilder.builder.material;

/**
 * Normalizes world Y into [0,1] for height-driven materials.
 */
public record HeightRangeField(int minY, int maxY) implements ScalarField {
    public HeightRangeField {
        if (minY >= maxY) {
            throw new IllegalArgumentException("minY must be < maxY");
        }
    }

    @Override
    public double sample(MaterialContext context) {
        if (context.y() <= minY) return 0.0;
        if (context.y() >= maxY) return 1.0;
        return ((double) context.y() - minY) / ((double) maxY - minY);
    }
}

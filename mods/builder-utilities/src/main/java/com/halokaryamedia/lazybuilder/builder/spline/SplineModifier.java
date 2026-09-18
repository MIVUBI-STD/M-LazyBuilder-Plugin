package com.halokaryamedia.lazybuilder.builder.spline;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;

@FunctionalInterface
public interface SplineModifier {
    SplineSample apply(SplineSample sample, int index, int sampleCount, OperationSeed seed);
}

package com.halokaryamedia.lazybuilder.builder.spline;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;

import java.util.List;

/**
 * Reusable payload contract evaluated over already-sampled spline geometry.
 */
@FunctionalInterface
public interface SplinePayload<T> {
    List<T> plan(List<SplineSample> samples, OperationSeed seed);
}

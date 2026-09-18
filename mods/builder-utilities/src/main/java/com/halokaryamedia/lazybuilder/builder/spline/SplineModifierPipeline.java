package com.halokaryamedia.lazybuilder.builder.spline;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SplineModifierPipeline {
    private SplineModifierPipeline() {}

    public static List<SplineSample> apply(
            List<SplineSample> samples,
            SplineModifier modifier,
            OperationSeed seed
    ) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(modifier, "modifier");
        Objects.requireNonNull(seed, "seed");
        List<SplineSample> result = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            result.add(Objects.requireNonNull(
                    modifier.apply(Objects.requireNonNull(samples.get(i), "sample"), i, samples.size(), seed),
                    "modifier result"
            ));
        }
        return List.copyOf(result);
    }
}

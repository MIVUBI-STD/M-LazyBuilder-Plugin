package com.halokaryamedia.lazybuilder.builder.spline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SplineSampler {
    public static final int MAX_SAMPLES = 1_000_000;

    private SplineSampler() {
    }

    public static List<SplineSample> sample(CatmullRomSpline spline, int samplesPerSegment) {
        Objects.requireNonNull(spline, "spline");
        if (samplesPerSegment <= 0) throw new IllegalArgumentException("samplesPerSegment must be > 0");
        int intervals = Math.multiplyExact(spline.segmentCount(), samplesPerSegment);
        int sampleCount = Math.addExact(intervals, 1);
        if (sampleCount > MAX_SAMPLES) {
            throw new IllegalArgumentException("spline sample count exceeds " + MAX_SAMPLES);
        }
        List<SplineSample> samples = new ArrayList<>(sampleCount);
        SplineFrame transported = null;
        for (int i = 0; i <= intervals; i++) {
            double t = (double) i / intervals;
            SplineEvaluation evaluation = spline.evaluate(t);
            transported = transported == null
                    ? ParallelTransport.initial(evaluation.tangent())
                    : ParallelTransport.transport(transported, evaluation.tangent());
            SplineFrame rolled = ParallelTransport.applyRoll(transported, evaluation.rollDegrees());
            samples.add(new SplineSample(t, evaluation.position(), rolled, evaluation.radius(), evaluation.rollDegrees()));
        }
        return List.copyOf(samples);
    }
}

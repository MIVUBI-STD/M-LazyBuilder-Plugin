package com.halokaryamedia.lazybuilder.builder.spline;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;

import java.util.Objects;

/** Reusable deterministic geometry modifiers for spline-driven tools. */
public final class SplineModifiers {
    private SplineModifiers() {}

    public static SplineModifier identity() {
        return (sample, index, count, seed) -> sample;
    }

    public static SplineModifier taper(double startScale, double endScale) {
        if (!Double.isFinite(startScale) || startScale < 0.0
                || !Double.isFinite(endScale) || endScale < 0.0) {
            throw new IllegalArgumentException("taper scales must be finite and >= 0");
        }
        return (sample, index, count, seed) -> {
            double u = normalizedIndex(index, count);
            double scale = startScale + (endScale - startScale) * u;
            return new SplineSample(
                    sample.t(),
                    sample.position(),
                    sample.frame(),
                    sample.radius() * scale,
                    sample.rollDegrees()
            );
        };
    }

    public static SplineModifier twist(double startDegrees, double endDegrees) {
        if (!Double.isFinite(startDegrees) || !Double.isFinite(endDegrees)) {
            throw new IllegalArgumentException("twist angles must be finite");
        }
        return (sample, index, count, seed) -> {
            double u = normalizedIndex(index, count);
            double twist = startDegrees + (endDegrees - startDegrees) * u;
            return new SplineSample(
                    sample.t(),
                    sample.position(),
                    ParallelTransport.applyRoll(sample.frame(), twist),
                    sample.radius(),
                    sample.rollDegrees() + twist
            );
        };
    }

    public static SplineModifier offset(double normalOffset, double binormalOffset) {
        if (!Double.isFinite(normalOffset) || !Double.isFinite(binormalOffset)) {
            throw new IllegalArgumentException("offsets must be finite");
        }
        return (sample, index, count, seed) -> {
            BuilderVec3 position = sample.position()
                    .add(sample.frame().normal().multiply(normalOffset))
                    .add(sample.frame().binormal().multiply(binormalOffset));
            return new SplineSample(
                    sample.t(), position, sample.frame(), sample.radius(), sample.rollDegrees());
        };
    }

    public static SplineModifier jitter(
            double normalAmplitude,
            double binormalAmplitude,
            double radiusAmplitude,
            long channel
    ) {
        if (!Double.isFinite(normalAmplitude) || normalAmplitude < 0.0
                || !Double.isFinite(binormalAmplitude) || binormalAmplitude < 0.0
                || !Double.isFinite(radiusAmplitude) || radiusAmplitude < 0.0) {
            throw new IllegalArgumentException("jitter amplitudes must be finite and >= 0");
        }
        return (sample, index, count, seed) -> {
            double n = signed(seed.sampleUnit(index, 0, 0, channel)) * normalAmplitude;
            double b = signed(seed.sampleUnit(index, 0, 0, channel + 1)) * binormalAmplitude;
            double r = signed(seed.sampleUnit(index, 0, 0, channel + 2)) * radiusAmplitude;
            BuilderVec3 position = sample.position()
                    .add(sample.frame().normal().multiply(n))
                    .add(sample.frame().binormal().multiply(b));
            return new SplineSample(
                    sample.t(),
                    position,
                    sample.frame(),
                    Math.max(0.0, sample.radius() + r),
                    sample.rollDegrees()
            );
        };
    }

    public static SplineModifier compose(SplineModifier... modifiers) {
        Objects.requireNonNull(modifiers, "modifiers");
        SplineModifier[] copy = modifiers.clone();
        for (SplineModifier modifier : copy) Objects.requireNonNull(modifier, "modifier");
        return (sample, index, count, seed) -> {
            SplineSample result = sample;
            for (SplineModifier modifier : copy) {
                result = Objects.requireNonNull(
                        modifier.apply(result, index, count, seed),
                        "modifier result"
                );
            }
            return result;
        };
    }

    private static double normalizedIndex(int index, int count) {
        if (count <= 0 || index < 0 || index >= count) {
            throw new IllegalArgumentException("invalid spline sample index/count");
        }
        return count == 1 ? 0.0 : index / (double) (count - 1);
    }

    private static double signed(double unit) {
        return unit * 2.0 - 1.0;
    }
}

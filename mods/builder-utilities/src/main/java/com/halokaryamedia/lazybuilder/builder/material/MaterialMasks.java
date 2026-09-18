package com.halokaryamedia.lazybuilder.builder.material;

import java.util.Objects;
import java.util.Set;

public final class MaterialMasks {
    private MaterialMasks() {
    }

    public static MaterialMask all() {
        return context -> true;
    }

    public static MaterialMask none() {
        return context -> false;
    }

    public static MaterialMask existingState(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state must be non-blank");
        }
        return context -> state.equals(context.existingBlockState());
    }

    public static MaterialMask existingStates(Set<String> states) {
        if (states == null || states.isEmpty() || states.stream().anyMatch(state -> state == null || state.isBlank())) {
            throw new IllegalArgumentException("states must contain non-blank values");
        }
        Set<String> copy = Set.copyOf(states);
        return context -> copy.contains(context.existingBlockState());
    }

    public static MaterialMask fieldAtOrAbove(ScalarField field, double threshold) {
        Objects.requireNonNull(field, "field");
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("threshold must be finite");
        }
        return context -> field.sample(context) >= threshold;
    }

    public static MaterialMask fieldBetween(
            ScalarField field,
            double minimumInclusive,
            double maximumInclusive
    ) {
        Objects.requireNonNull(field, "field");
        if (!Double.isFinite(minimumInclusive)
                || !Double.isFinite(maximumInclusive)
                || maximumInclusive < minimumInclusive) {
            throw new IllegalArgumentException("field range must be finite and ordered");
        }
        return context -> {
            double value = field.sample(context);
            return value >= minimumInclusive && value <= maximumInclusive;
        };
    }

    public static MaterialMask yBetween(int minimumInclusive, int maximumInclusive) {
        if (maximumInclusive < minimumInclusive) {
            throw new IllegalArgumentException("Y range must be ordered");
        }
        return context -> context.y() >= minimumInclusive && context.y() <= maximumInclusive;
    }

    public static MaterialMask deterministicDensity(double probability, long channel) {
        if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be in [0,1]");
        }
        return context -> context.seed().sampleUnit(
                context.x(), context.y(), context.z(), channel) < probability;
    }

    public static MaterialMask xor(MaterialMask first, MaterialMask second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return context -> first.test(context) ^ second.test(context);
    }

    public static MaterialMask not(MaterialMask mask) {
        Objects.requireNonNull(mask, "mask");
        return context -> !mask.test(context);
    }

    public static MaterialMask and(MaterialMask first, MaterialMask second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return context -> first.test(context) && second.test(context);
    }

    public static MaterialMask or(MaterialMask first, MaterialMask second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return context -> first.test(context) || second.test(context);
    }
}

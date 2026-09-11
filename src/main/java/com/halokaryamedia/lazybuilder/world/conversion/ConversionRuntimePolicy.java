package com.halokaryamedia.lazybuilder.world.conversion;

import java.time.Duration;

/**
 * Stable resource/update defaults for the internal World Manager conversion runtime.
 */
public record ConversionRuntimePolicy(
        UpdateMode updateMode,
        Duration minimumCheckInterval,
        boolean stableOnly,
        boolean checksumRequired,
        boolean compatibilityProbeRequired,
        boolean rollbackEnabled,
        int retainedVerifiedVersions,
        int maxConcurrentConversions
) {
    public enum UpdateMode {
        AUTOMATIC_STABLE,
        NOTIFY_ONLY,
        MANUAL
    }

    public ConversionRuntimePolicy {
        if (minimumCheckInterval.isNegative() || minimumCheckInterval.isZero()) {
            throw new IllegalArgumentException("minimumCheckInterval must be positive");
        }
        if (retainedVerifiedVersions < 1) {
            throw new IllegalArgumentException("retainedVerifiedVersions must be at least 1");
        }
        if (maxConcurrentConversions < 1) {
            throw new IllegalArgumentException("maxConcurrentConversions must be at least 1");
        }
    }

    public static ConversionRuntimePolicy defaults() {
        return new ConversionRuntimePolicy(
                UpdateMode.AUTOMATIC_STABLE,
                Duration.ofHours(24),
                true,
                true,
                true,
                true,
                2,
                1
        );
    }
}

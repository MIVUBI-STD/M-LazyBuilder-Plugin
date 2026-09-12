package com.halokaryamedia.lazybuilder.world.conversion;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Verified metadata for one installed internal conversion runtime. */
public record ConversionRuntimeManifest(
        String version,
        int adapterContract,
        String sha256,
        Instant installedAt,
        List<String> supportedFormats
) {
    public ConversionRuntimeManifest {
        version = requireText(version, "version");
        sha256 = requireText(sha256, "sha256").toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(installedAt, "installedAt");
        supportedFormats = List.copyOf(Objects.requireNonNull(supportedFormats, "supportedFormats"));
        if (adapterContract < 1) {
            throw new IllegalArgumentException("adapterContract must be at least 1");
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be a 64-character hexadecimal digest");
        }
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}

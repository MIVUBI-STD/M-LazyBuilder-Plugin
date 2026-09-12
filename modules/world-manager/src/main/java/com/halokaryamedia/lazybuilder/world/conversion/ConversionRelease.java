package com.halokaryamedia.lazybuilder.world.conversion;

import java.net.URI;

/** One stable runtime release selected by the update source. */
public record ConversionRelease(String version, URI artifactUri, String sha256) {
    public ConversionRelease {
        version = java.util.Objects.requireNonNull(version, "version").strip();
        java.util.Objects.requireNonNull(artifactUri, "artifactUri");
        sha256 = java.util.Objects.requireNonNull(sha256, "sha256").strip().toLowerCase(java.util.Locale.ROOT);
        if (version.isEmpty()) throw new IllegalArgumentException("version must not be blank");
        if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be hexadecimal");
    }
}

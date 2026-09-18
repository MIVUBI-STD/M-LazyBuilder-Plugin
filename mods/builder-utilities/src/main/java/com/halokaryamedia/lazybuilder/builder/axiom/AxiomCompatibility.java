package com.halokaryamedia.lazybuilder.builder.axiom;

import net.fabricmc.loader.api.FabricLoader;

import java.util.Objects;

/** Read-only runtime identity for the Axiom dependency accepted by fabric.mod.json. */
public record AxiomCompatibility(
        String installedVersion,
        String supportedRange
) {
    public static final String SUPPORTED_RANGE = ">=5.3.0 <5.5.0";

    public AxiomCompatibility {
        if (installedVersion == null || installedVersion.isBlank()) {
            throw new IllegalArgumentException("installedVersion must be non-blank");
        }
        if (supportedRange == null || supportedRange.isBlank()) {
            throw new IllegalArgumentException("supportedRange must be non-blank");
        }
    }

    public static AxiomCompatibility current() {
        var container = FabricLoader.getInstance()
                .getModContainer("axiom")
                .orElseThrow(() -> new IllegalStateException(
                        "Axiom is required but Fabric Loader did not expose its mod container"));
        String installed = Objects.requireNonNull(
                container.getMetadata().getVersion().getFriendlyString(),
                "Axiom version");
        return new AxiomCompatibility(installed, SUPPORTED_RANGE);
    }

    public String summary() {
        return "Axiom " + installedVersion + " | supported " + supportedRange;
    }
}

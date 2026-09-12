package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Converter-specific boundary. Only this adapter may know CLI/runtime details. */
public interface ConverterAdapter {
    int ADAPTER_CONTRACT = 1;

    ConverterProbe probe(Path runtimeArtifact) throws IOException;

    record ConverterProbe(String runtimeVersion, List<String> supportedFormats) {
        public ConverterProbe {
            runtimeVersion = java.util.Objects.requireNonNull(runtimeVersion, "runtimeVersion").strip();
            supportedFormats = List.copyOf(java.util.Objects.requireNonNull(supportedFormats, "supportedFormats"));
            if (runtimeVersion.isEmpty()) throw new IllegalArgumentException("runtimeVersion must not be blank");
        }
    }
}

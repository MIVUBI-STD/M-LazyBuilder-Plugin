package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Converter-specific boundary. Only this adapter may know CLI/runtime details. */
public interface ConverterAdapter {
    int ADAPTER_CONTRACT = 2;

    ConverterProbe probe(Path runtimeArtifact) throws IOException;

    default ConversionResult convert(Path runtimeArtifact, ConversionRequest request) throws IOException {
        throw new UnsupportedOperationException("Conversion execution is not implemented by this adapter");
    }

    record ConverterProbe(String runtimeVersion, List<String> supportedFormats) {
        public ConverterProbe {
            runtimeVersion = Objects.requireNonNull(runtimeVersion, "runtimeVersion").strip();
            supportedFormats = List.copyOf(Objects.requireNonNull(supportedFormats, "supportedFormats"));
            if (runtimeVersion.isEmpty()) throw new IllegalArgumentException("runtimeVersion must not be blank");
            if (supportedFormats.isEmpty()) throw new IllegalArgumentException("supportedFormats must not be empty");
        }
    }

    record ConversionRequest(
            Path inputDirectory,
            Path outputDirectory,
            String outputFormat,
            Path pruningSettings,
            Path worldSettings,
            Path converterSettings
    ) {
        public ConversionRequest(
                Path inputDirectory,
                Path outputDirectory,
                String outputFormat,
                Path pruningSettings
        ) {
            this(inputDirectory, outputDirectory, outputFormat, pruningSettings, null, null);
        }

        public ConversionRequest {
            inputDirectory = Objects.requireNonNull(inputDirectory, "inputDirectory").toAbsolutePath().normalize();
            outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
            outputFormat = Objects.requireNonNull(outputFormat, "outputFormat").strip().toUpperCase(java.util.Locale.ROOT);
            if (outputFormat.isEmpty()) throw new IllegalArgumentException("outputFormat must not be blank");
            if (pruningSettings != null) pruningSettings = pruningSettings.toAbsolutePath().normalize();
            if (worldSettings != null) worldSettings = worldSettings.toAbsolutePath().normalize();
            if (converterSettings != null) converterSettings = converterSettings.toAbsolutePath().normalize();
        }
    }

    record ConversionResult(String runtimeOutput) {
        public ConversionResult {
            runtimeOutput = Objects.requireNonNull(runtimeOutput, "runtimeOutput");
        }
    }
}

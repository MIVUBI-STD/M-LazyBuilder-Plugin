package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Converter-specific boundary. Only this adapter may know CLI/runtime details. */
public interface ConverterAdapter {
    int ADAPTER_CONTRACT = 3;

    ConverterProbe probe(Path runtimeArtifact) throws IOException;

    /**
     * True only when this exact request can be completed without acquiring or executing
     * the external conversion runtime. The request remains owned by this adapter; callers
     * must not infer why a particular request is runtime-free.
     */
    default boolean canConvertWithoutRuntime(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        return false;
    }

    /** Executes a request previously accepted by {@link #canConvertWithoutRuntime(ConversionRequest)}. */
    default ConversionResult convertWithoutRuntime(ConversionRequest request) throws IOException {
        Objects.requireNonNull(request, "request");
        throw new UnsupportedOperationException("Runtime-free conversion is not implemented by this adapter");
    }

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
        private static final String CANONICAL_NATIVE_FORMAT = "JAVA_1_21_4";

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

        /**
         * Managed LazyBuilder worlds are canonical Java 1.21.4. A pruning request targeting
         * that same format is therefore a same-format area export and can safely request
         * original NBT preservation. Cross-version/cross-edition conversions must not.
         */
        public boolean keepOriginalNbt() {
            return pruningSettings != null && CANONICAL_NATIVE_FORMAT.equals(outputFormat);
        }
    }

    record ConversionResult(String runtimeOutput) {
        public ConversionResult {
            runtimeOutput = Objects.requireNonNull(runtimeOutput, "runtimeOutput");
        }
    }
}

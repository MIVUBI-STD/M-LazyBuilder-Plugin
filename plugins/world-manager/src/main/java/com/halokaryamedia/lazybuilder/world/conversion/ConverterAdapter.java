package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Converter-specific boundary. Only this adapter may know CLI/runtime details. */
public interface ConverterAdapter {
    int ADAPTER_CONTRACT = 2;

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
            Path converterSettings,
            boolean preserveNativeInput
    ) {
        private static final String CANONICAL_NATIVE_FORMAT = "JAVA_1_21_4";

        /** Compatibility constructor used by normal converter-backed requests. */
        public ConversionRequest(
                Path inputDirectory,
                Path outputDirectory,
                String outputFormat,
                Path pruningSettings,
                Path worldSettings,
                Path converterSettings
        ) {
            this(inputDirectory, outputDirectory, outputFormat, pruningSettings,
                    worldSettings, converterSettings,
                    pruningSettings != null && CANONICAL_NATIVE_FORMAT.equalsIgnoreCase(outputFormat));
        }

        public ConversionRequest(
                Path inputDirectory,
                Path outputDirectory,
                String outputFormat,
                Path pruningSettings
        ) {
            this(inputDirectory, outputDirectory, outputFormat, pruningSettings, null, null,
                    pruningSettings != null && CANONICAL_NATIVE_FORMAT.equalsIgnoreCase(outputFormat));
        }

        public ConversionRequest {
            inputDirectory = Objects.requireNonNull(inputDirectory, "inputDirectory").toAbsolutePath().normalize();
            outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
            outputFormat = Objects.requireNonNull(outputFormat, "outputFormat").strip().toUpperCase(java.util.Locale.ROOT);
            if (outputFormat.isEmpty()) throw new IllegalArgumentException("outputFormat must not be blank");
            if (pruningSettings != null) pruningSettings = pruningSettings.toAbsolutePath().normalize();
            if (worldSettings != null) worldSettings = worldSettings.toAbsolutePath().normalize();
            if (converterSettings != null) converterSettings = converterSettings.toAbsolutePath().normalize();
            if (preserveNativeInput && !CANONICAL_NATIVE_FORMAT.equals(outputFormat)) {
                throw new IllegalArgumentException("Native-input preservation requires canonical Java 1.21.4 output");
            }
        }

        /** Same-format native chunk preservation is valid only for canonical managed export input. */
        public boolean keepOriginalNbt() {
            return preserveNativeInput && pruningSettings != null && CANONICAL_NATIVE_FORMAT.equals(outputFormat);
        }

        public boolean canonicalNativeOutput() {
            return CANONICAL_NATIVE_FORMAT.equals(outputFormat);
        }
    }

    record ConversionResult(String runtimeOutput) {
        public ConversionResult {
            runtimeOutput = Objects.requireNonNull(runtimeOutput, "runtimeOutput");
        }
    }
}

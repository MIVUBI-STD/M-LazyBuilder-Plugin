package com.halokaryamedia.lazybuilder.performance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class PerformanceShaderReloadRegistrationContract {
    private PerformanceShaderReloadRegistrationContract() {
    }

    static boolean sourceContractPresent() throws IOException {
        try (var stream = PerformanceShaderReloadRegistrationContract.class.getClassLoader()
                .getResourceAsStream("performance-reload-registration.contract")) {
            if (stream == null) return false;
            String value = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return value.contains("PerformanceShaderReloadInvalidator")
                    && value.contains("CLIENT_RESOURCES");
        }
    }
}

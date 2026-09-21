package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PerformanceReloadRegistrationTest {
    @Test
    void clientEntrypointRegistersShaderReloadInvalidation() throws IOException {
        String resource = "com/halokaryamedia/lazybuilder/performance/PerformanceManagerClient.class";
        try (var stream = PerformanceReloadRegistrationTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "PerformanceManagerClient must be packaged");
            String classBytes = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(
                    classBytes.contains("PerformanceShaderReloadInvalidator"),
                    "client entrypoint must reference the shader reload invalidator"
            );
            assertTrue(
                    classBytes.contains("CLIENT_RESOURCES"),
                    "shader reload invalidator must be registered for client resources"
            );
        }
    }
    @Test
    void clientEntrypointUsesShaderOnlyReloadForTerrainChanges() throws IOException {
        String resource = "com/halokaryamedia/lazybuilder/performance/PerformanceManagerClient.class";
        try (var stream = PerformanceReloadRegistrationTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "PerformanceManagerClient must be packaged");
            String classBytes = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);

            assertTrue(
                    classBytes.contains("getShaderLoader"),
                    "terrain shader changes must use Minecraft's ShaderLoader boundary"
            );
            assertFalse(
                    classBytes.contains("reloadResources"),
                    "terrain shader changes must not trigger a full client-resource reload"
            );
        }
    }
}

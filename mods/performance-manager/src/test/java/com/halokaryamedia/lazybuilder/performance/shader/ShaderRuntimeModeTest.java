package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ShaderRuntimeModeTest {
    @Test
    void preservesTerrainWhenOptionalPassesAreUnavailable() {
        assertEquals(
                ShaderRuntimeMode.TERRAIN_ONLY,
                ShaderRuntimeMode.evaluate(true, true, true, true, false, true, false)
        );
    }

    @Test
    void reportsFullOnlyWhenDeclaredOptionalPassesAreHealthy() {
        assertEquals(
                ShaderRuntimeMode.FULL,
                ShaderRuntimeMode.evaluate(true, true, true, true, true, true, true)
        );
    }

    @Test
    void failedTerrainIntegrationFallsBackExplicitly() {
        assertEquals(
                ShaderRuntimeMode.FALLBACK,
                ShaderRuntimeMode.evaluate(true, true, false, true, true, true, true)
        );
    }
}

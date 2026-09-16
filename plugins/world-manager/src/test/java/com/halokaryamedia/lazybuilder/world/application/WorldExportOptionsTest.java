package com.halokaryamedia.lazybuilder.world.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldExportOptionsTest {
    @Test
    void normalizesTypedGameRulesWithoutChangingTheirMeaning() {
        WorldExportOptions options = new WorldExportOptions(
                null, null, null, null, null, null, null,
                Map.of(" doDaylightCycle ", " false "),
                false);

        assertEquals(Map.of("doDaylightCycle", "false"), options.gameRules());
        assertTrue(options.hasWorldOverrides());
    }

    @Test
    void nullGameRulesNormalizeToAnEmptyTypedMap() {
        WorldExportOptions options = new WorldExportOptions(
                null, null, null, null, null, null, null,
                null,
                false);

        assertTrue(options.gameRules().isEmpty());
    }
}

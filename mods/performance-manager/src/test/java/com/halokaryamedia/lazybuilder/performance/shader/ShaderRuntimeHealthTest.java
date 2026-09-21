package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderRuntimeHealthTest {
    @Test
    void primaryErrorUsesStableSubsystemPriority() {
        ShaderRuntimeHealth health = new ShaderRuntimeHealth();
        health.preview("preview");
        health.terrain("terrain");
        health.catalog("catalog");

        assertEquals("catalog", health.primary());

        health.catalog("");
        assertEquals("terrain", health.primary());
    }

    @Test
    void clearResetsAllHealthCategories() {
        ShaderRuntimeHealth health = new ShaderRuntimeHealth();
        health.compile("compile");
        health.shadow("shadow");

        health.clear();

        assertTrue(health.primary().isBlank());
        assertTrue(health.snapshot().values().stream().allMatch(String::isBlank));
    }
}

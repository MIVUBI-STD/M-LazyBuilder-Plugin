package com.halokaryamedia.lazybuilder.performance.compatibility;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility.OptimizationDomain.ENTITY_CULLING;
import static com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility.OptimizationDomain.IMMEDIATE_RENDERING;
import static com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility.OptimizationDomain.MODEL_MEMORY;
import static com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility.OptimizationDomain.PARTICLES;
import static com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility.OptimizationDomain.TEXT_RENDERING;
import static com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility.OptimizationDomain.TERRAIN_BUILD;
import static com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility.OptimizationDomain.TERRAIN_SUBMISSION;
import static com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility.OptimizationDomain.TERRAIN_UPLOAD;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OptimizationCompatibilityTest {
    @Test
    void vanillaIndigoKeepsFirstPartyOwnership() {
        var renderer = new RendererCompatibility.Snapshot(false, false, List.of());
        var policy = OptimizationCompatibility.evaluate(renderer, false, false, false);

        for (var domain : OptimizationCompatibility.OptimizationDomain.values()) {
            assertTrue(policy.owns(domain), domain + " should remain first-party");
        }
    }

    @Test
    void externalOwnersOnlyDisableOverlappingDomains() {
        var renderer = new RendererCompatibility.Snapshot(true, false, List.of("sodium"));
        var policy = OptimizationCompatibility.evaluate(renderer, true, true, true);

        assertFalse(policy.owns(IMMEDIATE_RENDERING));
        assertFalse(policy.owns(TEXT_RENDERING));
        assertFalse(policy.owns(PARTICLES));
        assertFalse(policy.owns(ENTITY_CULLING));
        assertFalse(policy.owns(TERRAIN_BUILD));
        assertFalse(policy.owns(TERRAIN_UPLOAD));
        assertFalse(policy.owns(TERRAIN_SUBMISSION));
        assertFalse(policy.owns(MODEL_MEMORY));
    }

    @Test
    void irisOnlyBlocksShaderSensitiveTerrainSubmission() {
        var renderer = new RendererCompatibility.Snapshot(true, false, List.of());
        var policy = OptimizationCompatibility.evaluate(renderer, false, false, false);

        assertTrue(policy.owns(TERRAIN_BUILD));
        assertTrue(policy.owns(TERRAIN_UPLOAD));
        assertFalse(policy.owns(TERRAIN_SUBMISSION));
        assertTrue(policy.owns(IMMEDIATE_RENDERING));
        assertTrue(policy.owns(TEXT_RENDERING));
        assertTrue(policy.owns(PARTICLES));
        assertTrue(policy.owns(ENTITY_CULLING));
        assertTrue(policy.owns(MODEL_MEMORY));
    }

    @Test
    void compatibilityUncertaintyFailsClosedOnlyForTerrainOwnership() {
        var renderer = new RendererCompatibility.Snapshot(false, true, List.of());
        var policy = OptimizationCompatibility.evaluate(renderer, false, false, false);

        assertFalse(policy.owns(TERRAIN_BUILD));
        assertFalse(policy.owns(TERRAIN_UPLOAD));
        assertFalse(policy.owns(TERRAIN_SUBMISSION));
        assertTrue(policy.owns(IMMEDIATE_RENDERING));
        assertTrue(policy.owns(TEXT_RENDERING));
        assertTrue(policy.owns(PARTICLES));
        assertTrue(policy.owns(ENTITY_CULLING));
        assertTrue(policy.owns(MODEL_MEMORY));
    }

    @Test
    void entityCullingOnlyOwnsEntityCullingDomain() {
        var renderer = new RendererCompatibility.Snapshot(false, false, List.of());
        var policy = OptimizationCompatibility.evaluate(renderer, false, false, true);

        assertFalse(policy.owns(ENTITY_CULLING));
        assertTrue(policy.owns(TERRAIN_BUILD));
        assertTrue(policy.owns(TERRAIN_UPLOAD));
        assertTrue(policy.owns(TERRAIN_SUBMISSION));
        assertTrue(policy.owns(TEXT_RENDERING));
        assertTrue(policy.owns(MODEL_MEMORY));
    }
}

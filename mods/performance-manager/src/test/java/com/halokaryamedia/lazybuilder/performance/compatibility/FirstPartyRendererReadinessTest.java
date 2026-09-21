package com.halokaryamedia.lazybuilder.performance.compatibility;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FirstPartyRendererReadinessTest {
    @Test
    void standaloneMinecraftFabricPathIsFirstPartyReady() {
        var renderer = new RendererCompatibility.Snapshot(false, false, List.of());
        var policy = OptimizationCompatibility.evaluate(renderer, false, false, false);

        var readiness = FirstPartyRendererReadiness.evaluate(renderer, policy);

        assertTrue(readiness.ready());
        assertTrue(readiness.blockers().isEmpty());
    }

    @Test
    void irisBlocksStandaloneTerrainSubmissionUntilFirstPartyShaderRuntimeOwnsIt() {
        var renderer = new RendererCompatibility.Snapshot(true, false, List.of());
        var policy = OptimizationCompatibility.evaluate(renderer, false, false, false);

        var readiness = FirstPartyRendererReadiness.evaluate(renderer, policy);

        assertFalse(readiness.ready());
        assertTrue(readiness.blockers().stream().anyMatch(value -> value.contains("terrain_submission")));
    }

    @Test
    void declaredCustomRendererRemainsCompatibilityOwnerWhenInstalled() {
        var renderer = new RendererCompatibility.Snapshot(false, false, List.of("sodium"));
        var policy = OptimizationCompatibility.evaluate(renderer, false, false, false);

        var readiness = FirstPartyRendererReadiness.evaluate(renderer, policy);

        assertFalse(readiness.ready());
        assertTrue(readiness.blockers().stream().anyMatch(value -> value.contains("custom-renderer-owner")));
    }
}

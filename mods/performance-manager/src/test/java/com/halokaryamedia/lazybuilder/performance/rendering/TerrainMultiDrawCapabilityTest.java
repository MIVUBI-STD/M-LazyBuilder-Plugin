package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainMultiDrawCapabilityTest {
    @Test
    void vanillaPathStopsAtModelOffsetUntilShaderDrawDataExists() {
        var snapshot = TerrainMultiDrawCapability.evaluate(false, false, false, false);
        assertFalse(snapshot.ready());
        assertEquals("model-offset-uniform", snapshot.status());
    }

    @Test
    void rendererOwnershipAndIrisRemainHardGates() {
        assertEquals("compatibility-uncertain", TerrainMultiDrawCapability.evaluate(true, false, false, true).status());
        assertEquals("custom-renderer-owner", TerrainMultiDrawCapability.evaluate(false, true, false, true).status());
        assertEquals("iris-shader-owner", TerrainMultiDrawCapability.evaluate(false, false, true, true).status());
    }

    @Test
    void readyRequiresFirstPartyShaderDrawDataOwnership() {
        var snapshot = TerrainMultiDrawCapability.evaluate(false, false, false, true);
        assertTrue(snapshot.ready());
        assertEquals("ready", snapshot.status());
    }
}

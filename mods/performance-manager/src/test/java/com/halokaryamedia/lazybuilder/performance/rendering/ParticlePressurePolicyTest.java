package com.halokaryamedia.lazybuilder.performance.rendering;

import com.halokaryamedia.lazybuilder.performance.FramePressure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ParticlePressurePolicyTest {
    @Test
    void normalPressureNeverSuppressesParticles() {
        assertFalse(ParticlePressurePolicy.shouldSuppress(FramePressure.NORMAL, 1_000_000.0D));
    }

    @Test
    void elevatedAndHeavyOnlySuppressDistantParticles() {
        assertFalse(ParticlePressurePolicy.shouldSuppress(FramePressure.ELEVATED, 96.0D * 96.0D));
        assertTrue(ParticlePressurePolicy.shouldSuppress(FramePressure.ELEVATED, 97.0D * 97.0D));
        assertFalse(ParticlePressurePolicy.shouldSuppress(FramePressure.HEAVY, 64.0D * 64.0D));
        assertTrue(ParticlePressurePolicy.shouldSuppress(FramePressure.HEAVY, 65.0D * 65.0D));
    }

    @Test
    void invalidDistanceFailsOpen() {
        assertFalse(ParticlePressurePolicy.shouldSuppress(FramePressure.HEAVY, Double.NaN));
        assertFalse(ParticlePressurePolicy.shouldSuppress(FramePressure.HEAVY, -1.0D));
    }
}

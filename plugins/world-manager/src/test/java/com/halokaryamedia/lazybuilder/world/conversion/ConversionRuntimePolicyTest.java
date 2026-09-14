package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionRuntimePolicyTest {
    @Test
    void defaultsAreBoundedAndFailClosed() {
        ConversionRuntimePolicy policy = ConversionRuntimePolicy.defaults();

        assertEquals(ConversionRuntimePolicy.UpdateMode.AUTOMATIC_STABLE, policy.updateMode());
        assertEquals(Duration.ofHours(24), policy.minimumCheckInterval());
        assertTrue(policy.stableOnly());
        assertTrue(policy.checksumRequired());
        assertTrue(policy.compatibilityProbeRequired());
        assertTrue(policy.rollbackEnabled());
        assertEquals(2, policy.retainedVerifiedVersions());
        assertEquals(1, policy.maxConcurrentConversions());
    }

    @Test
    void rejectsUnboundedOrInvalidConcurrency() {
        assertThrows(IllegalArgumentException.class, () -> new ConversionRuntimePolicy(
                ConversionRuntimePolicy.UpdateMode.AUTOMATIC_STABLE,
                Duration.ofHours(24),
                true,
                true,
                true,
                true,
                2,
                0
        ));
    }
}

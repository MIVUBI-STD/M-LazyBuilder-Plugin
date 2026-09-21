package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GpuCapabilityProfileTest {
    @Test
    void choosesAdvancedTierOnlyWithRequiredCapabilities() {
        var advanced = new GpuCapabilityProfile.Snapshot(
                true, "vendor", "renderer", "4.6", true, true, true, 8L * 1024L * 1024L * 1024L
        );
        var arena = new GpuCapabilityProfile.Snapshot(
                true, "vendor", "renderer", "4.5", true, false, true, 0L
        );
        var safe = new GpuCapabilityProfile.Snapshot(
                true, "vendor", "renderer", "3.3", true, false, false, 0L
        );

        assertEquals("advanced", advanced.tier());
        assertEquals("arena", arena.tier());
        assertEquals("safe", safe.tier());
    }
}

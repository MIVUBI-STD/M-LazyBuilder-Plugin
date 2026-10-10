package com.halokaryamedia.lazybuilder.performance.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PerformanceMixinPolicyTest {
    private static final String PREFIX = "com.halokaryamedia.lazybuilder.performance.mixin.";

    @Test
    void unknownMixinCannotBypassCompatibilityPolicy() {
        PerformanceMixinPlugin plugin = new PerformanceMixinPlugin();
        // Must reject before calling FabricLoader or assuming any renderer owner.
        assertFalse(plugin.shouldApplyMixin("unused", PREFIX + "UnclassifiedFutureMixin"));
        assertFalse(plugin.shouldApplyMixin("unused", "different.package.ForeignMixin"));
        assertFalse(plugin.shouldApplyMixin("unused", null));
    }

    @Test
    void everyPackagedClientMixinHasAnExplicitOptimizationDomain() throws IOException {
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("lazybuilder-performance-manager.mixins.json")) {
            assertNotNull(stream, "packaged mixin list must exist");
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var array = Pattern.compile("\\\"client\\\"\\s*:\\s*\\[([^]]*)]", Pattern.DOTALL).matcher(json);
            assertTrue(array.find(), "mixin config must retain its client array");
            var entries = Pattern.compile("\\\"([A-Za-z0-9]+)\\\"").matcher(array.group(1));
            int count = 0;
            while (entries.find()) {
                String name = entries.group(1);
                assertNotNull(PerformanceMixinPlugin.domainFor(PREFIX + name),
                        "unknown optimization owner for registered mixin " + name);
                count++;
            }
            assertTrue(count >= 20, "unexpectedly empty/truncated client mixin list");
        }
    }
}

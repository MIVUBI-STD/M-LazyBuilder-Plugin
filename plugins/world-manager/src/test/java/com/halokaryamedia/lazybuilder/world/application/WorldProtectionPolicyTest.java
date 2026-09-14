package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldProtectionPolicyTest {
    @Test
    void configuredFallbackWinsOverPrimaryWorld() {
        AtomicReference<String> configured = new AtomicReference<>("Fallback");
        WorldProtectionPolicy policy = new WorldProtectionPolicy(configured::get, () -> "world");

        assertTrue(policy.test(world("Fallback")));
        assertFalse(policy.test(world("world")));
    }

    @Test
    void primaryWorldProtectsWhenNoFallbackIsConfigured() {
        AtomicReference<String> configured = new AtomicReference<>("   ");
        WorldProtectionPolicy policy = new WorldProtectionPolicy(configured::get, () -> "world");

        assertTrue(policy.test(world("world")));
        assertFalse(policy.test(world("Build")));
    }

    @Test
    void policyReadsRuntimeSuppliersInsteadOfPersistingProtectionState() {
        AtomicReference<String> configured = new AtomicReference<>("Fallback");
        AtomicReference<String> primary = new AtomicReference<>("world");
        WorldProtectionPolicy policy = new WorldProtectionPolicy(configured::get, primary::get);
        WorldRecord build = world("Build");

        assertFalse(policy.test(build));
        configured.set("Build");
        assertTrue(policy.test(build));
        configured.set("");
        primary.set("Build");
        assertTrue(policy.test(build));
    }

    private static WorldRecord world(String folder) {
        return new WorldRecord(
                WorldId.create(), folder, folder, WorldKind.IMPORTED,
                WorldLifecycle.ACTIVE
        );
    }
}

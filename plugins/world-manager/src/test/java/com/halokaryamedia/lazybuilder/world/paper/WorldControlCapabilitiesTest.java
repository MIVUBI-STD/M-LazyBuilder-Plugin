package com.halokaryamedia.lazybuilder.world.paper;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldControlCapabilitiesTest {
    @Test
    void advertised_capabilities_are_unique_and_namespaced() {
        var capabilities = WorldControlCapabilities.advertised();
        assertEquals(capabilities.size(), new HashSet<>(capabilities).size());
        assertTrue(capabilities.stream().allMatch(value -> value.startsWith("world.")));
        assertTrue(capabilities.contains("world.list"));
        assertTrue(capabilities.contains("world.tasks"));
    }
}

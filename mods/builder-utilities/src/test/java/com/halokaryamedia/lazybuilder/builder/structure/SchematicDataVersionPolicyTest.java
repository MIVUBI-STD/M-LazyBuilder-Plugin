package com.halokaryamedia.lazybuilder.builder.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchematicDataVersionPolicyTest {
    @Test
    void currentAndLegacyVersionsAreClassifiedWithoutPretendingToDatafix() {
        int current = SchematicDataVersionPolicy.currentDataVersion();
        assertEquals(
                SchematicDataVersionPolicy.Compatibility.CURRENT,
                SchematicDataVersionPolicy.classify(current));
        if (current > 0) {
            assertEquals(
                    SchematicDataVersionPolicy.Compatibility.LEGACY_REQUIRES_VALIDATION,
                    SchematicDataVersionPolicy.classify(current - 1));
        }
    }

    @Test
    void futureVersionFailsClosed() {
        int current = SchematicDataVersionPolicy.currentDataVersion();
        assertThrows(IllegalArgumentException.class, () ->
                SchematicDataVersionPolicy.requireNotFuture(Math.addExact(current, 1)));
    }
}

package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScopedOperationIdsTest {
    @Test
    void scopesOperationOnceAndRejectsCrossWorldReuse() {
        String scoped = ScopedOperationIds.scope("abc123", "op-1");
        assertEquals("abc123", ScopedOperationIds.scopeOf(scoped).orElseThrow());
        assertTrue(ScopedOperationIds.belongsTo(scoped, "abc123"));
        assertEquals(scoped, ScopedOperationIds.scope("abc123", scoped));
        assertThrows(IllegalArgumentException.class, () ->
                ScopedOperationIds.scope("other", scoped));
    }

    @Test
    void legacyUnscopedIdsDoNotBelongToAnyWorldScope() {
        assertTrue(ScopedOperationIds.scopeOf("legacy-op").isEmpty());
        assertFalse(ScopedOperationIds.belongsTo("legacy-op", "abc"));
    }
}

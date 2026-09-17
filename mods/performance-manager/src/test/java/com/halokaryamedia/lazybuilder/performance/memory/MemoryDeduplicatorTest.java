package com.halokaryamedia.lazybuilder.performance.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class MemoryDeduplicatorTest {
    @AfterEach
    void clearCache() {
        MemoryDeduplicator.clearCache();
    }

    @Test
    void identicalVertexArraysShareCanonicalStorage() {
        int[] first = {1, 2, 3, 4};
        int[] duplicate = {1, 2, 3, 4};

        int[] canonicalFirst = MemoryDeduplicator.canonicalize(first);
        int[] canonicalDuplicate = MemoryDeduplicator.canonicalize(duplicate);

        assertSame(first, canonicalFirst);
        assertSame(first, canonicalDuplicate);
        assertEquals(1, MemoryDeduplicator.cachedQuadCount());
    }

    @Test
    void differentVertexArraysRemainDistinct() {
        int[] first = {1, 2, 3, 4};
        int[] second = {1, 2, 3, 5};

        int[] canonicalFirst = MemoryDeduplicator.canonicalize(first);
        int[] canonicalSecond = MemoryDeduplicator.canonicalize(second);

        assertNotSame(canonicalFirst, canonicalSecond);
        assertArrayEquals(first, canonicalFirst);
        assertArrayEquals(second, canonicalSecond);
        assertEquals(2, MemoryDeduplicator.cachedQuadCount());
    }
}

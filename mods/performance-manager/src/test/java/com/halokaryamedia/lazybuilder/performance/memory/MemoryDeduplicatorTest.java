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
    void lookupIsBoundedAndStillReusesPreviouslyStoredQuadsWhenFull() {
        int[] first = {0, 42, 7, 99};
        assertSame(first, MemoryDeduplicator.canonicalize(first));
        // Unique arrays deliberately remain small, so the entry-count cap
        // is reached before the aggregate raw vertex-data budget.
        for (int index = 1; index < MemoryDeduplicator.MAX_UNIQUE_QUADS; index++) {
            MemoryDeduplicator.canonicalize(new int[]{index, 42, 7, 99});
        }
        assertEquals(MemoryDeduplicator.MAX_UNIQUE_QUADS,
                MemoryDeduplicator.cachedQuadCount());

        int[] newUnseen = {Integer.MIN_VALUE, 42, 7, 99};
        assertSame(newUnseen, MemoryDeduplicator.canonicalize(newUnseen));
        assertEquals(MemoryDeduplicator.MAX_UNIQUE_QUADS,
                MemoryDeduplicator.cachedQuadCount());
        assertSame(first, MemoryDeduplicator.canonicalize(new int[]{0, 42, 7, 99}));
    }

    @Test
    void oversizedModdedVertexArrayIsNotRetained() {
        int[] oversized = new int[1024];
        assertSame(oversized, MemoryDeduplicator.canonicalize(oversized));
        assertEquals(0, MemoryDeduplicator.cachedQuadCount());
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

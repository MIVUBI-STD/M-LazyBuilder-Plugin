package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalBlockPositionTest {
    @Test
    void roundTripsLocalCoordinatesAndSignedHeight() {
        for (int y : new int[]{Integer.MIN_VALUE, -64, 0, 319, Integer.MAX_VALUE}) {
            long packed = LocalBlockPosition.pack(15, y, 7);
            assertEquals(15, LocalBlockPosition.localX(packed));
            assertEquals(7, LocalBlockPosition.localZ(packed));
            assertEquals(y, LocalBlockPosition.y(packed));
        }
    }

    @Test
    void rejectsCoordinatesOutsideChunk() {
        assertThrows(IllegalArgumentException.class, () -> LocalBlockPosition.pack(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> LocalBlockPosition.pack(16, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> LocalBlockPosition.pack(0, 0, 16));
    }
}

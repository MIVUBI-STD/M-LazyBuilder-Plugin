package com.halokaryamedia.lazybuilder.builder.region;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PackedPointRegionTest {
    @Test
    void roundTripsNegativeCoordinatesAndDeduplicates() {
        long a = PackedWorldBlockPosition.pack(-1, -64, -17);
        long b = PackedWorldBlockPosition.pack(16, 319, 0);
        PackedPointRegion region = new PackedPointRegion(new long[]{b, a, a});

        assertEquals(2, region.size());
        assertTrue(region.contains(-1, -64, -17));
        assertTrue(region.contains(16, 319, 0));
        assertFalse(region.contains(0, 0, 0));
        assertEquals(-1, PackedWorldBlockPosition.x(a));
        assertEquals(-64, PackedWorldBlockPosition.y(a));
        assertEquals(-17, PackedWorldBlockPosition.z(a));
        assertEquals(2, region.workUnits().size());
    }
}

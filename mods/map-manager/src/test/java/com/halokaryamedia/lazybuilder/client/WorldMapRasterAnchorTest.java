package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldMapRasterAnchorTest {
    @Test
    void keepsSamplingCellStableUntilHalfCellBoundary() {
        var left = WorldMapRasterAnchor.resolve(7.9, 0.0, 16.0, 2.0);
        var right = WorldMapRasterAnchor.resolve(8.1, 0.0, 16.0, 2.0);

        assertEquals(0L, left.cellX());
        assertEquals(1L, right.cellX());
        assertEquals(0.0, left.centerX());
        assertEquals(16.0, right.centerX());
    }

    @Test
    void handlesNegativeCoordinatesSymmetrically() {
        var nearOrigin = WorldMapRasterAnchor.resolve(-7.9, -7.9, 16.0, 2.0);
        var nextNegative = WorldMapRasterAnchor.resolve(-8.1, -8.1, 16.0, 2.0);

        assertEquals(0L, nearOrigin.cellX());
        assertEquals(0L, nearOrigin.cellZ());
        assertEquals(-1L, nextNegative.cellX());
        assertEquals(-1L, nextNegative.cellZ());
    }

    @Test
    void convertsSubCellMovementIntoSmoothDrawOffset() {
        var anchor = WorldMapRasterAnchor.resolve(4.0, -6.0, 16.0, 2.0);

        assertEquals(-2, anchor.drawOffsetX());
        assertEquals(3, anchor.drawOffsetZ());
    }

    @Test
    void crossingBoundaryCompensatesTextureByOneCellWidth() {
        var before = WorldMapRasterAnchor.resolve(7.9, 0.0, 16.0, 2.0);
        var after = WorldMapRasterAnchor.resolve(8.1, 0.0, 16.0, 2.0);

        assertEquals(-4, before.drawOffsetX());
        assertEquals(4, after.drawOffsetX());
        assertEquals(8, after.drawOffsetX() - before.drawOffsetX());
    }

    @Test
    void drawOffsetRoundingStaysWithinHalfPixelOfExactPan() {
        double blocksPerPixel = 2.0;
        for (double center = -24.0; center <= 24.0; center += 0.25) {
            var anchor = WorldMapRasterAnchor.resolve(center, -center, 16.0, blocksPerPixel);
            double exactX = (anchor.centerX() - center) / blocksPerPixel;
            double exactZ = (anchor.centerZ() + center) / blocksPerPixel;

            assertTrue(Math.abs(anchor.drawOffsetX() - exactX) <= 0.5d);
            assertTrue(Math.abs(anchor.drawOffsetZ() - exactZ) <= 0.5d);
        }
    }

    @Test
    void rejectsInvalidScaleInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> WorldMapRasterAnchor.resolve(0.0, 0.0, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> WorldMapRasterAnchor.resolve(0.0, 0.0, 1.0, Double.NaN));
    }
}

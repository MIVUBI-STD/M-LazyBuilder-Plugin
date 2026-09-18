package com.halokaryamedia.lazybuilder.client;

/**
 * Computes a stable sampling anchor for the world-map raster while preserving smooth visual panning.
 *
 * <p>The raster is rebuilt only when the viewport center crosses a sampling-cell boundary. Movement
 * inside the active cell is represented as a draw offset, so dragging remains pixel-smooth without
 * forcing a full terrain resample on every mouse delta.</p>
 */
final class WorldMapRasterAnchor {
    private WorldMapRasterAnchor() {
    }

    static Anchor resolve(double centerX, double centerZ, double blocksPerCell, double blocksPerPixel) {
        requirePositiveFinite(blocksPerCell, "blocksPerCell");
        requirePositiveFinite(blocksPerPixel, "blocksPerPixel");
        requireFinite(centerX, "centerX");
        requireFinite(centerZ, "centerZ");

        long cellX = nearestCell(centerX, blocksPerCell);
        long cellZ = nearestCell(centerZ, blocksPerCell);
        double anchorX = cellX * blocksPerCell;
        double anchorZ = cellZ * blocksPerCell;
        int drawOffsetX = safePixelOffset((anchorX - centerX) / blocksPerPixel);
        int drawOffsetZ = safePixelOffset((anchorZ - centerZ) / blocksPerPixel);
        return new Anchor(cellX, cellZ, anchorX, anchorZ, drawOffsetX, drawOffsetZ);
    }

    private static long nearestCell(double coordinate, double blocksPerCell) {
        double scaled = coordinate / blocksPerCell;
        if (scaled <= Long.MIN_VALUE || scaled >= Long.MAX_VALUE) {
            throw new IllegalArgumentException("map center exceeds supported raster coordinate range");
        }
        return (long) Math.floor(scaled + 0.5d);
    }

    private static int safePixelOffset(double offset) {
        if (offset <= Integer.MIN_VALUE || offset >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("raster draw offset exceeds supported pixel range");
        }
        return (int) Math.round(offset);
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0d) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    record Anchor(
            long cellX,
            long cellZ,
            double centerX,
            double centerZ,
            int drawOffsetX,
            int drawOffsetZ
    ) {
    }
}

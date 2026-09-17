package com.halokaryamedia.lazybuilder.client;

import java.util.Objects;

/**
 * Stable raster viewport identity for {@link WorldMapScreen}.
 *
 * <p>The visible map center may move continuously while the sampling key remains stable inside the
 * current raster cell. A key change means the sampled texture must be rebuilt; otherwise the
 * existing texture can be shifted by the returned draw offsets.</p>
 */
final class WorldMapRasterViewport {
    private WorldMapRasterViewport() {
    }

    static State resolve(
            String scope,
            int left,
            int top,
            int right,
            int bottom,
            int pixel,
            double zoom,
            double centerX,
            double centerZ
    ) {
        Objects.requireNonNull(scope, "scope");
        if (right <= left || bottom <= top) throw new IllegalArgumentException("map bounds must be non-empty");
        if (pixel <= 0) throw new IllegalArgumentException("pixel must be positive");
        if (!Double.isFinite(zoom) || zoom <= 0.0d) throw new IllegalArgumentException("zoom must be positive and finite");

        double blocksPerPixel = zoom / 2.0d;
        double blocksPerCell = blocksPerPixel * pixel;
        WorldMapRasterAnchor.Anchor anchor = WorldMapRasterAnchor.resolve(
                centerX, centerZ, blocksPerCell, blocksPerPixel);
        Key key = new Key(
                scope,
                left,
                top,
                right,
                bottom,
                pixel,
                Double.doubleToLongBits(zoom),
                anchor.cellX(),
                anchor.cellZ());
        return new State(
                key,
                anchor.centerX(),
                anchor.centerZ(),
                anchor.drawOffsetX(),
                anchor.drawOffsetZ(),
                blocksPerCell);
    }

    record Key(
            String scope,
            int left,
            int top,
            int right,
            int bottom,
            int pixel,
            long zoomBits,
            long cellX,
            long cellZ
    ) {
        Key {
            Objects.requireNonNull(scope, "scope");
        }
    }

    record State(
            Key key,
            double sampleCenterX,
            double sampleCenterZ,
            int drawOffsetX,
            int drawOffsetZ,
            double blocksPerCell
    ) {
        State {
            Objects.requireNonNull(key, "key");
        }
    }
}

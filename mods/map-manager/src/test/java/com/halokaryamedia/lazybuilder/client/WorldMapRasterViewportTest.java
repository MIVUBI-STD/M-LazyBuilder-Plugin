package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldMapRasterViewportTest {
    @Test
    void smallPanKeepsSamplingKeyAndChangesOnlyDrawOffset() {
        var first = WorldMapRasterViewport.resolve("world|overworld", 100, 0, 900, 600, 2, 8.0, 100.0, 200.0);
        var moved = WorldMapRasterViewport.resolve("world|overworld", 100, 0, 900, 600, 2, 8.0, 103.0, 197.0);

        assertEquals(first.key(), moved.key());
        assertNotEquals(first.drawOffsetX(), moved.drawOffsetX());
        assertNotEquals(first.drawOffsetZ(), moved.drawOffsetZ());
    }

    @Test
    void crossingSamplingCellChangesKey() {
        var first = WorldMapRasterViewport.resolve("world|overworld", 0, 0, 800, 600, 2, 8.0, 3.9, 0.0);
        var crossed = WorldMapRasterViewport.resolve("world|overworld", 0, 0, 800, 600, 2, 8.0, 4.1, 0.0);

        assertNotEquals(first.key(), crossed.key());
    }

    @Test
    void negativeCoordinatesUseStableNearestCell() {
        var first = WorldMapRasterViewport.resolve("world|overworld", 0, 0, 800, 600, 1, 4.0, -3.9, -6.1);
        var moved = WorldMapRasterViewport.resolve("world|overworld", 0, 0, 800, 600, 1, 4.0, -4.1, -5.9);

        assertEquals(first.key(), moved.key());
    }

    @Test
    void zoomBoundsPixelAndScopeRemainPartOfRefreshIdentity() {
        var base = WorldMapRasterViewport.resolve("a|overworld", 0, 0, 800, 600, 2, 8.0, 100.0, 100.0);

        assertNotEquals(base.key(), WorldMapRasterViewport.resolve("b|overworld", 0, 0, 800, 600, 2, 8.0, 100.0, 100.0).key());
        assertNotEquals(base.key(), WorldMapRasterViewport.resolve("a|overworld", 1, 0, 800, 600, 2, 8.0, 100.0, 100.0).key());
        assertNotEquals(base.key(), WorldMapRasterViewport.resolve("a|overworld", 0, 0, 800, 600, 3, 8.0, 100.0, 100.0).key());
        assertNotEquals(base.key(), WorldMapRasterViewport.resolve("a|overworld", 0, 0, 800, 600, 2, 8.1, 100.0, 100.0).key());
    }

    @Test
    void rasterViewportAndTerrainScopeRemainIsolatedAcrossServers() {
        String worldAndDimension = "01234567-89ab-cdef-0123-456789abcdef|minecraft:overworld";
        String serverA = ClientServerIdentity.mapScope(
                ClientServerIdentity.encode("a.example:25565"), worldAndDimension);
        String serverB = ClientServerIdentity.mapScope(
                ClientServerIdentity.encode("b.example:25565"), worldAndDimension);

        // These are the same canonical identities used by ClientMapSurfaceCache.
        // A retained raster must not be reusable for a different server.
        assertNotEquals(serverA, serverB);
        var rasterA = WorldMapRasterViewport.resolve(
                serverA, 0, 0, 800, 600, 2, 8.0, 100.0, 200.0);
        var rasterB = WorldMapRasterViewport.resolve(
                serverB, 0, 0, 800, 600, 2, 8.0, 100.0, 200.0);
        assertNotEquals(rasterA.key(), rasterB.key());

        MapRasterPresentationState retained = new MapRasterPresentationState();
        retained.scope = serverA;
        retained.colors = new int[] {0xFF112233};
        retained.columns = 1;
        retained.rows = 1;
        retained.contentDirty = false;
        if (!serverB.equals(retained.scope) && !retained.scope.isBlank()) {
            retained.resetContent();
        }
        org.junit.jupiter.api.Assertions.assertFalse(retained.hasImage());
        org.junit.jupiter.api.Assertions.assertTrue(retained.contentDirty);
    }

    @Test
    void rejectsInvalidViewportInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> WorldMapRasterViewport.resolve("scope", 0, 0, 0, 10, 1, 2.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> WorldMapRasterViewport.resolve("scope", 0, 0, 10, 10, 0, 2.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> WorldMapRasterViewport.resolve("scope", 0, 0, 10, 10, 1, Double.NaN, 0.0, 0.0));
    }
}

package com.halokaryamedia.lazybuilder.client;

/**
 * Mutable camera state for the world map.
 *
 * <p>This object owns only presentation coordinates. It deliberately has no
 * rendering, networking, persistence, or Minecraft lifecycle responsibilities.</p>
 */
final class MapViewportState {
    double centerX;
    double centerZ;
    double zoom = 2.0;
    boolean dragging;

    double blocksPerPixel() {
        return zoom / 2.0;
    }

    void panByPixels(double deltaX, double deltaY) {
        double blocksPerPixel = blocksPerPixel();
        centerX -= deltaX * blocksPerPixel;
        centerZ -= deltaY * blocksPerPixel;
    }

    void centerOn(double blockX, double blockZ) {
        centerX = blockX;
        centerZ = blockZ;
    }

    double[] worldAtScreen(double screenX, double screenY, int mapCenterX, int mapCenterY) {
        double blocksPerPixel = blocksPerPixel();
        return new double[] {
                centerX + (screenX - mapCenterX) * blocksPerPixel,
                centerZ + (screenY - mapCenterY) * blocksPerPixel
        };
    }
}

package com.halokaryamedia.lazybuilder.client;

/**
 * Mutable presentation snapshot for the map raster.
 *
 * <p>Sampling and rendering stay with their existing owners. This state object
 * only groups the cached raster image, viewport fingerprint, refresh marker,
 * and GPU texture lifecycle that previously lived as unrelated screen fields.</p>
 */
final class MapRasterPresentationState {
    int[] colors = new int[0];
    int columns;
    int rows;
    int halfCellsX;
    int halfCellsZ;
    int pixel = -1;
    int left;
    int top;
    int right;
    int bottom;
    double centerX = Double.NaN;
    double centerZ = Double.NaN;
    double zoom = Double.NaN;
    String scope = "";
    boolean contentDirty = true;
    long worldTime = Long.MIN_VALUE;
    ClientMapRasterTexture texture;

    void resetContent() {
        colors = new int[0];
        columns = 0;
        rows = 0;
        contentDirty = true;
        worldTime = Long.MIN_VALUE;
    }

    void invalidateLayout() {
        left = Integer.MIN_VALUE;
        right = Integer.MIN_VALUE;
        top = Integer.MIN_VALUE;
        bottom = Integer.MIN_VALUE;
    }

    boolean hasImage() {
        return columns > 0 && rows > 0 && colors.length == columns * rows;
    }

    void closeTexture() {
        if (texture == null) return;
        texture.close();
        texture = null;
    }
}

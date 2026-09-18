package com.halokaryamedia.lazybuilder.client;

import java.util.UUID;

/**
 * Chunk-aligned export selection state.
 *
 * <p>The screen remains responsible for interaction and rendering. This class
 * owns only selection identity and bounds so map lifecycle code no longer
 * stores those values as unrelated top-level fields.</p>
 */
final class MapAreaSelectionState {
    enum DragMode { NONE, MOVE, N, NE, E, SE, S, SW, W, NW }

    boolean active;
    UUID worldId;
    int minChunkX;
    int maxChunkX;
    int minChunkZ;
    int maxChunkZ;

    private DragMode dragMode = DragMode.NONE;
    private int dragStartChunkX;
    private int dragStartChunkZ;
    private int dragMinChunkX;
    private int dragMaxChunkX;
    private int dragMinChunkZ;
    private int dragMaxChunkZ;

    boolean ownsWorld(UUID candidateWorldId) {
        return worldId != null && worldId.equals(candidateWorldId);
    }

    void activate(UUID worldId, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        this.worldId = worldId;
        this.minChunkX = Math.min(minChunkX, maxChunkX);
        this.maxChunkX = Math.max(minChunkX, maxChunkX);
        this.minChunkZ = Math.min(minChunkZ, maxChunkZ);
        this.maxChunkZ = Math.max(minChunkZ, maxChunkZ);
        this.active = true;
    }

    void clear() {
        active = false;
        worldId = null;
        endDrag();
    }

    DragMode dragMode() {
        return dragMode;
    }

    boolean dragging() {
        return dragMode != DragMode.NONE;
    }

    void beginDrag(DragMode mode, int chunkX, int chunkZ) {
        dragMode = mode;
        dragStartChunkX = chunkX;
        dragStartChunkZ = chunkZ;
        dragMinChunkX = minChunkX;
        dragMaxChunkX = maxChunkX;
        dragMinChunkZ = minChunkZ;
        dragMaxChunkZ = maxChunkZ;
    }

    void updateDrag(int chunkX, int chunkZ) {
        if (dragMode == DragMode.MOVE) {
            int dx = chunkX - dragStartChunkX;
            int dz = chunkZ - dragStartChunkZ;
            minChunkX = dragMinChunkX + dx;
            maxChunkX = dragMaxChunkX + dx;
            minChunkZ = dragMinChunkZ + dz;
            maxChunkZ = dragMaxChunkZ + dz;
            return;
        }

        int x1 = dragMinChunkX;
        int x2 = dragMaxChunkX;
        int z1 = dragMinChunkZ;
        int z2 = dragMaxChunkZ;
        switch (dragMode) {
            case NW -> { x1 = chunkX; z1 = chunkZ; }
            case N -> z1 = chunkZ;
            case NE -> { x2 = chunkX; z1 = chunkZ; }
            case E -> x2 = chunkX;
            case SE -> { x2 = chunkX; z2 = chunkZ; }
            case S -> z2 = chunkZ;
            case SW -> { x1 = chunkX; z2 = chunkZ; }
            case W -> x1 = chunkX;
            default -> { return; }
        }
        minChunkX = Math.min(x1, x2);
        maxChunkX = Math.max(x1, x2);
        minChunkZ = Math.min(z1, z2);
        maxChunkZ = Math.max(z1, z2);
    }

    void endDrag() {
        dragMode = DragMode.NONE;
    }

    int minBlockX(int chunkBlocks) {
        return minChunkX * chunkBlocks;
    }

    int maxBlockX(int chunkBlocks) {
        return maxChunkX * chunkBlocks + chunkBlocks - 1;
    }

    int minBlockZ(int chunkBlocks) {
        return minChunkZ * chunkBlocks;
    }

    int maxBlockZ(int chunkBlocks) {
        return maxChunkZ * chunkBlocks + chunkBlocks - 1;
    }
}

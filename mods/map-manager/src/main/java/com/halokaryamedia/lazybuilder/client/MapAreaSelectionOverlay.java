package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.client.MapAreaSelectionGeometry.Handle;
import com.halokaryamedia.lazybuilder.client.MapAreaSelectionGeometry.SelectionRect;
import com.halokaryamedia.lazybuilder.client.MapAreaSelectionState.DragMode;
import net.minecraft.client.gui.DrawContext;

/** Presentation-only renderer and hit tester for the map area-selection overlay. */
final class MapAreaSelectionOverlay {
    private static final int CHUNK_BLOCKS = 16;
    private static final int REGION_BLOCKS = 512;
    private static final int HANDLE_RADIUS = 5;
    private static final int CHUNK_GRID_COLOR = 0x2EFFFFFF;
    private static final int REGION_GRID_COLOR = 0x667F8FA3;
    private static final int OUTSIDE_SELECTION_DIM = 0x48101418;

    void render(
            DrawContext context,
            MapAreaSelectionState selection,
            Viewport viewport,
            int mouseX,
            int mouseY
    ) {
        if (!selection.active) return;
        renderGrid(context, viewport);
        renderSelection(context, selection, viewport, mouseX, mouseY);
    }

    DragMode hit(
            MapAreaSelectionState selection,
            Viewport viewport,
            double mouseX,
            double mouseY
    ) {
        if (!selection.active) return DragMode.NONE;
        SelectionRect rect = selectionRect(selection, viewport);
        return MapAreaSelectionGeometry.hit(rect, mouseX, mouseY, HANDLE_RADIUS);
    }

    private void renderGrid(DrawContext context, Viewport viewport) {
        double bpp = viewport.blocksPerPixel();
        double chunkPixels = CHUNK_BLOCKS / bpp;
        double regionPixels = REGION_BLOCKS / bpp;
        double worldLeft = viewport.centerBlockX()
                + (viewport.left() - viewport.centerX()) * bpp;
        double worldRight = viewport.centerBlockX()
                + (viewport.right() - viewport.centerX()) * bpp;
        double worldTop = viewport.centerBlockZ()
                + (viewport.top() - viewport.centerY()) * bpp;
        double worldBottom = viewport.centerBlockZ()
                + (viewport.bottom() - viewport.centerY()) * bpp;

        if (chunkPixels >= 6.0) {
            int firstChunkX = Math.floorDiv((int) Math.floor(worldLeft), CHUNK_BLOCKS) - 1;
            int lastChunkX = Math.floorDiv((int) Math.ceil(worldRight), CHUNK_BLOCKS) + 1;
            for (int chunkX = firstChunkX; chunkX <= lastChunkX; chunkX++) {
                int x = worldToScreenX(chunkX * CHUNK_BLOCKS, viewport);
                if (x >= viewport.left() && x < viewport.right()) {
                    context.fill(x, viewport.top(), x + 1, viewport.bottom(), CHUNK_GRID_COLOR);
                }
            }

            int firstChunkZ = Math.floorDiv((int) Math.floor(worldTop), CHUNK_BLOCKS) - 1;
            int lastChunkZ = Math.floorDiv((int) Math.ceil(worldBottom), CHUNK_BLOCKS) + 1;
            for (int chunkZ = firstChunkZ; chunkZ <= lastChunkZ; chunkZ++) {
                int y = worldToScreenZ(chunkZ * CHUNK_BLOCKS, viewport);
                if (y >= viewport.top() && y < viewport.bottom()) {
                    context.fill(viewport.left(), y, viewport.right(), y + 1, CHUNK_GRID_COLOR);
                }
            }
        }

        if (regionPixels >= 8.0) {
            int firstRegionX = Math.floorDiv((int) Math.floor(worldLeft), REGION_BLOCKS) - 1;
            int lastRegionX = Math.floorDiv((int) Math.ceil(worldRight), REGION_BLOCKS) + 1;
            for (int regionX = firstRegionX; regionX <= lastRegionX; regionX++) {
                int x = worldToScreenX(regionX * REGION_BLOCKS, viewport);
                if (x >= viewport.left() && x < viewport.right()) {
                    context.fill(x, viewport.top(), x + 2, viewport.bottom(), REGION_GRID_COLOR);
                }
            }

            int firstRegionZ = Math.floorDiv((int) Math.floor(worldTop), REGION_BLOCKS) - 1;
            int lastRegionZ = Math.floorDiv((int) Math.ceil(worldBottom), REGION_BLOCKS) + 1;
            for (int regionZ = firstRegionZ; regionZ <= lastRegionZ; regionZ++) {
                int y = worldToScreenZ(regionZ * REGION_BLOCKS, viewport);
                if (y >= viewport.top() && y < viewport.bottom()) {
                    context.fill(viewport.left(), y, viewport.right(), y + 2, REGION_GRID_COLOR);
                }
            }
        }
    }

    private void renderSelection(
            DrawContext context,
            MapAreaSelectionState selection,
            Viewport viewport,
            int mouseX,
            int mouseY
    ) {
        SelectionRect rect = selectionRect(selection, viewport);
        int left = Math.max(viewport.left(), rect.left());
        int right = Math.min(viewport.right(), rect.right());
        int top = Math.max(viewport.top(), rect.top());
        int bottom = Math.min(viewport.bottom(), rect.bottom());
        if (right <= left || bottom <= top) return;

        if (top > viewport.top()) {
            context.fill(viewport.left(), viewport.top(), viewport.right(), top, OUTSIDE_SELECTION_DIM);
        }
        if (bottom < viewport.bottom()) {
            context.fill(viewport.left(), bottom, viewport.right(), viewport.bottom(), OUTSIDE_SELECTION_DIM);
        }
        if (left > viewport.left()) {
            context.fill(viewport.left(), top, left, bottom, OUTSIDE_SELECTION_DIM);
        }
        if (right < viewport.right()) {
            context.fill(right, top, viewport.right(), bottom, OUTSIDE_SELECTION_DIM);
        }

        context.fill(left, top, right, top + 2, LbUi.ACCENT_BRIGHT);
        context.fill(left, bottom - 2, right, bottom, LbUi.ACCENT_BRIGHT);
        context.fill(left, top, left + 2, bottom, LbUi.ACCENT_BRIGHT);
        context.fill(right - 2, top, right, bottom, LbUi.ACCENT_BRIGHT);

        DragMode hover = MapAreaSelectionGeometry.hit(rect, mouseX, mouseY, HANDLE_RADIUS);
        for (Handle handle : MapAreaSelectionGeometry.handles(rect)) {
            boolean active = handle.mode() == hover || handle.mode() == selection.dragMode();
            int radius = active ? HANDLE_RADIUS + 1 : HANDLE_RADIUS;
            int color = active ? LbUi.TEXT_PRIMARY : LbUi.ACCENT_BRIGHT;
            context.fill(
                    handle.x() - radius,
                    handle.y() - radius,
                    handle.x() + radius + 1,
                    handle.y() + radius + 1,
                    0xAA10151C);
            context.fill(
                    handle.x() - radius + 2,
                    handle.y() - radius + 2,
                    handle.x() + radius - 1,
                    handle.y() + radius - 1,
                    color);
        }
    }

    private SelectionRect selectionRect(
            MapAreaSelectionState selection,
            Viewport viewport
    ) {
        int left = worldToScreenX(selection.minBlockX(CHUNK_BLOCKS), viewport);
        int right = worldToScreenX((selection.maxChunkX + 1) * CHUNK_BLOCKS, viewport);
        int top = worldToScreenZ(selection.minBlockZ(CHUNK_BLOCKS), viewport);
        int bottom = worldToScreenZ((selection.maxChunkZ + 1) * CHUNK_BLOCKS, viewport);
        return MapAreaSelectionGeometry.rect(left, top, right, bottom);
    }

    private static int worldToScreenX(int blockX, Viewport viewport) {
        return viewport.centerX()
                + (int) Math.round((blockX - viewport.centerBlockX()) / viewport.blocksPerPixel());
    }

    private static int worldToScreenZ(int blockZ, Viewport viewport) {
        return viewport.centerY()
                + (int) Math.round((blockZ - viewport.centerBlockZ()) / viewport.blocksPerPixel());
    }

    record Viewport(
            int left,
            int top,
            int right,
            int bottom,
            int centerX,
            int centerY,
            double centerBlockX,
            double centerBlockZ,
            double blocksPerPixel
    ) {}
}

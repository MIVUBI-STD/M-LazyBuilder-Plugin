package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Map-first LazyBuilder surface. Interaction intentionally follows the mature
 * fullscreen-map model: direct pan, cursor-anchored smooth zoom, persistent
 * explored terrain, contextual right-click actions and explicit area selection.
 */
public final class WorldMapScreen extends Screen {
    private static final int TOP_BAR = 32;
    private static final int BOTTOM_BAR = 30;
    private static final int MAP_MARGIN = 8;
    private static final int CELL = 4;
    private static final int SAMPLE_BUDGET_PER_FRAME = 4096;
    private static final int[] ZOOM_STEPS = {1, 2, 4, 8, 16, 32, 64};
    private static final ClientMapSurfaceCache SURFACE = new ClientMapSurfaceCache();

    private final ClientWorldController worlds;
    private final ClientTransferController transfers;
    private final ClientMapController maps;

    private double centerX;
    private double centerZ;
    private int zoomIndex = 1;
    private double animatedZoom = ZOOM_STEPS[zoomIndex];
    private double targetZoom = ZOOM_STEPS[zoomIndex];
    private boolean centeredOnce;
    private boolean dragging;

    private boolean zoomAnchored;
    private double zoomAnchorWorldX;
    private double zoomAnchorWorldZ;
    private double zoomAnchorScreenX;
    private double zoomAnchorScreenY;

    private boolean areaMode;
    private Integer areaX1;
    private Integer areaZ1;
    private Integer areaX2;
    private Integer areaZ2;

    private boolean contextOpen;
    private int contextBlockX;
    private int contextBlockZ;
    private int contextScreenX;
    private int contextScreenY;

    public WorldMapScreen(
            ClientWorldController worlds,
            ClientTransferController transfers,
            ClientMapController maps
    ) {
        super(Text.literal("LazyBuilder World Map"));
        this.worlds = worlds;
        this.transfers = transfers;
        this.maps = maps;
    }

    @Override
    protected void init() {
        if (!centeredOnce) {
            centerOnPlayer();
            centeredOnce = true;
        }
        maps.refreshCurrentWorld();

        addDrawableChild(ButtonWidget.builder(Text.literal("Worlds"), button -> {
            if (client != null) client.setScreen(new WorldManagerScreen(worlds, transfers, maps));
        }).dimensions(10, height - 24, 70, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("−"), button -> stepZoom(1, width - 110, 20))
                .dimensions(width - 110, 7, 20, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> stepZoom(-1, width - 86, 20))
                .dimensions(width - 86, 7, 20, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Recenter"), button -> centerOnPlayer())
                .dimensions(width - 160, height - 24, 72, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                .dimensions(width - 82, height - 24, 72, 18).build());

        if (contextOpen) addContextButtons();
    }

    private void addContextButtons() {
        int panelX = Math.max(8, Math.min(width - 150, contextScreenX));
        int panelY = Math.max(TOP_BAR + 4, Math.min(height - BOTTOM_BAR - 78, contextScreenY));

        if (selectionReady()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Export Selection"), button -> {
                maps.exportAreaCurrent(areaX1, areaZ1, areaX2, areaZ2,
                        "JAVA_1_21_4", "area-" + System.currentTimeMillis());
                clearAreaSelection();
                clearAndInit();
            }).dimensions(panelX, panelY, 138, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel Selection"), button -> {
                clearAreaSelection();
                clearAndInit();
            }).dimensions(panelX, panelY + 20, 138, 18).build());
            return;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Teleport Here"), button -> {
            maps.teleportCurrent(contextBlockX, contextBlockZ);
            contextOpen = false;
            clearAndInit();
        }).dimensions(panelX, panelY, 138, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Export Area"), button -> {
            areaMode = true;
            areaX1 = null;
            areaZ1 = null;
            areaX2 = null;
            areaZ2 = null;
            contextOpen = false;
            clearAndInit();
        }).dimensions(panelX, panelY + 20, 138, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            contextOpen = false;
            clearAndInit();
        }).dimensions(panelX, panelY + 40, 138, 18).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateZoomAnimation();
        context.fill(0, 0, width, height, 0xFF0C0F13);
        renderMap(context);
        renderTopBar(context, mouseX, mouseY);
        renderSelection(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderMap(DrawContext context) {
        ClientWorld world = client == null ? null : client.world;
        if (world == null) return;

        String managedWorld = maps.currentWorld() == null ? "unmanaged" : maps.currentWorld().worldId().toString();
        String dimension = world.getRegistryKey().getValue().toString();
        Path storage = client == null ? null : client.runDirectory.toPath().resolve("lazybuilder").resolve("maps");
        SURFACE.useScope(managedWorld + "|" + dimension, storage);
        SURFACE.processPending(world, SAMPLE_BUDGET_PER_FRAME);

        Bounds bounds = mapBounds();
        context.fill(bounds.left, bounds.top, bounds.right, bounds.bottom, 0xFF151A20);

        double blocksPerCell = zoom();
        int halfCellsX = Math.max(1, bounds.width() / CELL / 2);
        int halfCellsZ = Math.max(1, bounds.height() / CELL / 2);
        double originCellX = centerX / blocksPerCell;
        double originCellZ = centerZ / blocksPerCell;

        for (int cz = -halfCellsZ - 1; cz <= halfCellsZ + 1; cz++) {
            int screenY = bounds.centerY() + cz * CELL;
            if (screenY + CELL < bounds.top || screenY >= bounds.bottom) continue;
            for (int cx = -halfCellsX - 1; cx <= halfCellsX + 1; cx++) {
                int screenX = bounds.centerX() + cx * CELL;
                if (screenX + CELL < bounds.left || screenX >= bounds.right) continue;

                int blockX = (int) Math.floor((originCellX + cx) * blocksPerCell);
                int blockZ = (int) Math.floor((originCellZ + cz) * blocksPerCell);
                ClientMapSurfaceCache.SurfaceSample sample = SURFACE.sample(world, blockX, blockZ);
                context.fill(screenX, screenY, screenX + CELL, screenY + CELL, sample.color());
            }
        }

        renderPlayerMarker(context, bounds);
    }

    private void renderPlayerMarker(DrawContext context, Bounds bounds) {
        if (client == null || client.player == null) return;
        double scale = CELL / zoom();
        int px = bounds.centerX() + (int) Math.round((client.player.getX() - centerX) * scale);
        int pz = bounds.centerY() + (int) Math.round((client.player.getZ() - centerZ) * scale);
        if (!bounds.contains(px, pz)) return;

        double angle = Math.toRadians(client.player.getYaw());
        int dx = (int) Math.round(-Math.sin(angle) * 6.0);
        int dz = (int) Math.round(Math.cos(angle) * 6.0);
        context.fill(px - 3, pz - 3, px + 4, pz + 4, 0xCC000000);
        context.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFFFFFFFF);
        context.fill(px + Math.min(0, dx), pz + Math.min(0, dz),
                px + Math.max(1, dx + 1), pz + Math.max(1, dz + 1), 0xFFFF4A4A);
    }

    private void renderTopBar(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, width, TOP_BAR, 0xE6171B21);
        context.fill(0, height - BOTTOM_BAR, width, height, 0xE6171B21);

        String worldName = maps.currentWorld() == null ? "Current World" : maps.currentWorld().displayName();
        String dimension = client == null || client.world == null
                ? ""
                : client.world.getRegistryKey().getValue().getPath();
        context.drawTextWithShadow(textRenderer,
                Text.literal(dimension.isBlank() ? worldName : worldName + "  •  " + dimension),
                10, 11, 0xFFFFFF);

        int[] hovered = screenToWorld(mouseX, mouseY);
        String zoomText = String.format(Locale.ROOT, "%.1f", zoom());
        String coords = hovered == null
                ? "Zoom 1:" + zoomText
                : "X " + hovered[0] + "  Z " + hovered[1] + "   Zoom 1:" + zoomText;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(coords), width / 2, 11, 0xD8DEE9);

        if (selectionReady()) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Export Area selected — confirm from the action menu"),
                    90, height - 19, 0xFFD166);
        } else if (areaMode) {
            String status = areaX1 == null
                    ? "Export Area: click first corner"
                    : "Export Area: click second corner";
            context.drawTextWithShadow(textRenderer, Text.literal(status), 90, height - 19, 0xFFD166);
        } else {
            String loading = SURFACE.pendingCount() > 0
                    ? "  •  Mapping " + SURFACE.pendingCount() + " columns"
                    : "";
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Drag to pan  •  Wheel to zoom  •  Right-click for actions" + loading),
                    90, height - 19, 0xAEB7C4);
        }
    }

    private void renderSelection(DrawContext context) {
        if (!areaMode || areaX1 == null || areaZ1 == null) return;
        Bounds bounds = mapBounds();
        int x2 = areaX2 == null ? areaX1 : areaX2;
        int z2 = areaZ2 == null ? areaZ1 : areaZ2;

        int sx1 = worldToScreenX(areaX1, bounds);
        int sy1 = worldToScreenZ(areaZ1, bounds);
        int sx2 = worldToScreenX(x2, bounds);
        int sy2 = worldToScreenZ(z2, bounds);

        int left = Math.max(bounds.left, Math.min(sx1, sx2));
        int right = Math.min(bounds.right, Math.max(sx1, sx2));
        int top = Math.max(bounds.top, Math.min(sy1, sy2));
        int bottom = Math.min(bounds.bottom, Math.max(sy1, sy2));
        if (right <= left || bottom <= top) return;

        context.fill(left, top, right, bottom, 0x33FFD166);
        context.fill(left, top, right, top + 2, 0xFFFFD166);
        context.fill(left, bottom - 2, right, bottom, 0xFFFFD166);
        context.fill(left, top, left + 2, bottom, 0xFFFFD166);
        context.fill(right - 2, top, right, bottom, 0xFFFFD166);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!mapBounds().contains(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);

        if (button == 1) {
            if (areaMode && areaX1 != null && areaX2 == null) return true;
            int[] world = screenToWorld(mouseX, mouseY);
            if (world == null) return true;
            contextBlockX = world[0];
            contextBlockZ = world[1];
            contextScreenX = (int) mouseX;
            contextScreenY = (int) mouseY;
            contextOpen = true;
            clearAndInit();
            return true;
        }

        if (button == 0) {
            if (areaMode) {
                int[] world = screenToWorld(mouseX, mouseY);
                if (world == null) return true;
                if (areaX1 == null) {
                    areaX1 = world[0];
                    areaZ1 = world[1];
                } else if (areaX2 == null) {
                    areaX2 = world[0];
                    areaZ2 = world[1];
                    contextBlockX = world[0];
                    contextBlockZ = world[1];
                    contextScreenX = (int) mouseX;
                    contextScreenY = (int) mouseY;
                    contextOpen = true;
                    clearAndInit();
                }
                return true;
            }
            dragging = true;
            contextOpen = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && dragging && !areaMode) {
            double blocksPerPixel = zoom() / CELL;
            centerX -= deltaX * blocksPerPixel;
            centerZ -= deltaY * blocksPerPixel;
            zoomAnchored = false;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!mapBounds().contains(mouseX, mouseY) || verticalAmount == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        stepZoom(verticalAmount < 0 ? 1 : -1, mouseX, mouseY);
        return true;
    }

    private void stepZoom(int direction, double screenX, double screenY) {
        int next = Math.max(0, Math.min(ZOOM_STEPS.length - 1, zoomIndex + direction));
        if (next == zoomIndex) return;
        int[] anchor = screenToWorld(screenX, screenY);
        if (anchor != null) {
            zoomAnchorWorldX = anchor[0];
            zoomAnchorWorldZ = anchor[1];
            zoomAnchorScreenX = screenX;
            zoomAnchorScreenY = screenY;
            zoomAnchored = true;
        }
        zoomIndex = next;
        targetZoom = ZOOM_STEPS[zoomIndex];
    }

    private void updateZoomAnimation() {
        double difference = targetZoom - animatedZoom;
        if (Math.abs(difference) < 0.01) {
            animatedZoom = targetZoom;
            zoomAnchored = false;
            return;
        }
        animatedZoom += difference * 0.24;
        if (zoomAnchored) {
            Bounds bounds = mapBounds();
            double blocksPerPixel = animatedZoom / CELL;
            centerX = zoomAnchorWorldX - (zoomAnchorScreenX - bounds.centerX()) * blocksPerPixel;
            centerZ = zoomAnchorWorldZ - (zoomAnchorScreenY - bounds.centerY()) * blocksPerPixel;
        }
    }

    private void clearAreaSelection() {
        areaMode = false;
        areaX1 = null;
        areaZ1 = null;
        areaX2 = null;
        areaZ2 = null;
        contextOpen = false;
    }

    private boolean selectionReady() {
        return areaMode && areaX1 != null && areaZ1 != null && areaX2 != null && areaZ2 != null;
    }

    private void centerOnPlayer() {
        if (client != null && client.player != null) {
            centerX = client.player.getX();
            centerZ = client.player.getZ();
            zoomAnchored = false;
        }
    }

    private int[] screenToWorld(double mouseX, double mouseY) {
        Bounds bounds = mapBounds();
        if (!bounds.contains(mouseX, mouseY)) return null;
        double blocksPerPixel = zoom() / CELL;
        int x = (int) Math.floor(centerX + (mouseX - bounds.centerX()) * blocksPerPixel);
        int z = (int) Math.floor(centerZ + (mouseY - bounds.centerY()) * blocksPerPixel);
        return new int[]{x, z};
    }

    private int worldToScreenX(int blockX, Bounds bounds) {
        return bounds.centerX() + (int) Math.round((blockX - centerX) * CELL / zoom());
    }

    private int worldToScreenZ(int blockZ, Bounds bounds) {
        return bounds.centerY() + (int) Math.round((blockZ - centerZ) * CELL / zoom());
    }

    private double zoom() {
        return animatedZoom;
    }

    private Bounds mapBounds() {
        return new Bounds(MAP_MARGIN, TOP_BAR, width - MAP_MARGIN, height - BOTTOM_BAR);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        SURFACE.flushAsync();
        if (client != null) client.setScreen(null);
    }

    private record Bounds(int left, int top, int right, int bottom) {
        int width() { return right - left; }
        int height() { return bottom - top; }
        int centerX() { return left + width() / 2; }
        int centerY() { return top + height() / 2; }
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}

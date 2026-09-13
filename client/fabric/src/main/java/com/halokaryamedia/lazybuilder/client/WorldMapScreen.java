package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Fullscreen LazyBuilder world map.
 *
 * <p>The interaction contract follows the established fullscreen-map mental
 * model: map-first presentation, continuous drag panning, cursor-anchored
 * animated zoom, fine zoom with CTRL, cursor-local context actions, persistent
 * explored terrain, directional player indication and map-native area selection.</p>
 */
public final class WorldMapScreen extends Screen {
    private static final int TOP_BAR = 28;
    private static final int BOTTOM_BAR = 24;
    private static final int SAMPLE_BUDGET_PER_FRAME = 4096;
    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 64.0;
    private static final double PRECISE_ZOOM_FACTOR = 1.18;
    private static final double[] ZOOM_STEPS = {0.5, 1, 2, 4, 8, 16, 32, 64};
    private static final ClientMapSurfaceCache SURFACE = new ClientMapSurfaceCache();

    private final ClientWorldController worlds;
    private final ClientTransferController transfers;
    private final ClientMapController maps;

    private double centerX;
    private double centerZ;
    private double animatedZoom = 2.0;
    private double targetZoom = 2.0;
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
        super(Text.literal("World Map"));
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
            if (client != null) client.setScreen(new WorldManagerScreen(this, worlds, transfers, maps));
        }).dimensions(5, 5, 58, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("−"), button -> discreteZoom(1, width - 51, 14))
                .dimensions(width - 51, 5, 20, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> discreteZoom(-1, width - 27, 14))
                .dimensions(width - 27, 5, 20, 18).build());

        if (contextOpen) addContextButtons();
    }

    private void addContextButtons() {
        int menuWidth = 154;
        int itemHeight = 19;
        int itemCount = selectionReady() ? 2 : 5;
        int panelX = Math.max(4, Math.min(width - menuWidth - 4, contextScreenX));
        int panelY = Math.max(TOP_BAR + 3,
                Math.min(height - BOTTOM_BAR - (itemCount * itemHeight + 24), contextScreenY));
        int y = panelY + 22;

        if (selectionReady()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Export Selection"), button -> {
                maps.exportAreaCurrent(areaX1, areaZ1, areaX2, areaZ2,
                        "JAVA_1_21_4", "area-" + System.currentTimeMillis());
                clearAreaSelection();
                clearAndInit();
            }).dimensions(panelX + 3, y, menuWidth - 6, 18).build());
            y += itemHeight;
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel Selection"), button -> {
                clearAreaSelection();
                clearAndInit();
            }).dimensions(panelX + 3, y, menuWidth - 6, 18).build());
            return;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Teleport Here"), button -> {
            maps.teleportCurrent(contextBlockX, contextBlockZ);
            contextOpen = false;
            clearAndInit();
        }).dimensions(panelX + 3, y, menuWidth - 6, 18).build());
        y += itemHeight;

        addDrawableChild(ButtonWidget.builder(Text.literal("Export Area"), button -> {
            areaMode = true;
            areaX1 = null;
            areaZ1 = null;
            areaX2 = null;
            areaZ2 = null;
            contextOpen = false;
            clearAndInit();
        }).dimensions(panelX + 3, y, menuWidth - 6, 18).build());
        y += itemHeight;

        addDrawableChild(ButtonWidget.builder(Text.literal("Center Map Here"), button -> {
            centerX = contextBlockX;
            centerZ = contextBlockZ;
            contextOpen = false;
            zoomAnchored = false;
            clearAndInit();
        }).dimensions(panelX + 3, y, menuWidth - 6, 18).build());
        y += itemHeight;

        addDrawableChild(ButtonWidget.builder(Text.literal("Copy Coordinates"), button -> {
            if (client != null) client.keyboard.setClipboard(contextBlockX + ", " + contextBlockZ);
            contextOpen = false;
            clearAndInit();
        }).dimensions(panelX + 3, y, menuWidth - 6, 18).build());
        y += itemHeight;

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            contextOpen = false;
            clearAndInit();
        }).dimensions(panelX + 3, y, menuWidth - 6, 18).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateZoomAnimation();
        context.fill(0, 0, width, height, 0xFF0C0F13);
        renderMap(context);
        renderSelection(context);
        renderCursor(context, mouseX, mouseY);
        renderHud(context, mouseX, mouseY);
        renderContextMenuBackground(context);
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
        context.fill(bounds.left, bounds.top, bounds.right, bounds.bottom, ClientMapSurfaceCache.UNEXPLORED_COLOR);

        int pixel = mapPixelSize();
        double blocksPerCell = zoom() * pixel / 2.0;
        int sampleSpan = Math.max(1, (int) Math.ceil(blocksPerCell));
        int halfCellsX = Math.max(1, bounds.width() / pixel / 2);
        int halfCellsZ = Math.max(1, bounds.height() / pixel / 2);
        double originCellX = centerX / blocksPerCell;
        double originCellZ = centerZ / blocksPerCell;

        for (int cz = -halfCellsZ - 2; cz <= halfCellsZ + 2; cz++) {
            int screenY = bounds.centerY() + cz * pixel;
            if (screenY + pixel < bounds.top || screenY >= bounds.bottom) continue;
            for (int cx = -halfCellsX - 2; cx <= halfCellsX + 2; cx++) {
                int screenX = bounds.centerX() + cx * pixel;
                if (screenX + pixel < bounds.left || screenX >= bounds.right) continue;

                int blockX = (int) Math.floor((originCellX + cx) * blocksPerCell);
                int blockZ = (int) Math.floor((originCellZ + cz) * blocksPerCell);
                ClientMapSurfaceCache.SurfaceSample sample =
                        SURFACE.sampleArea(world, blockX, blockZ, sampleSpan);
                context.fill(screenX, screenY, screenX + pixel, screenY + pixel, sample.color());
            }
        }

        renderPlayerMarker(context, bounds);
    }

    /**
     * Close zoom uses smaller screen pixels so roads/buildings do not appear as
     * obvious 4px cells. Far zoom increases the pixel footprint to keep sampling
     * bounded while multi-sample LOD preserves larger terrain features.
     */
    private int mapPixelSize() {
        double z = zoom();
        if (z <= 2.0) return 2;
        if (z <= 8.0) return 3;
        return 4;
    }

    private double blocksPerPixel() {
        return zoom() / 2.0;
    }

    private void renderPlayerMarker(DrawContext context, Bounds bounds) {
        if (client == null || client.player == null) return;
        double scale = 1.0 / blocksPerPixel();
        int px = bounds.centerX() + (int) Math.round((client.player.getX() - centerX) * scale);
        int pz = bounds.centerY() + (int) Math.round((client.player.getZ() - centerZ) * scale);
        if (!bounds.contains(px, pz)) return;

        context.getMatrices().push();
        context.getMatrices().translate(px, pz, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-client.player.getYaw()));
        context.fill(-2, -9, 3, 5, 0xDD000000);
        context.fill(-3, -7, 4, 1, 0xFFFFFFFF);
        context.fill(-5, -4, 6, -1, 0xFFFFFFFF);
        context.fill(-1, -8, 2, 2, 0xFFFF5555);
        context.getMatrices().pop();
    }

    private void renderCursor(DrawContext context, int mouseX, int mouseY) {
        if (contextOpen || !mapBounds().contains(mouseX, mouseY)) return;
        int alpha = areaMode ? 0x99 : 0x55;
        int color = (alpha << 24) | 0xFFFFFF;
        context.fill(mouseX - 5, mouseY, mouseX + 6, mouseY + 1, color);
        context.fill(mouseX, mouseY - 5, mouseX + 1, mouseY + 6, color);
    }

    private void renderHud(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, width, TOP_BAR, 0xB914171B);
        context.fill(0, height - BOTTOM_BAR, width, height, 0xB914171B);

        String worldName = maps.currentWorld() == null ? "World Map" : maps.currentWorld().displayName();
        String dimension = client == null || client.world == null
                ? ""
                : client.world.getRegistryKey().getValue().getPath();
        context.drawTextWithShadow(textRenderer,
                Text.literal(dimension.isBlank() ? worldName : worldName + "  •  " + dimension),
                69, 10, 0xFFFFFF);

        int[] hovered = screenToWorld(mouseX, mouseY);
        String zoomText = zoomLabel();
        String coords = hovered == null
                ? zoomText
                : "X: " + hovered[0] + "  Z: " + hovered[1] + "   " + zoomText;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(coords), width / 2, height - 16, 0xE7E7E7);

        if (selectionReady()) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Selection ready — choose Export Selection"),
                    8, height - 16, 0xFFD166);
        } else if (areaMode) {
            String status = areaX1 == null ? "Select first corner" : "Select second corner";
            context.drawTextWithShadow(textRenderer, Text.literal(status), 8, height - 16, 0xFFD166);
        } else if (SURFACE.pendingCount() > 0) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Mapping… " + SURFACE.pendingCount()), 8, height - 16, 0xAEB7C4);
        }
    }

    private void renderContextMenuBackground(DrawContext context) {
        if (!contextOpen) return;
        int menuWidth = 154;
        int itemCount = selectionReady() ? 2 : 5;
        int panelX = Math.max(4, Math.min(width - menuWidth - 4, contextScreenX));
        int panelY = Math.max(TOP_BAR + 3,
                Math.min(height - BOTTOM_BAR - (itemCount * 19 + 24), contextScreenY));
        int panelBottom = panelY + 24 + itemCount * 19;
        context.fill(panelX - 1, panelY - 1, panelX + menuWidth + 1, panelBottom + 1, 0xFF050607);
        context.fill(panelX, panelY, panelX + menuWidth, panelBottom, 0xEE15181D);
        context.fill(panelX, panelY, panelX + menuWidth, panelY + 20, 0xF0252930);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Choose an Option"),
                panelX + menuWidth / 2, panelY + 6, 0xFFFFFF);
        if (!selectionReady()) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal(contextBlockX + ", " + contextBlockZ), panelX + 5, panelBottom - 11, 0x8E98A5);
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

        if (button == 2) {
            centerOnPlayer();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && dragging && !areaMode) {
            centerX -= deltaX * blocksPerPixel();
            centerZ -= deltaY * blocksPerPixel();
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
        if (hasControlDown()) preciseZoom(verticalAmount < 0 ? 1 : -1, mouseX, mouseY);
        else discreteZoom(verticalAmount < 0 ? 1 : -1, mouseX, mouseY);
        return true;
    }

    private void discreteZoom(int direction, double screenX, double screenY) {
        int current = nearestZoomIndex(targetZoom);
        int next = Math.max(0, Math.min(ZOOM_STEPS.length - 1, current + direction));
        setZoomTarget(ZOOM_STEPS[next], screenX, screenY);
    }

    private void preciseZoom(int direction, double screenX, double screenY) {
        double next = direction > 0
                ? targetZoom * PRECISE_ZOOM_FACTOR
                : targetZoom / PRECISE_ZOOM_FACTOR;
        setZoomTarget(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, next)), screenX, screenY);
    }

    private void setZoomTarget(double next, double screenX, double screenY) {
        if (Math.abs(next - targetZoom) < 0.0001) return;
        double[] anchor = screenToWorldExact(screenX, screenY);
        if (anchor != null) {
            zoomAnchorWorldX = anchor[0];
            zoomAnchorWorldZ = anchor[1];
            zoomAnchorScreenX = screenX;
            zoomAnchorScreenY = screenY;
            zoomAnchored = true;
        }
        targetZoom = next;
    }

    private int nearestZoomIndex(double value) {
        int best = 0;
        double distance = Double.MAX_VALUE;
        for (int i = 0; i < ZOOM_STEPS.length; i++) {
            double candidate = Math.abs(ZOOM_STEPS[i] - value);
            if (candidate < distance) {
                distance = candidate;
                best = i;
            }
        }
        return best;
    }

    private void updateZoomAnimation() {
        double difference = targetZoom - animatedZoom;
        if (Math.abs(difference) < 0.005) {
            animatedZoom = targetZoom;
            zoomAnchored = false;
            return;
        }
        animatedZoom += difference * 0.22;
        if (zoomAnchored) {
            Bounds bounds = mapBounds();
            double blocksPerPixel = blocksPerPixel();
            centerX = zoomAnchorWorldX - (zoomAnchorScreenX - bounds.centerX()) * blocksPerPixel;
            centerZ = zoomAnchorWorldZ - (zoomAnchorScreenY - bounds.centerY()) * blocksPerPixel;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (contextOpen) {
                contextOpen = false;
                clearAndInit();
                return true;
            }
            if (areaMode) {
                clearAreaSelection();
                clearAndInit();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    private double[] screenToWorldExact(double mouseX, double mouseY) {
        Bounds bounds = mapBounds();
        if (!bounds.contains(mouseX, mouseY)) return null;
        double blocksPerPixel = blocksPerPixel();
        return new double[]{
                centerX + (mouseX - bounds.centerX()) * blocksPerPixel,
                centerZ + (mouseY - bounds.centerY()) * blocksPerPixel
        };
    }

    private int[] screenToWorld(double mouseX, double mouseY) {
        double[] exact = screenToWorldExact(mouseX, mouseY);
        if (exact == null) return null;
        return new int[]{(int) Math.floor(exact[0]), (int) Math.floor(exact[1])};
    }

    private int worldToScreenX(int blockX, Bounds bounds) {
        return bounds.centerX() + (int) Math.round((blockX - centerX) / blocksPerPixel());
    }

    private int worldToScreenZ(int blockZ, Bounds bounds) {
        return bounds.centerY() + (int) Math.round((blockZ - centerZ) / blocksPerPixel());
    }

    private double zoom() {
        return animatedZoom;
    }

    private String zoomLabel() {
        if (zoom() >= 1.0) return String.format(Locale.ROOT, "Zoom 1:%.1f", zoom());
        return String.format(Locale.ROOT, "Zoom %.2f:1", 1.0 / zoom());
    }

    private Bounds mapBounds() {
        return new Bounds(0, TOP_BAR, width, height - BOTTOM_BAR);
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

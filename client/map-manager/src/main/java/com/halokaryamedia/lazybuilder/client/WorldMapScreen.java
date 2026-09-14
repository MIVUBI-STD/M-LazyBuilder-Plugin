package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Fullscreen LazyBuilder world map.
 *
 * <p>The map owns spatial presentation and selection only. The chunk-grid
 * selection interaction is first-party LazyBuilder UI; no external converter
 * component, asset, widget, or runtime owns this surface. Export format,
 * packaging and conversion stay in the canonical Import / Export workspace.</p>
 */
public final class WorldMapScreen extends Screen {
    private static final int TOP_BAR = 30;
    private static final int BOTTOM_BAR = 26;
    private static final int SELECTION_BOTTOM_BAR = 66;
    private static final int SAMPLE_BUDGET_PER_FRAME = 4096;
    private static final int CHUNK_BLOCKS = 16;
    private static final int REGION_BLOCKS = 512;
    private static final int DEFAULT_SELECTION_CHUNKS = 8;
    private static final int HANDLE_RADIUS = 5;
    private static final int CHUNK_GRID_COLOR = 0x2EFFFFFF;
    private static final int REGION_GRID_COLOR = 0x667F8FA3;
    private static final int OUTSIDE_SELECTION_DIM = 0x48101418;
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
    private boolean draggingMap;

    private boolean zoomAnchored;
    private double zoomAnchorWorldX;
    private double zoomAnchorWorldZ;
    private double zoomAnchorScreenX;
    private double zoomAnchorScreenY;

    private boolean areaMode;
    private UUID selectionWorldId;
    private int minChunkX;
    private int maxChunkX;
    private int minChunkZ;
    private int maxChunkZ;
    private DragMode selectionDrag = DragMode.NONE;
    private int dragStartChunkX;
    private int dragStartChunkZ;
    private int dragMinChunkX;
    private int dragMaxChunkX;
    private int dragMinChunkZ;
    private int dragMaxChunkZ;

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

        addDrawableChild(LbUi.button(6, 6, 64, 18,
                "Worlds", LbButtonWidget.Style.SECONDARY,
                () -> { if (client != null) client.setScreen(new WorldManagerScreen(this, worlds, transfers, maps)); }));

        addDrawableChild(LbUi.button(width - 52, 6, 20, 18,
                "−", LbButtonWidget.Style.GHOST,
                () -> discreteZoom(1, width - 42, 15)));
        addDrawableChild(LbUi.button(width - 28, 6, 20, 18,
                "+", LbButtonWidget.Style.GHOST,
                () -> discreteZoom(-1, width - 18, 15)));

        if (areaMode) addSelectionActions();
        else if (contextOpen) addContextButtons();
    }

    private void addContextButtons() {
        int menuWidth = 164;
        int itemHeight = 21;
        int itemCount = 5;
        int panelX = Math.max(5, Math.min(width - menuWidth - 5, contextScreenX));
        int panelY = Math.max(TOP_BAR + 4,
                Math.min(height - BOTTOM_BAR - (itemCount * itemHeight + 30), contextScreenY));
        int y = panelY + 25;

        addDrawableChild(LbUi.button(panelX + 5, y, menuWidth - 10, 19,
                "Teleport Here", LbButtonWidget.Style.PRIMARY, () -> {
                    maps.teleportCurrent(contextBlockX, contextBlockZ);
                    contextOpen = false;
                    clearAndInit();
                }));
        y += itemHeight;

        LbButtonWidget exportArea = LbUi.button(panelX + 5, y, menuWidth - 10, 19,
                "Export Area", LbButtonWidget.Style.SECONDARY,
                () -> beginAreaSelection(contextBlockX, contextBlockZ));
        exportArea.active = maps.currentWorld() != null && worlds.canManage();
        addDrawableChild(exportArea);
        y += itemHeight;

        addDrawableChild(LbUi.button(panelX + 5, y, menuWidth - 10, 19,
                "Center Map Here", LbButtonWidget.Style.GHOST, () -> {
                    centerX = contextBlockX;
                    centerZ = contextBlockZ;
                    contextOpen = false;
                    zoomAnchored = false;
                    clearAndInit();
                }));
        y += itemHeight;

        addDrawableChild(LbUi.button(panelX + 5, y, menuWidth - 10, 19,
                "Copy Coordinates", LbButtonWidget.Style.GHOST, () -> {
                    if (client != null) client.keyboard.setClipboard(contextBlockX + ", " + contextBlockZ);
                    contextOpen = false;
                    clearAndInit();
                }));
        y += itemHeight;

        addDrawableChild(LbUi.button(panelX + 5, y, menuWidth - 10, 19,
                "Cancel", LbButtonWidget.Style.GHOST, () -> {
                    contextOpen = false;
                    clearAndInit();
                }));
    }

    private void addSelectionActions() {
        int y = height - 43;
        int continueWidth = 92;
        int cancelWidth = 72;
        int gap = 8;
        int right = width - 10;

        addDrawableChild(LbUi.button(right - continueWidth, y, continueWidth, 24,
                "Continue", LbButtonWidget.Style.PRIMARY, this::continueAreaExport));
        addDrawableChild(LbUi.button(right - continueWidth - gap - cancelWidth, y, cancelWidth, 24,
                "Cancel", LbButtonWidget.Style.GHOST, () -> {
                    clearAreaSelection();
                    clearAndInit();
                }));
    }

    private void beginAreaSelection(int blockX, int blockZ) {
        var current = maps.currentWorld();
        if (current == null) {
            LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: open a managed world before selecting an export area.");
            return;
        }
        if (!worlds.canManage()) {
            LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: your server role does not allow area export.");
            return;
        }

        int centerChunkX = Math.floorDiv(blockX, CHUNK_BLOCKS);
        int centerChunkZ = Math.floorDiv(blockZ, CHUNK_BLOCKS);
        int before = DEFAULT_SELECTION_CHUNKS / 2;
        int after = DEFAULT_SELECTION_CHUNKS - before - 1;
        minChunkX = centerChunkX - before;
        maxChunkX = centerChunkX + after;
        minChunkZ = centerChunkZ - before;
        maxChunkZ = centerChunkZ + after;
        selectionWorldId = current.worldId().value();
        areaMode = true;
        contextOpen = false;
        selectionDrag = DragMode.NONE;
        clearAndInit();
    }

    private void continueAreaExport() {
        if (!areaMode || client == null) return;
        var current = maps.currentWorld();
        if (current == null || selectionWorldId == null || !selectionWorldId.equals(current.worldId().value())) {
            clearAreaSelection();
            LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: the selected area no longer belongs to the current world.");
            clearAndInit();
            return;
        }

        client.setScreen(WorldTransferScreen.forArea(
                this,
                worlds,
                transfers,
                maps,
                current,
                minBlockX(), minBlockZ(), maxBlockX(), maxBlockZ()));
    }

    /** Called by the export workspace after a selected-area export completes. */
    void finishAreaExport() {
        clearAreaSelection();
    }

    @Override
    public void tick() {
        if (areaMode) {
            var current = maps.currentWorld();
            if (current == null || selectionWorldId == null || !selectionWorldId.equals(current.worldId().value())) {
                clearAreaSelection();
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: area selection cleared because the current world changed.");
                clearAndInit();
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateZoomAnimation();
        context.fill(0, 0, width, height, LbUi.BACKGROUND);
        renderMap(context);
        if (areaMode) {
            renderSelectionGrid(context);
            renderSelection(context, mouseX, mouseY);
        }
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
                ClientMapSurfaceCache.SurfaceSample sample = SURFACE.sampleArea(world, blockX, blockZ, sampleSpan);
                context.fill(screenX, screenY, screenX + pixel, screenY + pixel, sample.color());
            }
        }

        renderPlayerMarker(context, bounds);
    }

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
        context.fill(-3, -10, 4, 6, 0xCC000000);
        context.fill(-3, -8, 4, 1, LbUi.TEXT_PRIMARY);
        context.fill(-5, -5, 6, -1, LbUi.TEXT_PRIMARY);
        context.fill(-1, -9, 2, 2, LbUi.ACCENT_BRIGHT);
        context.getMatrices().pop();
    }

    private void renderSelectionGrid(DrawContext context) {
        Bounds bounds = mapBounds();
        double bpp = blocksPerPixel();
        double chunkPixels = CHUNK_BLOCKS / bpp;
        double regionPixels = REGION_BLOCKS / bpp;

        double worldLeft = centerX + (bounds.left - bounds.centerX()) * bpp;
        double worldRight = centerX + (bounds.right - bounds.centerX()) * bpp;
        double worldTop = centerZ + (bounds.top - bounds.centerY()) * bpp;
        double worldBottom = centerZ + (bounds.bottom - bounds.centerY()) * bpp;

        if (chunkPixels >= 6.0) {
            int firstChunkX = Math.floorDiv((int) Math.floor(worldLeft), CHUNK_BLOCKS) - 1;
            int lastChunkX = Math.floorDiv((int) Math.ceil(worldRight), CHUNK_BLOCKS) + 1;
            for (int chunkX = firstChunkX; chunkX <= lastChunkX; chunkX++) {
                int x = worldToScreenX(chunkX * CHUNK_BLOCKS, bounds);
                if (x >= bounds.left && x < bounds.right) context.fill(x, bounds.top, x + 1, bounds.bottom, CHUNK_GRID_COLOR);
            }
            int firstChunkZ = Math.floorDiv((int) Math.floor(worldTop), CHUNK_BLOCKS) - 1;
            int lastChunkZ = Math.floorDiv((int) Math.ceil(worldBottom), CHUNK_BLOCKS) + 1;
            for (int chunkZ = firstChunkZ; chunkZ <= lastChunkZ; chunkZ++) {
                int y = worldToScreenZ(chunkZ * CHUNK_BLOCKS, bounds);
                if (y >= bounds.top && y < bounds.bottom) context.fill(bounds.left, y, bounds.right, y + 1, CHUNK_GRID_COLOR);
            }
        }

        if (regionPixels >= 8.0) {
            int firstRegionX = Math.floorDiv((int) Math.floor(worldLeft), REGION_BLOCKS) - 1;
            int lastRegionX = Math.floorDiv((int) Math.ceil(worldRight), REGION_BLOCKS) + 1;
            for (int regionX = firstRegionX; regionX <= lastRegionX; regionX++) {
                int x = worldToScreenX(regionX * REGION_BLOCKS, bounds);
                if (x >= bounds.left && x < bounds.right) context.fill(x, bounds.top, x + 2, bounds.bottom, REGION_GRID_COLOR);
            }
            int firstRegionZ = Math.floorDiv((int) Math.floor(worldTop), REGION_BLOCKS) - 1;
            int lastRegionZ = Math.floorDiv((int) Math.ceil(worldBottom), REGION_BLOCKS) + 1;
            for (int regionZ = firstRegionZ; regionZ <= lastRegionZ; regionZ++) {
                int y = worldToScreenZ(regionZ * REGION_BLOCKS, bounds);
                if (y >= bounds.top && y < bounds.bottom) context.fill(bounds.left, y, bounds.right, y + 2, REGION_GRID_COLOR);
            }
        }
    }

    private void renderSelection(DrawContext context, int mouseX, int mouseY) {
        SelectionRect rect = selectionRect();
        if (rect == null) return;

        Bounds bounds = mapBounds();
        int left = Math.max(bounds.left, rect.left);
        int right = Math.min(bounds.right, rect.right);
        int top = Math.max(bounds.top, rect.top);
        int bottom = Math.min(bounds.bottom, rect.bottom);
        if (right <= left || bottom <= top) return;

        // Keep the selected terrain readable and de-emphasize everything that will not be exported.
        if (top > bounds.top) context.fill(bounds.left, bounds.top, bounds.right, top, OUTSIDE_SELECTION_DIM);
        if (bottom < bounds.bottom) context.fill(bounds.left, bottom, bounds.right, bounds.bottom, OUTSIDE_SELECTION_DIM);
        if (left > bounds.left) context.fill(bounds.left, top, left, bottom, OUTSIDE_SELECTION_DIM);
        if (right < bounds.right) context.fill(right, top, bounds.right, bottom, OUTSIDE_SELECTION_DIM);

        context.fill(left, top, right, top + 2, LbUi.ACCENT_BRIGHT);
        context.fill(left, bottom - 2, right, bottom, LbUi.ACCENT_BRIGHT);
        context.fill(left, top, left + 2, bottom, LbUi.ACCENT_BRIGHT);
        context.fill(right - 2, top, right, bottom, LbUi.ACCENT_BRIGHT);

        DragMode hover = hitSelection(mouseX, mouseY);
        for (Handle handle : handles(rect)) {
            int radius = handle.mode == hover || handle.mode == selectionDrag ? HANDLE_RADIUS + 1 : HANDLE_RADIUS;
            int color = handle.mode == hover || handle.mode == selectionDrag ? LbUi.TEXT_PRIMARY : LbUi.ACCENT_BRIGHT;
            context.fill(handle.x - radius, handle.y - radius, handle.x + radius + 1, handle.y + radius + 1, 0xAA10151C);
            context.fill(handle.x - radius + 2, handle.y - radius + 2,
                    handle.x + radius - 1, handle.y + radius - 1, color);
        }
    }

    private void renderCursor(DrawContext context, int mouseX, int mouseY) {
        if (contextOpen || !mapBounds().contains(mouseX, mouseY)) return;
        if (areaMode) {
            DragMode hover = hitSelection(mouseX, mouseY);
            String hint = switch (hover) {
                case MOVE -> "Move selection";
                case N, S, E, W, NE, NW, SE, SW -> "Resize selection";
                default -> "Drag map";
            };
            context.drawTextWithShadow(textRenderer, Text.literal(hint), mouseX + 9, mouseY + 9, LbUi.TEXT_SECONDARY);
        }
        int color = areaMode ? 0xBB8AA8FF : 0x667F8A98;
        context.fill(mouseX - 5, mouseY, mouseX + 6, mouseY + 1, color);
        context.fill(mouseX, mouseY - 5, mouseX + 1, mouseY + 6, color);
    }

    private void renderHud(DrawContext context, int mouseX, int mouseY) {
        int bottomBar = areaMode ? SELECTION_BOTTOM_BAR : BOTTOM_BAR;
        context.fill(0, 0, width, TOP_BAR, 0xE314181E);
        context.fill(0, TOP_BAR - 1, width, TOP_BAR, LbUi.BORDER);
        context.fill(0, height - bottomBar, width, height, 0xE314181E);
        context.fill(0, height - bottomBar, width, height - bottomBar + 1, LbUi.BORDER);

        String worldName = maps.currentWorld() == null ? "World Map" : maps.currentWorld().displayName();
        String dimension = client == null || client.world == null
                ? ""
                : friendlyDimension(client.world.getRegistryKey().getValue().getPath());
        String title = dimension.isBlank() ? worldName : worldName + "  •  " + dimension;
        context.drawTextWithShadow(textRenderer, Text.literal(title), 78, 11, LbUi.TEXT_PRIMARY);

        int[] hovered = screenToWorld(mouseX, mouseY);
        String zoomText = zoomLabel();
        String coords;
        if (hovered == null) {
            coords = zoomText;
        } else {
            int hoverChunkX = Math.floorDiv(hovered[0], CHUNK_BLOCKS);
            int hoverChunkZ = Math.floorDiv(hovered[1], CHUNK_BLOCKS);
            coords = areaMode
                    ? "Chunk " + hoverChunkX + ", " + hoverChunkZ + "   •   Block X " + hovered[0] + " Z " + hovered[1] + "   •   " + zoomText
                    : "X " + hovered[0] + "   Z " + hovered[1] + "   •   " + zoomText;
        }
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(coords), width / 2,
                areaMode ? height - 59 : height - 17, LbUi.TEXT_SECONDARY);

        if (areaMode) {
            int chunksX = maxChunkX - minChunkX + 1;
            int chunksZ = maxChunkZ - minChunkZ + 1;
            context.drawTextWithShadow(textRenderer, Text.literal("EXPORT AREA"), 10, height - 43, LbUi.ACCENT_BRIGHT);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Chunks   X " + minChunkX + " → " + maxChunkX + "   Z " + minChunkZ + " → " + maxChunkZ),
                    82, height - 43, LbUi.TEXT_PRIMARY);
            context.drawTextWithShadow(textRenderer,
                    Text.literal(chunksX + " × " + chunksZ + " chunks   •   "
                            + (chunksX * CHUNK_BLOCKS) + " × " + (chunksZ * CHUNK_BLOCKS) + " blocks"),
                    10, height - 27, LbUi.TEXT_SECONDARY);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Blocks   X " + minBlockX() + " → " + maxBlockX() + "   Z " + minBlockZ() + " → " + maxBlockZ()),
                    10, height - 13, LbUi.TEXT_MUTED);
            return;
        }

        String leftStatus;
        int leftColor;
        if (SURFACE.pendingCount() > 0) {
            leftStatus = "Mapping " + SURFACE.pendingCount() + " columns…";
            leftColor = LbUi.TEXT_MUTED;
        } else {
            leftStatus = "Drag pan  •  Scroll zoom  •  Right-click actions  •  Middle-click recenter";
            leftColor = LbUi.TEXT_MUTED;
        }
        context.drawTextWithShadow(textRenderer, Text.literal(leftStatus), 8, height - 17, leftColor);
    }

    private static String friendlyDimension(String raw) {
        return switch (raw) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "The End";
            default -> raw.replace('_', ' ');
        };
    }

    private void renderContextMenuBackground(DrawContext context) {
        if (!contextOpen || areaMode) return;
        int menuWidth = 164;
        int itemCount = 5;
        int panelX = Math.max(5, Math.min(width - menuWidth - 5, contextScreenX));
        int panelY = Math.max(TOP_BAR + 4,
                Math.min(height - BOTTOM_BAR - (itemCount * 21 + 30), contextScreenY));
        int panelBottom = panelY + 30 + itemCount * 21;

        context.fill(panelX - 2, panelY - 2, panelX + menuWidth + 2, panelBottom + 2, 0x77000000);
        LbUi.elevatedPanel(context, panelX, panelY, menuWidth, panelBottom - panelY);
        context.fill(panelX + 1, panelY + 1, panelX + menuWidth - 1, panelY + 23, LbUi.SURFACE_3);
        context.drawTextWithShadow(textRenderer, Text.literal("Map Actions"),
                panelX + 7, panelY + 8, LbUi.TEXT_PRIMARY);
        String coordinate = "X " + contextBlockX + "   Z " + contextBlockZ;
        context.drawTextWithShadow(textRenderer, Text.literal(coordinate),
                panelX + 7, panelBottom - 13, LbUi.TEXT_MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!mapBounds().contains(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);

        if (button == 2) {
            centerOnPlayer();
            return true;
        }

        if (areaMode) {
            if (button == 1) return true;
            if (button == 0) {
                DragMode hit = hitSelection(mouseX, mouseY);
                int[] chunk = screenToChunk(mouseX, mouseY);
                if (chunk == null) return true;
                if (hit != DragMode.NONE) {
                    beginSelectionDrag(hit, chunk[0], chunk[1]);
                } else {
                    draggingMap = true;
                }
                return true;
            }
            return true;
        }

        if (button == 1) {
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
            draggingMap = true;
            contextOpen = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

        if (areaMode && selectionDrag != DragMode.NONE) {
            int[] chunk = screenToChunk(mouseX, mouseY);
            if (chunk != null) updateSelectionDrag(chunk[0], chunk[1]);
            return true;
        }

        if (draggingMap) {
            centerX -= deltaX * blocksPerPixel();
            centerZ -= deltaY * blocksPerPixel();
            zoomAnchored = false;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean handled = draggingMap || selectionDrag != DragMode.NONE;
            draggingMap = false;
            selectionDrag = DragMode.NONE;
            if (handled) return true;
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

    private void beginSelectionDrag(DragMode mode, int chunkX, int chunkZ) {
        selectionDrag = mode;
        dragStartChunkX = chunkX;
        dragStartChunkZ = chunkZ;
        dragMinChunkX = minChunkX;
        dragMaxChunkX = maxChunkX;
        dragMinChunkZ = minChunkZ;
        dragMaxChunkZ = maxChunkZ;
    }

    private void updateSelectionDrag(int chunkX, int chunkZ) {
        if (selectionDrag == DragMode.MOVE) {
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
        switch (selectionDrag) {
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

    private DragMode hitSelection(double mouseX, double mouseY) {
        if (!areaMode) return DragMode.NONE;
        SelectionRect rect = selectionRect();
        if (rect == null) return DragMode.NONE;

        for (Handle handle : handles(rect)) {
            if (Math.abs(mouseX - handle.x) <= HANDLE_RADIUS + 3
                    && Math.abs(mouseY - handle.y) <= HANDLE_RADIUS + 3) return handle.mode;
        }
        if (mouseX > rect.left + HANDLE_RADIUS && mouseX < rect.right - HANDLE_RADIUS
                && mouseY > rect.top + HANDLE_RADIUS && mouseY < rect.bottom - HANDLE_RADIUS) {
            return DragMode.MOVE;
        }
        return DragMode.NONE;
    }

    private Handle[] handles(SelectionRect rect) {
        int midX = rect.left + (rect.right - rect.left) / 2;
        int midY = rect.top + (rect.bottom - rect.top) / 2;
        return new Handle[]{
                new Handle(rect.left, rect.top, DragMode.NW),
                new Handle(midX, rect.top, DragMode.N),
                new Handle(rect.right, rect.top, DragMode.NE),
                new Handle(rect.right, midY, DragMode.E),
                new Handle(rect.right, rect.bottom, DragMode.SE),
                new Handle(midX, rect.bottom, DragMode.S),
                new Handle(rect.left, rect.bottom, DragMode.SW),
                new Handle(rect.left, midY, DragMode.W)
        };
    }

    private SelectionRect selectionRect() {
        if (!areaMode) return null;
        Bounds bounds = mapBounds();
        int left = worldToScreenX(minBlockX(), bounds);
        int right = worldToScreenX((maxChunkX + 1) * CHUNK_BLOCKS, bounds);
        int top = worldToScreenZ(minBlockZ(), bounds);
        int bottom = worldToScreenZ((maxChunkZ + 1) * CHUNK_BLOCKS, bounds);
        return new SelectionRect(Math.min(left, right), Math.min(top, bottom), Math.max(left, right), Math.max(top, bottom));
    }

    private int minBlockX() { return minChunkX * CHUNK_BLOCKS; }
    private int maxBlockX() { return maxChunkX * CHUNK_BLOCKS + CHUNK_BLOCKS - 1; }
    private int minBlockZ() { return minChunkZ * CHUNK_BLOCKS; }
    private int maxBlockZ() { return maxChunkZ * CHUNK_BLOCKS + CHUNK_BLOCKS - 1; }

    private void discreteZoom(int direction, double screenX, double screenY) {
        int current = nearestZoomIndex(targetZoom);
        int next = Math.max(0, Math.min(ZOOM_STEPS.length - 1, current + direction));
        setZoomTarget(ZOOM_STEPS[next], screenX, screenY);
    }

    private void preciseZoom(int direction, double screenX, double screenY) {
        double next = direction > 0 ? targetZoom * PRECISE_ZOOM_FACTOR : targetZoom / PRECISE_ZOOM_FACTOR;
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
        if (areaMode && (keyCode == 257 || keyCode == 335)) {
            continueAreaExport();
            return true;
        }
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
        selectionWorldId = null;
        selectionDrag = DragMode.NONE;
        draggingMap = false;
        contextOpen = false;
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

    private int[] screenToChunk(double mouseX, double mouseY) {
        int[] world = screenToWorld(mouseX, mouseY);
        if (world == null) return null;
        return new int[]{Math.floorDiv(world[0], CHUNK_BLOCKS), Math.floorDiv(world[1], CHUNK_BLOCKS)};
    }

    private int worldToScreenX(int blockX, Bounds bounds) {
        return bounds.centerX() + (int) Math.round((blockX - centerX) / blocksPerPixel());
    }

    private int worldToScreenZ(int blockZ, Bounds bounds) {
        return bounds.centerY() + (int) Math.round((blockZ - centerZ) / blocksPerPixel());
    }

    private double zoom() { return animatedZoom; }

    private String zoomLabel() {
        if (zoom() >= 1.0) return String.format(Locale.ROOT, "Zoom 1:%.1f", zoom());
        return String.format(Locale.ROOT, "Zoom %.2f:1", 1.0 / zoom());
    }

    private Bounds mapBounds() {
        return new Bounds(0, TOP_BAR, width, height - (areaMode ? SELECTION_BOTTOM_BAR : BOTTOM_BAR));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (areaMode) {
            clearAreaSelection();
            clearAndInit();
            return;
        }
        SURFACE.flushAsync();
        if (client != null) client.setScreen(null);
    }

    private enum DragMode { NONE, MOVE, N, NE, E, SE, S, SW, W, NW }
    private record Handle(int x, int y, DragMode mode) {}
    private record SelectionRect(int left, int top, int right, int bottom) {}

    private record Bounds(int left, int top, int right, int bottom) {
        int width() { return right - left; }
        int height() { return bottom - top; }
        int centerX() { return left + width() / 2; }
        int centerY() { return top + height() / 2; }
        boolean contains(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }
    }
}

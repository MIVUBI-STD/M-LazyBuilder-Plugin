package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import com.halokaryamedia.lazybuilder.client.MapAreaSelectionState.DragMode;
import com.halokaryamedia.lazybuilder.client.MapAreaSelectionGeometry.Handle;
import com.halokaryamedia.lazybuilder.client.MapAreaSelectionGeometry.SelectionRect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fullscreen LazyBuilder world map, managed-world navigator, and single-screen export workspace.
 *
 * <p>The terrain renderer/cache remains singular. Export is a presentation mode over the same
 * map authority; server controllers remain authoritative for world state and file operations.</p>
 */
public final class WorldMapScreen extends Screen {
    private static final int BOTTOM_BAR = 24;
    private static final int SIDEBAR_GAP = 1;
    private static final int SAMPLE_BUDGET_PER_TICK = 4096;
    private static final int RASTER_CONTENT_REFRESH_TICKS = 4;
    private static final int CHUNK_BLOCKS = 16;
    private static final int REGION_BLOCKS = 512;
    private static final int DEFAULT_SELECTION_CHUNKS = 8;
    private static final int HANDLE_RADIUS = 5;
    private static final int CHUNK_GRID_COLOR = 0x2EFFFFFF;
    private static final int REGION_GRID_COLOR = 0x667F8FA3;
    private static final int OUTSIDE_SELECTION_DIM = 0x48101418;
    private static final int MAX_FAVORITES = 5;
    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 64.0;
    private static final double PRECISE_ZOOM_FACTOR = 1.18;
    private static final double[] ZOOM_STEPS = {0.5, 1, 2, 4, 8, 16, 32, 64};
    private static final ClientMapSurfaceCache SURFACE = new ClientMapSurfaceCache();
    private static final WorldNavigationPreferences NAVIGATION = WorldNavigationPreferences.shared();

    private final ClientWorldController worlds;
    private final ClientTransferController transfers;
    private final ClientMapController maps;
    private final MapExportWorkspaceState exportWorkspace = new MapExportWorkspaceState();
    private final MapExportWorkspacePanel exportPanel = new MapExportWorkspacePanel();
    private final WorldMapSidebarPanel sidebarPanel = new WorldMapSidebarPanel();

    private final MapViewportState camera = new MapViewportState();
    private boolean centeredOnce;
    private boolean initialLayoutApplied;
    private boolean sidebarCollapsed;
    private boolean sidebarManuallyToggled;
    private boolean showAllWorlds;
    private int worldListOffset;
    private UUID selectedWorldId;

    private final MapAreaSelectionState areaSelection = new MapAreaSelectionState();

    private final MapContextMenuPanel contextMenu = new MapContextMenuPanel();

    private final MapRasterPresentationState rasterState = new MapRasterPresentationState();

    private boolean requestedCurrentWorld;
    private long observedWorldRevision;
    private long observedMapRevision;
    private UUID observedCurrentWorldId;
    private boolean closeAfterWorldTeleport;
    private boolean closeAfterMapTeleport;
    private Integer previousMenuBlur;

    public WorldMapScreen(
            ClientWorldController worlds,
            ClientTransferController transfers,
            ClientMapController maps
    ) {
        super(Text.literal("World Map"));
        this.worlds = worlds;
        this.transfers = transfers;
        this.maps = maps;
        this.observedWorldRevision = worlds.revision();
        this.observedMapRevision = maps.revision();
    }

    @Override
    protected void init() {
        if (previousMenuBlur == null && client != null) {
            previousMenuBlur = client.options.getMenuBackgroundBlurriness().getValue();
            client.options.getMenuBackgroundBlurriness().setValue(0);
        }
        ensureRasterTexture();
        if (!centeredOnce) {
            centerOnPlayer();
            centeredOnce = true;
        }

        if (!exportWorkspace.active()) {
            boolean autoCollapsed = shouldAutoCollapseSidebar();
            if (!initialLayoutApplied) {
                sidebarCollapsed = autoCollapsed;
                initialLayoutApplied = true;
            } else if (!sidebarManuallyToggled || mustCollapseSidebar()) {
                if (sidebarCollapsed != autoCollapsed) {
                    sidebarCollapsed = autoCollapsed;
                    contextMenu.close();
                    invalidateRasterViewport();
                }
            }
        }

        NAVIGATION.reload();
        if (!worlds.worldListReady() && !worlds.worldListPending()) worlds.refresh();
        if (!requestedCurrentWorld) {
            requestedCurrentWorld = true;
            maps.refreshCurrentWorld();
        }
        observeCurrentWorld();
        if (!exportWorkspace.active()) normalizeSelectionForSection();
        else ensureExportData(false);
        observedWorldRevision = worlds.revision();
        observedMapRevision = maps.revision();

        if (exportWorkspace.active() && exportWorkspace.initializedFor(currentWorldId())) {
            initExportNameField();
            exportWorkspace.markControlsReady();
        }
    }

    private void ensureRasterTexture() {
        if (rasterState.texture != null || client == null) return;
        rasterState.texture = new ClientMapRasterTexture(client);
        if (rasterState.hasImage()) {
            rasterState.texture.upload(rasterState.colors, rasterState.columns, rasterState.rows);
        }
    }

    private void initExportNameField() {
        addDrawableChild(exportPanel.initializeNameField(textRenderer, exportWorkspace, width, height));
    }

    @Override
    public void tick() {
        if (observedMapRevision != maps.revision()) {
            observedMapRevision = maps.revision();
            observeCurrentWorld();
        }
        if (observedWorldRevision != worlds.revision()) {
            observedWorldRevision = worlds.revision();
            if (exportWorkspace.active()) {
                ensureExportData(true);
            } else {
                clampSelectedWorld();
            }
        }

        if (exportWorkspace.active() && exportWorkspace.initializedFor(observedCurrentWorldId)
                && !exportWorkspace.initializedFor(currentWorldId())) {
            exitExportWorkspace();
            LazyBuilderClientNetworking.notifyPlayer("Export closed because the current world changed.");
            return;
        }

        if (closeAfterMapTeleport && !maps.teleportPending()) {
            closeAfterMapTeleport = false;
            if (maps.lastError() == null) {
                closeFromToggle();
                return;
            }
        }
        if (closeAfterWorldTeleport && !worlds.teleportPending()) {
            closeAfterWorldTeleport = false;
            if (worlds.lastError() == null) {
                closeFromToggle();
                return;
            }
        }

        if (areaSelection.active) {
            var current = maps.currentWorld();
            if (current == null || areaSelection.worldId == null || !areaSelection.worldId.equals(current.worldId().value())) {
                clearAreaSelection();
                if (exportWorkspace.active()) exportWorkspace.scope(MapExportWorkspaceState.Scope.FULL_WORLD);
                LazyBuilderClientNetworking.notifyPlayer(
                        "Area selection cleared because the current world changed.");
            }
        }
    }

    private void ensureExportData(boolean rebuildIfReady) {
        UUID currentId = currentWorldId();
        var current = maps.currentWorld();
        if (!exportWorkspace.active() || currentId == null || current == null) return;

        if (!exportWorkspace.requestedFormats()) {
            exportWorkspace.markFormatsRequested();
            worlds.requestExportFormats();
        }
        WorldControlWireProtocol.SettingsSnapshot settings = worlds.settings(currentId);
        if (settings == null) {
            if (!exportWorkspace.requestedSettings()) {
                exportWorkspace.markSettingsRequested();
                worlds.requestSettings(currentId);
            }
            return;
        }

        boolean wasInitialized = exportWorkspace.initializedFor(currentId);
        exportWorkspace.initialize(currentId, current.displayName(), settings, worlds.exportFormats());
        if (!wasInitialized && rebuildIfReady && !exportWorkspace.controlsReady()) {
            exportWorkspace.markControlsReady();
            clearAndInit();
        }
    }

    private void observeCurrentWorld() {
        UUID currentId = currentWorldId();
        if (currentId == null || currentId.equals(observedCurrentWorldId)) return;
        observedCurrentWorldId = currentId;
        NAVIGATION.recordVisited(currentId);
        if (selectedWorldId == null) selectedWorldId = currentId;
    }

    private void clampSelectedWorld() {
        if (selectedWorldId == null) return;
        if (findActiveWorld(selectedWorldId) == null) selectedWorldId = null;
        int maxOffset = Math.max(0, activeWorlds().size() - visibleSidebarRows());
        worldListOffset = Math.max(0, Math.min(worldListOffset, maxOffset));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, LbUi.BACKGROUND);
        renderMap(context);
        if (areaSelection.active) {
            renderSelectionGrid(context);
            renderSelection(context, mouseX, mouseY);
        }
        renderCursor(context, mouseX, mouseY);
        if (exportWorkspace.active()) renderExportSidebar(context, mouseX, mouseY);
        else renderSidebar(context, mouseX, mouseY);
        renderBottomStatus(context, mouseX, mouseY);
        if (!exportWorkspace.active() && !areaSelection.active) {
            renderContextMenu(context, mouseX, mouseY);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderMap(DrawContext context) {
        ClientWorld world = client == null ? null : client.world;
        Bounds bounds = mapBounds();
        context.fill(bounds.left, bounds.top, bounds.right, bounds.bottom, ClientMapSurfaceCache.UNEXPLORED_COLOR);
        if (world == null) return;

        var current = maps.currentWorld();
        if (current == null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Loading current world…"),
                    bounds.centerX(), bounds.centerY() - 4, LbUi.TEXT_SECONDARY);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Map will appear when the current world is ready"),
                    bounds.centerX(), bounds.centerY() + 12, LbUi.TEXT_MUTED);
            return;
        }

        String dimension = world.getRegistryKey().getValue().toString();
        String scope = current.worldId() + "|" + dimension;
        Path storage = client.runDirectory.toPath().resolve("lazybuilder").resolve("maps");

        if (!scope.equals(rasterState.scope) && !rasterState.scope.isBlank()) {
            rasterState.resetContent();
        }
        SURFACE.useScope(scope, storage);
        if (SURFACE.processPending(world, SAMPLE_BUDGET_PER_TICK) > 0) rasterState.contentDirty = true;

        int pixel = mapPixelSize();
        WorldMapRasterViewport.State viewport = WorldMapRasterViewport.resolve(
                scope, bounds.left, bounds.top, bounds.right, bounds.bottom, pixel, camera.zoom, camera.centerX, camera.centerZ);
        long worldTime = world.getTime();
        if (rasterNeedsRefresh(scope, bounds, pixel, worldTime, viewport)) {
            rebuildRaster(world, scope, bounds, pixel, worldTime, viewport);
        }
        drawRaster(context, bounds, pixel, viewport);
        renderPlayerMarker(context, bounds);
    }

    private boolean rasterNeedsRefresh(
            String scope,
            Bounds bounds,
            int pixel,
            long worldTime,
            WorldMapRasterViewport.State viewport
    ) {
        boolean viewportChanged = rasterState.colors.length == 0
                || rasterState.pixel != pixel
                || rasterState.left != bounds.left
                || rasterState.top != bounds.top
                || rasterState.right != bounds.right
                || rasterState.bottom != bounds.bottom
                || Double.compare(rasterState.centerX, viewport.sampleCenterX()) != 0
                || Double.compare(rasterState.centerZ, viewport.sampleCenterZ()) != 0
                || Double.compare(rasterState.zoom, camera.zoom) != 0
                || !rasterState.scope.equals(scope);
        if (viewportChanged) return true;
        if (!rasterState.contentDirty) return false;
        return rasterState.worldTime == Long.MIN_VALUE
                || worldTime < rasterState.worldTime
                || worldTime - rasterState.worldTime >= RASTER_CONTENT_REFRESH_TICKS;
    }

    private void rebuildRaster(
            ClientWorld world,
            String scope,
            Bounds bounds,
            int pixel,
            long worldTime,
            WorldMapRasterViewport.State viewport
    ) {
        double blocksPerCell = viewport.blocksPerCell();
        int sampleSpan = Math.max(1, (int) Math.ceil(blocksPerCell));
        int halfCellsX = Math.max(1, bounds.width() / pixel / 2);
        int halfCellsZ = Math.max(1, bounds.height() / pixel / 2);
        int columns = halfCellsX * 2 + 5;
        int rows = halfCellsZ * 2 + 5;
        int[] nextColors = new int[columns * rows];
        java.util.Arrays.fill(nextColors, ClientMapSurfaceCache.UNEXPLORED_COLOR);

        double originCellX = viewport.sampleCenterX() / blocksPerCell;
        double originCellZ = viewport.sampleCenterZ() / blocksPerCell;
        int index = 0;
        for (int cz = -halfCellsZ - 2; cz <= halfCellsZ + 2; cz++) {
            int screenY = bounds.centerY() + cz * pixel;
            for (int cx = -halfCellsX - 2; cx <= halfCellsX + 2; cx++) {
                int screenX = bounds.centerX() + cx * pixel;
                if (screenY + pixel >= bounds.top && screenY < bounds.bottom
                        && screenX + pixel >= bounds.left && screenX < bounds.right) {
                    int blockX = (int) Math.floor((originCellX + cx) * blocksPerCell);
                    int blockZ = (int) Math.floor((originCellZ + cz) * blocksPerCell);
                    nextColors[index] = SURFACE.sampleArea(world, blockX, blockZ, sampleSpan).color();
                }
                index++;
            }
        }

        rasterState.colors = nextColors;
        rasterState.columns = columns;
        rasterState.rows = rows;
        rasterState.halfCellsX = halfCellsX;
        rasterState.halfCellsZ = halfCellsZ;
        rasterState.pixel = pixel;
        rasterState.left = bounds.left;
        rasterState.top = bounds.top;
        rasterState.right = bounds.right;
        rasterState.bottom = bounds.bottom;
        rasterState.centerX = viewport.sampleCenterX();
        rasterState.centerZ = viewport.sampleCenterZ();
        rasterState.zoom = camera.zoom;
        rasterState.scope = scope;
        rasterState.contentDirty = false;
        rasterState.worldTime = worldTime;

        ensureRasterTexture();
        if (rasterState.texture != null) {
            rasterState.texture.upload(rasterState.colors, rasterState.columns, rasterState.rows);
        }
    }

    private void drawRaster(
            DrawContext context,
            Bounds bounds,
            int pixel,
            WorldMapRasterViewport.State viewport
    ) {
        if (rasterState.columns <= 0 || rasterState.rows <= 0 || rasterState.colors.length == 0) return;
        ensureRasterTexture();
        if (rasterState.texture == null || !rasterState.texture.ready()) return;

        int rasterX = bounds.centerX() + (-rasterState.halfCellsX - 2) * pixel + viewport.drawOffsetX();
        int rasterY = bounds.centerY() + (-rasterState.halfCellsZ - 2) * pixel + viewport.drawOffsetZ();
        int drawWidth = rasterState.columns * pixel;
        int drawHeight = rasterState.rows * pixel;

        context.enableScissor(bounds.left, bounds.top, bounds.right, bounds.bottom);
        rasterState.texture.draw(context, rasterX, rasterY, drawWidth, drawHeight);
        context.disableScissor();
    }

    private void renderPlayerMarker(DrawContext context, Bounds bounds) {
        if (client == null || client.player == null) return;
        double scale = 1.0 / blocksPerPixel();
        int px = bounds.centerX() + (int) Math.round((client.player.getX() - camera.centerX) * scale);
        int pz = bounds.centerY() + (int) Math.round((client.player.getZ() - camera.centerZ) * scale);
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

    private void renderSidebar(DrawContext context, int mouseX, int mouseY) {
        sidebarPanel.render(
                context,
                textRenderer,
                NAVIGATION,
                sidebarView(),
                width,
                height,
                mouseX,
                mouseY);
    }

    private WorldMapSidebarPanel.View sidebarView() {
        UUID currentId = currentWorldId();
        String currentName = maps.currentWorld() == null ? "Loading…" : maps.currentWorld().displayName();
        String dimensionPath = client != null && client.world != null
                ? client.world.getRegistryKey().getValue().getPath()
                : "";
        return new WorldMapSidebarPanel.View(
                sidebarCollapsed,
                showAllWorlds,
                selectedWorldId,
                currentId,
                currentName,
                dimensionPath,
                sidebarWorlds(),
                worlds.canTeleport(),
                teleportBusy());
    }

    private void renderExportSidebar(DrawContext context, int mouseX, int mouseY) {
        exportPanel.render(
                context,
                textRenderer,
                exportWorkspace,
                exportWorkspace.initializedFor(currentWorldId()),
                worlds.lastError(),
                maps.lastError(),
                exportBusy(),
                width,
                height,
                mouseX,
                mouseY);
    }

    private void renderBottomStatus(DrawContext context, int mouseX, int mouseY) {
        Bounds map = mapBounds();
        int left = map.left;
        int right = map.right;
        context.fill(left, height - BOTTOM_BAR, right, height, 0xE314181E);
        context.fill(left, height - BOTTOM_BAR, right, height - BOTTOM_BAR + 1, LbUi.BORDER);

        int[] hovered = screenToWorld(mouseX, mouseY);
        String coordinates = hovered == null ? "" : "Cursor  X " + hovered[0] + "   Z " + hovered[1];
        String status = teleportBusy() ? "Teleporting…"
                : SURFACE.pendingCount() > 0 ? "Loading map…"
                : "";

        renderZoomControl(context, mouseX, mouseY);
        renderCompactAction(context, recenterMapRect(), "Recenter", false, mouseX, mouseY);

        if (exportWorkspace.active() && areaSelection.active) {
            int chunksX = areaSelection.maxChunkX - areaSelection.minChunkX + 1;
            int chunksZ = areaSelection.maxChunkZ - areaSelection.minChunkZ + 1;
            String chunkSummary = chunksX + " × " + chunksZ + " chunks";
            String fullSummary = chunkSummary + "  ·  "
                    + chunksX * CHUNK_BLOCKS + " × " + chunksZ * CHUNK_BLOCKS + " blocks";
            int available = Math.max(0, map.width() / 3);
            String summary = textRenderer.getWidth(fullSummary) <= available ? fullSummary : chunkSummary;
            context.drawTextWithShadow(textRenderer, Text.literal(trim(summary, available)),
                    left + 8, height - 16, LbUi.ACCENT_BRIGHT);
        }

        if (!coordinates.isBlank()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(coordinates),
                    map.centerX(), height - 16, LbUi.TEXT_SECONDARY);
        }

        if (!status.isBlank()) {
            int statusWidth = textRenderer.getWidth(status);
            int statusRight = zoomLabelRect().left - 10;
            int statusX = statusRight - statusWidth;
            if (statusX > recenterMapRect().right + 10) {
                context.drawTextWithShadow(textRenderer, Text.literal(status), statusX, height - 16, LbUi.TEXT_MUTED);
            }
        }
    }

    private void renderZoomControl(DrawContext context, int mouseX, int mouseY) {
        Rect label = zoomLabelRect();
        Rect minus = zoomMinusRect();
        Rect plus = zoomPlusRect();
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(zoomLabel()),
                (label.left + label.right) / 2, label.top + 5, LbUi.TEXT_MUTED);
        context.fill(minus.left, minus.top, minus.right, minus.bottom,
                minus.contains(mouseX, mouseY) ? LbUi.SURFACE_3 : LbUi.SURFACE_2);
        context.fill(plus.left, plus.top, plus.right, plus.bottom,
                plus.contains(mouseX, mouseY) ? LbUi.SURFACE_3 : LbUi.SURFACE_2);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("−"), (minus.left + minus.right) / 2, minus.top + 5, LbUi.TEXT_PRIMARY);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("+"), (plus.left + plus.right) / 2, plus.top + 5, LbUi.TEXT_PRIMARY);
    }

    private void renderCompactAction(DrawContext context, Rect rect, String label, boolean primary, int mouseX, int mouseY) {
        int base = primary ? LbUi.ACCENT_FILL : LbUi.SURFACE_2;
        int hover = primary ? LbUi.ACCENT_HOVER : LbUi.SURFACE_3;
        context.fill(rect.left(), rect.top(), rect.right(), rect.bottom(), rect.contains(mouseX, mouseY) ? hover : base);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label),
                (rect.left() + rect.right()) / 2, rect.top() + 6, LbUi.TEXT_PRIMARY);
    }

    private void renderContextMenu(DrawContext context, int mouseX, int mouseY) {
        Bounds bounds = mapBounds();
        contextMenu.render(
                context,
                textRenderer,
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                maps.currentWorld() != null && worlds.canTeleport() && !teleportBusy(),
                teleportBusy(),
                maps.currentWorld() != null && worlds.canManage(),
                mouseX,
                mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (exportWorkspace.active()) {
            if (button == 0) {
                if (exportPanel.clickNameField(mouseX, mouseY, button)) return true;
                MapExportWorkspacePanel.Action action = exportPanel.actionAt(
                        exportWorkspace,
                        exportWorkspace.initializedFor(currentWorldId()),
                        width,
                        height,
                        mouseX,
                        mouseY);
                if (handleExportPanelAction(action)) return true;
            }
            if (exportPanel.panelRect(width, height).contains(mouseX, mouseY)) return true;
        } else {
            if (button == 0 && sidebarPanel.contains(mouseX, width, sidebarCollapsed)) {
                WorldMapSidebarPanel.Action action = sidebarPanel.actionAt(
                        sidebarView(),
                        width,
                        height,
                        mouseX,
                        mouseY);
                handleSidebarAction(action);
                return true;
            }
        }

        if (!exportWorkspace.active() && contextMenu.isOpen()) {
            Bounds bounds = mapBounds();
            MapContextMenuPanel.Action action = button == 0
                    ? contextMenu.actionAt(bounds.left, bounds.top, bounds.right, bounds.bottom, mouseX, mouseY)
                    : MapContextMenuPanel.Action.NONE;
            switch (action) {
                case TELEPORT -> {
                    if (maps.currentWorld() != null && worlds.canTeleport() && !teleportBusy()) {
                        closeAfterMapTeleport = true;
                        maps.teleportCurrent(contextMenu.blockX(), contextMenu.blockZ());
                        contextMenu.close();
                    }
                    return true;
                }
                case EXPORT_AREA -> {
                    if (maps.currentWorld() != null && worlds.canManage()) {
                        enterExportWorkspace(
                                MapExportWorkspaceState.Scope.CUSTOM_AREA,
                                contextMenu.blockX(),
                                contextMenu.blockZ());
                    }
                    return true;
                }
                case COPY_COORDINATES -> {
                    if (client != null) {
                        client.keyboard.setClipboard(contextMenu.blockX() + ", " + contextMenu.blockZ());
                    }
                    contextMenu.close();
                    return true;
                }
                case DISMISS -> {
                    contextMenu.close();
                    return true;
                }
                case NONE -> {
                }
            }
        }

        if (recenterMapRect().contains(mouseX, mouseY) && button == 0) {
            centerOnPlayer();
            return true;
        }
        if (zoomMinusRect().contains(mouseX, mouseY) && button == 0) {
            discreteZoom(1, mapBounds().centerX(), mapBounds().centerY());
            return true;
        }
        if (zoomPlusRect().contains(mouseX, mouseY) && button == 0) {
            discreteZoom(-1, mapBounds().centerX(), mapBounds().centerY());
            return true;
        }

        if (!mapBounds().contains(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 2) {
            centerOnPlayer();
            return true;
        }
        if (areaSelection.active) {
            if (button == 1) return true;
            if (button == 0) {
                DragMode hit = hitSelection(mouseX, mouseY);
                int[] chunk = screenToChunk(mouseX, mouseY);
                if (chunk == null) return true;
                if (hit != DragMode.NONE) beginSelectionDrag(hit, chunk[0], chunk[1]);
                else camera.dragging = true;
                return true;
            }
            return true;
        }
        if (!exportWorkspace.active() && button == 1) {
            int[] world = screenToWorld(mouseX, mouseY);
            if (world == null) return true;
            contextMenu.open(world[0], world[1], (int) mouseX, (int) mouseY);
            return true;
        }
        if (button == 0) {
            camera.dragging = true;
            contextMenu.close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleSidebarAction(WorldMapSidebarPanel.Action action) {
        switch (action.type()) {
            case EXPAND -> {
                if (!mustCollapseSidebar()) {
                    sidebarCollapsed = false;
                    sidebarManuallyToggled = true;
                    invalidateRasterViewport();
                }
            }
            case COLLAPSE -> {
                sidebarCollapsed = true;
                sidebarManuallyToggled = true;
                contextMenu.close();
                invalidateRasterViewport();
            }
            case SELECT_CURRENT -> selectedWorldId = action.worldId();
            case TOGGLE_CURRENT_PIN, TOGGLE_ROW_PIN -> {
                if (action.worldId() != null) {
                    NAVIGATION.togglePinned(action.worldId());
                    if (!showAllWorlds) normalizeSelectionForSection();
                }
            }
            case SHOW_FAVORITES -> {
                if (showAllWorlds) {
                    showAllWorlds = false;
                    worldListOffset = 0;
                    normalizeSelectionForSection();
                }
            }
            case SHOW_ALL -> {
                if (!showAllWorlds) {
                    showAllWorlds = true;
                    worldListOffset = 0;
                }
            }
            case SELECT_ROW -> selectedWorldId = action.worldId();
            case TELEPORT_SELECTED -> {
                WorldControlWireProtocol.WorldSummary selected = findActiveWorld(action.worldId());
                if (selected != null && !isCurrentWorld(selected.worldId())
                        && worlds.canTeleport() && !teleportBusy()) {
                    closeAfterWorldTeleport = true;
                    worlds.teleport(selected.worldId());
                }
            }
            case MANAGE -> {
                if (client != null) client.setScreen(new WorldManagerScreen(this, worlds, transfers, maps));
            }
            case NONE -> {
            }
        }
    }

    private boolean handleExportPanelAction(MapExportWorkspacePanel.Action action) {
        return switch (action) {
            case BACK -> {
                exitExportWorkspace();
                yield true;
            }
            case FULL_SCOPE -> {
                setExportScope(MapExportWorkspaceState.Scope.FULL_WORLD);
                yield true;
            }
            case AREA_SCOPE -> {
                setExportScope(MapExportWorkspaceState.Scope.CUSTOM_AREA);
                yield true;
            }
            case CYCLE_FORMAT -> {
                exportWorkspace.cycleFormat(worlds.exportFormats());
                yield true;
            }
            case SUBMIT -> {
                submitExport();
                yield true;
            }
            case TOGGLE_WORLD_SETTINGS -> {
                exportWorkspace.toggleWorldSettings();
                exportWorkspace.clampScroll(exportPanel.maxScroll(exportWorkspace, width, height));
                yield true;
            }
            case USE_CURRENT_POSITION -> {
                if (client != null && client.player != null) {
                    exportWorkspace.useSpawn(
                            client.player.getBlockX(),
                            client.player.getBlockY(),
                            client.player.getBlockZ());
                }
                yield true;
            }
            case NONE -> false;
        };
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        if (areaSelection.active && areaSelection.dragging()) {
            int[] chunk = screenToChunk(mouseX, mouseY);
            if (chunk != null) updateSelectionDrag(chunk[0], chunk[1]);
            return true;
        }
        if (camera.dragging) {
            camera.panByPixels(deltaX, deltaY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean handled = camera.dragging || areaSelection.dragging();
            camera.dragging = false;
            areaSelection.endDrag();
            if (handled) return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (exportWorkspace.active()
                && exportPanel.advancedViewport(width, height).contains(mouseX, mouseY)
                && verticalAmount != 0) {
            exportWorkspace.scrollBy(
                    -(int) Math.signum(verticalAmount) * 26,
                    exportPanel.maxScroll(exportWorkspace, width, height));
            return true;
        }
        if (!exportWorkspace.active() && !sidebarCollapsed && mouseX < sidebarWidth() && showAllWorlds && verticalAmount != 0) {
            int maxOffset = Math.max(0, activeWorlds().size() - visibleSidebarRows());
            worldListOffset = Math.max(0, Math.min(maxOffset,
                    worldListOffset - (int) Math.signum(verticalAmount)));
            return true;
        }
        if (!mapBounds().contains(mouseX, mouseY) || verticalAmount == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (hasControlDown()) preciseZoom(verticalAmount < 0 ? 1 : -1, mouseX, mouseY);
        else discreteZoom(verticalAmount < 0 ? 1 : -1, mouseX, mouseY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (exportWorkspace.active() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            exitExportWorkspace();
            return true;
        }
        if (exportWorkspace.active() && exportPanel.nameFieldFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_M) {
            closeFromToggle();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeFromToggle();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            centerOnPlayer();
            return true;
        }
        if (exportWorkspace.active() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            submitExport();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C && hasShiftDown()) {
            copyReviewReference();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void enterExportWorkspace(MapExportWorkspaceState.Scope scope, int blockX, int blockZ) {
        if (maps.currentWorld() == null || !worlds.canManage()) return;
        exportWorkspace.enter();
        exportWorkspace.scope(scope);
        contextMenu.close();
        if (scope == MapExportWorkspaceState.Scope.CUSTOM_AREA) initializeAreaSelection(blockX, blockZ);
        else areaSelection.active = false;
        invalidateRasterViewport();
        ensureExportData(false);
        clearAndInit();
    }

    void openExportWorkspace(boolean customArea) {
        int blockX = client != null && client.player != null ? client.player.getBlockX() : 0;
        int blockZ = client != null && client.player != null ? client.player.getBlockZ() : 0;
        enterExportWorkspace(customArea
                ? MapExportWorkspaceState.Scope.CUSTOM_AREA
                : MapExportWorkspaceState.Scope.FULL_WORLD, blockX, blockZ);
    }

    void openExportWorkspaceForProof(boolean customArea) {
        openExportWorkspace(customArea);
    }

    void openExportWorkspaceForProof(boolean customArea, int blockX, int blockZ) {
        camera.centerOn(blockX, blockZ);
        enterExportWorkspace(customArea
                ? MapExportWorkspaceState.Scope.CUSTOM_AREA
                : MapExportWorkspaceState.Scope.FULL_WORLD, blockX, blockZ);
    }

    void expandWorldSettingsForProof() {
        if (!exportWorkspace.worldSettingsExpanded()) exportWorkspace.toggleWorldSettings();
    }

    private void exitExportWorkspace() {
        exportWorkspace.exit();
        exportPanel.clearControls();
        clearAreaSelection();
        invalidateRasterViewport();
        clearAndInit();
    }

    private void setExportScope(MapExportWorkspaceState.Scope scope) {
        exportWorkspace.scope(scope);
        if (scope == MapExportWorkspaceState.Scope.FULL_WORLD) {
            areaSelection.active = false;
            areaSelection.endDrag();
            camera.dragging = false;
            invalidateRasterViewport();
            return;
        }
        UUID currentId = currentWorldId();
        if (areaSelection.ownsWorld(currentId)) {
            areaSelection.active = true;
            invalidateRasterViewport();
            return;
        }
        int blockX = client != null && client.player != null ? client.player.getBlockX() : (int) Math.floor(camera.centerX);
        int blockZ = client != null && client.player != null ? client.player.getBlockZ() : (int) Math.floor(camera.centerZ);
        initializeAreaSelection(blockX, blockZ);
    }

    private void initializeAreaSelection(int blockX, int blockZ) {
        var current = maps.currentWorld();
        if (current == null) return;
        int centerChunkX = Math.floorDiv(blockX, CHUNK_BLOCKS);
        int centerChunkZ = Math.floorDiv(blockZ, CHUNK_BLOCKS);
        int before = DEFAULT_SELECTION_CHUNKS / 2;
        int after = DEFAULT_SELECTION_CHUNKS - before - 1;
        areaSelection.activate(
                current.worldId().value(),
                centerChunkX - before,
                centerChunkX + after,
                centerChunkZ - before,
                centerChunkZ + after);
        areaSelection.endDrag();
        camera.dragging = false;
        contextMenu.close();
        invalidateRasterViewport();
    }

    private void submitExport() {
        UUID currentId = currentWorldId();
        if (!exportWorkspace.active() || currentId == null || !exportWorkspace.initializedFor(currentId) || exportBusy()) return;
        String artifact = exportWorkspace.artifactName();
        artifact = artifact == null ? "" : artifact.strip();
        if (artifact.isEmpty()) {
            LazyBuilderClientNetworking.notifyPlayer("Choose a world name before exporting.");
            return;
        }
        exportWorkspace.artifactName(artifact);
        exportPanel.updateNameField(artifact);

        if (exportWorkspace.scope() == MapExportWorkspaceState.Scope.CUSTOM_AREA) {
            if (!areaSelection.active || !areaSelection.ownsWorld(currentId)) {
                LazyBuilderClientNetworking.notifyPlayer("Choose an area in the current world before exporting.");
                return;
            }
            maps.exportAreaCurrent(
                    minBlockX(), minBlockZ(), maxBlockX(), maxBlockZ(),
                    exportWorkspace.format(), artifact, exportWorkspace.toWire());
            return;
        }
        worlds.exportWorld(currentId, exportWorkspace.format(), artifact, exportWorkspace.toWire());
    }

    private boolean exportBusy() {
        return maps.exportBusy() || worlds.exportPending();
    }

    private void clearAreaSelection() {
        areaSelection.clear();
        areaSelection.endDrag();
        camera.dragging = false;
        contextMenu.close();
        invalidateRasterViewport();
    }

    private void copyReviewReference() {
        var current = maps.currentWorld();
        if (client == null || client.player == null || client.world == null || current == null) {
            LazyBuilderClientNetworking.notifyPlayer(
                    "LazyBuilder: open a managed world before copying location details.");
            return;
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
        String reference = "World: " + current.displayName() + "\n"
                + "World ID: " + current.worldId() + "\n"
                + "Location: " + client.player.getBlockX() + " "
                + client.player.getBlockY() + " " + client.player.getBlockZ() + "\n"
                + "Dimension: " + dimension;
        client.keyboard.setClipboard(reference);
        LazyBuilderClientNetworking.notifyPlayer("Location details copied.");
    }

    private void renderSelectionGrid(DrawContext context) {
        Bounds bounds = mapBounds();
        double bpp = blocksPerPixel();
        double chunkPixels = CHUNK_BLOCKS / bpp;
        double regionPixels = REGION_BLOCKS / bpp;
        double worldLeft = camera.centerX + (bounds.left - bounds.centerX()) * bpp;
        double worldRight = camera.centerX + (bounds.right - bounds.centerX()) * bpp;
        double worldTop = camera.centerZ + (bounds.top - bounds.centerY()) * bpp;
        double worldBottom = camera.centerZ + (bounds.bottom - bounds.centerY()) * bpp;

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
        int left = Math.max(bounds.left, rect.left());
        int right = Math.min(bounds.right, rect.right());
        int top = Math.max(bounds.top, rect.top());
        int bottom = Math.min(bounds.bottom, rect.bottom());
        if (right <= left || bottom <= top) return;

        if (top > bounds.top) context.fill(bounds.left, bounds.top, bounds.right, top, OUTSIDE_SELECTION_DIM);
        if (bottom < bounds.bottom) context.fill(bounds.left, bottom, bounds.right, bounds.bottom, OUTSIDE_SELECTION_DIM);
        if (left > bounds.left) context.fill(bounds.left, top, left, bottom, OUTSIDE_SELECTION_DIM);
        if (right < bounds.right) context.fill(right, top, bounds.right, bottom, OUTSIDE_SELECTION_DIM);

        context.fill(left, top, right, top + 2, LbUi.ACCENT_BRIGHT);
        context.fill(left, bottom - 2, right, bottom, LbUi.ACCENT_BRIGHT);
        context.fill(left, top, left + 2, bottom, LbUi.ACCENT_BRIGHT);
        context.fill(right - 2, top, right, bottom, LbUi.ACCENT_BRIGHT);

        DragMode hover = hitSelection(mouseX, mouseY);
        for (Handle handle : MapAreaSelectionGeometry.handles(rect)) {
            int radius = handle.mode() == hover || handle.mode() == areaSelection.dragMode() ? HANDLE_RADIUS + 1 : HANDLE_RADIUS;
            int color = handle.mode() == hover || handle.mode() == areaSelection.dragMode() ? LbUi.TEXT_PRIMARY : LbUi.ACCENT_BRIGHT;
            context.fill(handle.x() - radius, handle.y() - radius, handle.x() + radius + 1, handle.y() + radius + 1, 0xAA10151C);
            context.fill(handle.x() - radius + 2, handle.y() - radius + 2,
                    handle.x() + radius - 1, handle.y() + radius - 1, color);
        }
    }

    private void renderCursor(DrawContext context, int mouseX, int mouseY) {
        if (contextMenu.isOpen() || !mapBounds().contains(mouseX, mouseY)) return;
        int color = areaSelection.active ? 0xBB8AA8FF : 0x667F8A98;
        context.fill(mouseX - 5, mouseY, mouseX + 6, mouseY + 1, color);
        context.fill(mouseX, mouseY - 5, mouseX + 1, mouseY + 6, color);
    }

    private void beginSelectionDrag(DragMode mode, int chunkX, int chunkZ) {
        areaSelection.beginDrag(mode, chunkX, chunkZ);
    }

    private void updateSelectionDrag(int chunkX, int chunkZ) {
        areaSelection.updateDrag(chunkX, chunkZ);
    }

    private DragMode hitSelection(double mouseX, double mouseY) {
        if (!areaSelection.active) return DragMode.NONE;
        SelectionRect rect = selectionRect();
        return rect == null
                ? DragMode.NONE
                : MapAreaSelectionGeometry.hit(rect, mouseX, mouseY, HANDLE_RADIUS);
    }

    private SelectionRect selectionRect() {
        if (!areaSelection.active) return null;
        Bounds bounds = mapBounds();
        int left = worldToScreenX(minBlockX(), bounds);
        int right = worldToScreenX((areaSelection.maxChunkX + 1) * CHUNK_BLOCKS, bounds);
        int top = worldToScreenZ(minBlockZ(), bounds);
        int bottom = worldToScreenZ((areaSelection.maxChunkZ + 1) * CHUNK_BLOCKS, bounds);
        return MapAreaSelectionGeometry.rect(left, top, right, bottom);
    }

    private int minBlockX() { return areaSelection.minBlockX(CHUNK_BLOCKS); }
    private int maxBlockX() { return areaSelection.maxBlockX(CHUNK_BLOCKS); }
    private int minBlockZ() { return areaSelection.minBlockZ(CHUNK_BLOCKS); }
    private int maxBlockZ() { return areaSelection.maxBlockZ(CHUNK_BLOCKS); }

    private void discreteZoom(int direction, double screenX, double screenY) {
        int current = nearestZoomIndex(camera.zoom);
        int next = Math.max(0, Math.min(ZOOM_STEPS.length - 1, current + direction));
        setZoom(ZOOM_STEPS[next], screenX, screenY);
    }

    private void preciseZoom(int direction, double screenX, double screenY) {
        double next = direction > 0 ? camera.zoom * PRECISE_ZOOM_FACTOR : camera.zoom / PRECISE_ZOOM_FACTOR;
        setZoom(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, next)), screenX, screenY);
    }

    private void setZoom(double next, double screenX, double screenY) {
        if (Math.abs(next - camera.zoom) < 0.0001) return;
        double[] anchor = screenToWorldExact(screenX, screenY);
        camera.zoom = next;
        if (anchor != null) {
            Bounds bounds = mapBounds();
            double bpp = blocksPerPixel();
            camera.centerX = anchor[0] - (screenX - bounds.centerX()) * bpp;
            camera.centerZ = anchor[1] - (screenY - bounds.centerY()) * bpp;
        }
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

    private void centerOnPlayer() {
        if (client != null && client.player != null) {
            camera.centerOn(client.player.getX(), client.player.getZ());
        }
    }

    private int mapPixelSize() {
        if (camera.zoom <= 2.0) return 1;
        if (camera.zoom <= 8.0) return 2;
        return 3;
    }

    private double blocksPerPixel() { return camera.blocksPerPixel(); }

    private double[] screenToWorldExact(double mouseX, double mouseY) {
        Bounds bounds = mapBounds();
        if (!bounds.contains(mouseX, mouseY)) return null;
        return camera.worldAtScreen(mouseX, mouseY, bounds.centerX(), bounds.centerY());
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
        return bounds.centerX() + (int) Math.round((blockX - camera.centerX) / blocksPerPixel());
    }

    private int worldToScreenZ(int blockZ, Bounds bounds) {
        return bounds.centerY() + (int) Math.round((blockZ - camera.centerZ) / blocksPerPixel());
    }

    private String zoomLabel() {
        if (Math.abs(camera.zoom - Math.rint(camera.zoom)) < 0.01) return "Zoom " + (int) Math.rint(camera.zoom) + "×";
        return String.format(Locale.ROOT, "Zoom %.1f×", camera.zoom);
    }

    private boolean shouldAutoCollapseSidebar() { return width < 520 || height < 260; }
    private boolean mustCollapseSidebar() { return width < 400 || height < 220; }

    private int sidebarWidth() {
        return sidebarPanel.sidebarWidth(width, sidebarCollapsed);
    }

    private Bounds mapBounds() {
        if (exportWorkspace.active()) {
            return new Bounds(0, 0, Math.max(1, width - exportPanel.sidebarWidth(width) - 1), height - BOTTOM_BAR);
        }
        return new Bounds(sidebarWidth() + SIDEBAR_GAP, 0, width, height - BOTTOM_BAR);
    }

    private Rect recenterMapRect() {
        Bounds map = mapBounds();
        int center = map.centerX();
        int buttonWidth = 116;
        int bottom = map.bottom - 20;
        return new Rect(center - buttonWidth / 2, bottom - 22, center + buttonWidth / 2, bottom);
    }
    private Rect zoomPlusRect() {
        int right = mapBounds().right - 8;
        return new Rect(right - 20, height - 22, right, height - 3);
    }
    private Rect zoomMinusRect() {
        int right = zoomPlusRect().left - 3;
        return new Rect(right - 20, height - 22, right, height - 3);
    }
    private Rect zoomLabelRect() {
        int labelWidth = 58;
        int right = zoomMinusRect().left - 6;
        return new Rect(right - labelWidth, height - 22, right, height - 3);
    }

    private List<WorldControlWireProtocol.WorldSummary> activeWorlds() {
        return worlds.worlds().stream()
                .filter(world -> "ACTIVE".equals(world.lifecycle()))
                .sorted(Comparator.comparing(WorldControlWireProtocol.WorldSummary::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<WorldControlWireProtocol.WorldSummary> sidebarWorlds() {
        List<WorldControlWireProtocol.WorldSummary> active = activeWorlds();
        if (showAllWorlds) {
            int from = Math.max(0, Math.min(worldListOffset, active.size()));
            int to = Math.min(active.size(), from + visibleSidebarRows());
            return active.subList(from, to);
        }
        List<WorldControlWireProtocol.WorldSummary> result = new ArrayList<>();
        UUID current = currentWorldId();
        for (UUID id : NAVIGATION.pinned()) {
            if (current != null && current.equals(id)) continue;
            WorldControlWireProtocol.WorldSummary world = find(active, id);
            if (world != null) result.add(world);
            if (result.size() >= MAX_FAVORITES) break;
        }
        return result;
    }

    private void normalizeSelectionForSection() {
        if (selectedWorldId == null || isCurrentWorld(selectedWorldId) || showAllWorlds) return;
        boolean available = sidebarWorlds().stream().anyMatch(world -> world.worldId().equals(selectedWorldId));
        if (!available) selectedWorldId = currentWorldId();
    }

    private int visibleSidebarRows() {
        return sidebarPanel.visibleRows(height, sidebarCollapsed);
    }

    private WorldControlWireProtocol.WorldSummary findActiveWorld(UUID id) {
        if (id == null) return null;
        return find(activeWorlds(), id);
    }

    private static WorldControlWireProtocol.WorldSummary find(List<WorldControlWireProtocol.WorldSummary> source, UUID id) {
        return source.stream().filter(world -> world.worldId().equals(id)).findFirst().orElse(null);
    }

    private UUID currentWorldId() { return maps.currentWorld() == null ? null : maps.currentWorld().worldId().value(); }
    private boolean teleportBusy() { return maps.teleportPending() || worlds.teleportPending(); }
    private boolean isCurrentWorld(UUID id) { UUID current = currentWorldId(); return current != null && current.equals(id); }

    private String trim(String value, int maxWidth) {
        if (value == null || maxWidth <= 0) return "";
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String base = value;
        while (base.length() > 1 && textRenderer.getWidth(base + "…") > maxWidth) base = base.substring(0, base.length() - 1);
        return textRenderer.getWidth(base + "…") <= maxWidth ? base + "…" : "";
    }

    private void invalidateRasterViewport() {
        rasterState.invalidateLayout();
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        super.removed();
        rasterState.closeTexture();
    }

    @Override
    public void close() {
        if (exportWorkspace.active()) exitExportWorkspace();
        else closeFromToggle();
        if (client != null && previousMenuBlur != null) {
            client.options.getMenuBackgroundBlurriness().setValue(previousMenuBlur);
            previousMenuBlur = null;
        }
    }

    void closeFromToggle() {
        clearAreaSelection();
        SURFACE.flushAsync();
        if (client != null) client.setScreen(null);
    }

    private record Rect(int left, int top, int right, int bottom) {
        boolean contains(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }
        int width() { return right - left; }
        int height() { return bottom - top; }
    }
    private record Bounds(int left, int top, int right, int bottom) {
        int width() { return right - left; }
        int height() { return bottom - top; }
        int centerX() { return left + width() / 2; }
        int centerY() { return top + height() / 2; }
        boolean contains(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }
    }
}
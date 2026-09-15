package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
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
 * Fullscreen LazyBuilder world map and lightweight managed-world navigator.
 *
 * <p>World administration remains in {@link WorldManagerScreen}. This screen owns only
 * spatial presentation, quick navigation, and map-local actions. UI state never becomes
 * authoritative world state, and presentation changes do not reset the terrain cache.</p>
 */
public final class WorldMapScreen extends Screen {
    private static final int BOTTOM_BAR = 24;
    private static final int SELECTION_BOTTOM_BAR = 64;
    private static final int COLLAPSED_SIDEBAR = 34;
    private static final int MIN_SIDEBAR = 152;
    private static final int MAX_SIDEBAR = 176;
    private static final int SIDEBAR_GAP = 1;
    private static final int SAMPLE_BUDGET_PER_TICK = 4096;
    private static final int RASTER_REFRESH_INTERVAL_FRAMES = 12;
    private static final int CHUNK_BLOCKS = 16;
    private static final int REGION_BLOCKS = 512;
    private static final int DEFAULT_SELECTION_CHUNKS = 8;
    private static final int HANDLE_RADIUS = 5;
    private static final int CHUNK_GRID_COLOR = 0x2EFFFFFF;
    private static final int REGION_GRID_COLOR = 0x667F8FA3;
    private static final int OUTSIDE_SELECTION_DIM = 0x48101418;
    private static final int SIDEBAR_ROW_HEIGHT = 27;
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

    private double centerX;
    private double centerZ;
    private double zoom = 2.0;
    private boolean centeredOnce;
    private boolean draggingMap;
    private boolean initialLayoutApplied;
    private boolean sidebarCollapsed;
    private boolean sidebarManuallyToggled;
    private boolean showAllWorlds;
    private int worldListOffset;
    private UUID selectedWorldId;

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

    private int[] rasterColors = new int[0];
    private int rasterColumns;
    private int rasterRows;
    private int rasterHalfCellsX;
    private int rasterHalfCellsZ;
    private int rasterPixel = -1;
    private int rasterAgeFrames = RASTER_REFRESH_INTERVAL_FRAMES;
    private int rasterLeft;
    private int rasterTop;
    private int rasterRight;
    private int rasterBottom;
    private double rasterCenterX = Double.NaN;
    private double rasterCenterZ = Double.NaN;
    private double rasterZoom = Double.NaN;
    private String rasterScope = "";

    private boolean requestedCurrentWorld;
    private long observedWorldRevision;
    private long observedMapRevision;
    private UUID observedCurrentWorldId;
    private boolean closeAfterWorldTeleport;
    private boolean closeAfterMapTeleport;

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
        if (!centeredOnce) {
            centerOnPlayer();
            centeredOnce = true;
        }
        boolean autoCollapsed = shouldAutoCollapseSidebar();
        if (!initialLayoutApplied) {
            sidebarCollapsed = autoCollapsed;
            initialLayoutApplied = true;
        } else if (!sidebarManuallyToggled || mustCollapseSidebar()) {
            if (sidebarCollapsed != autoCollapsed) {
                sidebarCollapsed = autoCollapsed;
                contextOpen = false;
                invalidateRasterViewport();
            }
        }
        NAVIGATION.reload();
        if (!worlds.worldListReady() && !worlds.worldListPending()) worlds.refresh();
        if (!requestedCurrentWorld) {
            requestedCurrentWorld = true;
            maps.refreshCurrentWorld();
        }
        observeCurrentWorld();
        observedWorldRevision = worlds.revision();
        observedMapRevision = maps.revision();
    }

    @Override
    public void tick() {
        if (observedMapRevision != maps.revision()) {
            observedMapRevision = maps.revision();
            observeCurrentWorld();
        }
        if (observedWorldRevision != worlds.revision()) {
            observedWorldRevision = worlds.revision();
            clampSelectedWorld();
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

        if (areaMode) {
            var current = maps.currentWorld();
            if (current == null || selectionWorldId == null || !selectionWorldId.equals(current.worldId().value())) {
                clearAreaSelection();
                LazyBuilderClientNetworking.notifyPlayer(
                        "LazyBuilder: area selection cleared because the current world changed.");
            }
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
        if (areaMode) {
            renderSelectionGrid(context);
            renderSelection(context, mouseX, mouseY);
        }
        renderCursor(context, mouseX, mouseY);
        renderSidebar(context, mouseX, mouseY);
        renderBottomStatus(context, mouseX, mouseY);
        renderContextMenu(context, mouseX, mouseY);
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

        if (!scope.equals(rasterScope) && !rasterScope.isBlank()) {
            rasterColors = new int[0];
            rasterColumns = 0;
            rasterRows = 0;
        }
        SURFACE.useScope(scope, storage);
        SURFACE.processPending(world, SAMPLE_BUDGET_PER_TICK);

        int pixel = mapPixelSize();
        if (rasterNeedsRefresh(scope, bounds, pixel)) {
            rebuildRaster(world, scope, bounds, pixel);
        } else {
            rasterAgeFrames++;
        }
        drawRaster(context, bounds, pixel);
        renderPlayerMarker(context, bounds);
    }

    private boolean rasterNeedsRefresh(String scope, Bounds bounds, int pixel) {
        return rasterColors.length == 0
                || rasterAgeFrames >= RASTER_REFRESH_INTERVAL_FRAMES
                || rasterPixel != pixel
                || rasterLeft != bounds.left
                || rasterTop != bounds.top
                || rasterRight != bounds.right
                || rasterBottom != bounds.bottom
                || Double.compare(rasterCenterX, centerX) != 0
                || Double.compare(rasterCenterZ, centerZ) != 0
                || Double.compare(rasterZoom, zoom) != 0
                || !rasterScope.equals(scope);
    }

    private void rebuildRaster(ClientWorld world, String scope, Bounds bounds, int pixel) {
        double blocksPerCell = zoom * pixel / 2.0;
        int sampleSpan = Math.max(1, (int) Math.ceil(blocksPerCell));
        int halfCellsX = Math.max(1, bounds.width() / pixel / 2);
        int halfCellsZ = Math.max(1, bounds.height() / pixel / 2);
        int columns = halfCellsX * 2 + 5;
        int rows = halfCellsZ * 2 + 5;
        int[] nextColors = new int[columns * rows];
        java.util.Arrays.fill(nextColors, ClientMapSurfaceCache.UNEXPLORED_COLOR);

        double originCellX = centerX / blocksPerCell;
        double originCellZ = centerZ / blocksPerCell;
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

        rasterColors = nextColors;
        rasterColumns = columns;
        rasterRows = rows;
        rasterHalfCellsX = halfCellsX;
        rasterHalfCellsZ = halfCellsZ;
        rasterPixel = pixel;
        rasterAgeFrames = 0;
        rasterLeft = bounds.left;
        rasterTop = bounds.top;
        rasterRight = bounds.right;
        rasterBottom = bounds.bottom;
        rasterCenterX = centerX;
        rasterCenterZ = centerZ;
        rasterZoom = zoom;
        rasterScope = scope;
    }

    private void drawRaster(DrawContext context, Bounds bounds, int pixel) {
        if (rasterColumns <= 0 || rasterRows <= 0 || rasterColors.length == 0) return;
        int index = 0;
        for (int row = 0; row < rasterRows; row++) {
            int cz = row - rasterHalfCellsZ - 2;
            int screenY = bounds.centerY() + cz * pixel;
            for (int column = 0; column < rasterColumns; column++) {
                int cx = column - rasterHalfCellsX - 2;
                int screenX = bounds.centerX() + cx * pixel;
                int color = rasterColors[index++];
                if (screenY + pixel < bounds.top || screenY >= bounds.bottom
                        || screenX + pixel < bounds.left || screenX >= bounds.right) continue;
                context.fill(screenX, screenY, screenX + pixel, screenY + pixel, color);
            }
        }
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

    private void renderSidebar(DrawContext context, int mouseX, int mouseY) {
        int sidebar = sidebarWidth();
        context.fill(0, 0, sidebar, height, 0xF214181E);
        context.fill(sidebar - 1, 0, sidebar, height, LbUi.BORDER);

        if (sidebarCollapsed) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("›"), sidebar / 2, 13, LbUi.TEXT_PRIMARY);
            return;
        }

        context.drawTextWithShadow(textRenderer, Text.literal("Worlds"), 12, 12, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("‹"), sidebar - 18, 12, LbUi.TEXT_MUTED);
        LbUi.divider(context, 10, 29, sidebar - 10);

        context.drawTextWithShadow(textRenderer, Text.literal("Current world"), 12, 39, LbUi.TEXT_MUTED);
        Rect currentRect = currentWorldRect();
        boolean currentHover = currentRect.contains(mouseX, mouseY);
        if (currentHover) context.fill(currentRect.left, currentRect.top, currentRect.right, currentRect.bottom, LbUi.SURFACE_2);
        UUID currentId = currentWorldId();
        String currentName = maps.currentWorld() == null ? "Loading…" : maps.currentWorld().displayName();
        context.drawTextWithShadow(textRenderer, Text.literal("●"), 12, currentRect.top + 8,
                currentId == null ? LbUi.TEXT_MUTED : LbUi.SUCCESS);
        context.drawTextWithShadow(textRenderer, Text.literal(trim(currentName, sidebar - 54)), 27,
                currentRect.top + 6, LbUi.TEXT_PRIMARY);
        if (client != null && client.world != null) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal(friendlyDimension(client.world.getRegistryKey().getValue().getPath())), 27,
                    currentRect.top + 17, LbUi.TEXT_MUTED);
        }
        if (currentId != null) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal(NAVIGATION.isPinned(currentId) ? "★" : "☆"), sidebar - 20,
                    currentRect.top + 8, NAVIGATION.isPinned(currentId) ? LbUi.ACCENT_BRIGHT : LbUi.TEXT_MUTED);
        }

        String section = showAllWorlds ? "All worlds" : "Favorites";
        String switchLabel = showAllWorlds ? "Favorites" : "All worlds ›";
        context.drawTextWithShadow(textRenderer, Text.literal(section), 12, 91, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal(switchLabel), sidebar - 12 - textRenderer.getWidth(switchLabel),
                91, LbUi.TEXT_MUTED);

        List<WorldControlWireProtocol.WorldSummary> rows = sidebarWorlds();
        int rowY = 105;
        for (int i = 0; i < rows.size() && i < visibleSidebarRows(); i++) {
            WorldControlWireProtocol.WorldSummary world = rows.get(i);
            Rect rect = new Rect(8, rowY, sidebar - 8, rowY + SIDEBAR_ROW_HEIGHT - 2);
            boolean selected = world.worldId().equals(selectedWorldId);
            boolean hover = rect.contains(mouseX, mouseY);
            if (selected) context.fill(rect.left, rect.top, rect.right, rect.bottom, LbUi.ACCENT_FILL);
            else if (hover) context.fill(rect.left, rect.top, rect.right, rect.bottom, LbUi.SURFACE_2);

            boolean pinned = NAVIGATION.isPinned(world.worldId());
            context.drawTextWithShadow(textRenderer, Text.literal(pinned ? "★" : "☆"), 12, rowY + 8,
                    pinned ? LbUi.ACCENT_BRIGHT : LbUi.TEXT_MUTED);
            context.drawTextWithShadow(textRenderer, Text.literal(trim(world.displayName(), sidebar - 49)), 28,
                    rowY + 8, LbUi.TEXT_PRIMARY);
            if (isCurrentWorld(world.worldId())) {
                context.drawTextWithShadow(textRenderer, Text.literal("●"), sidebar - 20, rowY + 8, LbUi.SUCCESS);
            }
            rowY += SIDEBAR_ROW_HEIGHT;
        }

        renderSelectedWorldAction(context, mouseX, mouseY);

        Rect manage = manageWorldsRect();
        if (manage.contains(mouseX, mouseY)) context.fill(manage.left, manage.top, manage.right, manage.bottom, LbUi.SURFACE_2);
        context.fill(10, manage.top - 6, sidebar - 10, manage.top - 5, LbUi.BORDER);
        context.drawTextWithShadow(textRenderer, Text.literal("Manage worlds"), 12, manage.top + 8, LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, Text.literal("›"), sidebar - 18, manage.top + 8, LbUi.TEXT_MUTED);
    }

    private void renderSelectedWorldAction(DrawContext context, int mouseX, int mouseY) {
        if (sidebarCollapsed || selectedWorldId == null) return;
        WorldControlWireProtocol.WorldSummary selected = findActiveWorld(selectedWorldId);
        if (selected == null) return;
        Rect action = selectedActionRect();
        if (action.top < 110) return;
        boolean current = isCurrentWorld(selected.worldId());
        boolean pending = worlds.teleportPending();
        int color = current ? LbUi.SURFACE_2 : pending ? LbUi.SURFACE_1 : LbUi.ACCENT_FILL;
        if (action.contains(mouseX, mouseY) && !pending) color = current ? LbUi.SURFACE_3 : LbUi.ACCENT_HOVER;
        context.fill(action.left, action.top, action.right, action.bottom, color);
        String label = current ? "Center on player" : pending ? "Teleporting…" : "Teleport →";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label),
                (action.left + action.right) / 2, action.top + 7,
                pending ? LbUi.TEXT_MUTED : LbUi.TEXT_PRIMARY);
    }

    private void renderBottomStatus(DrawContext context, int mouseX, int mouseY) {
        int bottom = areaMode ? SELECTION_BOTTOM_BAR : BOTTOM_BAR;
        int left = sidebarWidth();
        context.fill(left, height - bottom, width, height, 0xE314181E);
        context.fill(left, height - bottom, width, height - bottom + 1, LbUi.BORDER);

        if (areaMode) {
            int chunksX = maxChunkX - minChunkX + 1;
            int chunksZ = maxChunkZ - minChunkZ + 1;
            context.drawTextWithShadow(textRenderer, Text.literal("Export area"), left + 10, height - 49, LbUi.ACCENT_BRIGHT);
            String detail = chunksX + " × " + chunksZ + " chunks  •  X " + minChunkX + " → " + maxChunkX
                    + "  Z " + minChunkZ + " → " + maxChunkZ;
            int detailWidth = Math.max(0, areaCancelRect().left - left - 24);
            if (detailWidth > 24) {
                context.drawTextWithShadow(textRenderer, Text.literal(trim(detail, detailWidth)),
                        left + 10, height - 33, LbUi.TEXT_SECONDARY);
            }
            renderCompactAction(context, areaCancelRect(), "Cancel", false, mouseX, mouseY);
            renderCompactAction(context, areaContinueRect(), "Continue", true, mouseX, mouseY);
            return;
        }

        int[] hovered = screenToWorld(mouseX, mouseY);
        String center = hovered == null
                ? zoomLabel()
                : "X " + hovered[0] + "   Z " + hovered[1] + "   •   " + zoomLabel();
        String status = maps.teleportPending() ? "Teleporting…"
                : SURFACE.pendingCount() > 0 ? "Loading map…"
                : "M / Esc close  •  Drag move map  •  Scroll zoom  •  R center player";

        int textLeft = left + 8;
        int textRight = zoomMinusRect().left - 8;
        int availableWidth = Math.max(0, textRight - textLeft);
        int centerWidth = textRenderer.getWidth(center);
        int statusWidth = textRenderer.getWidth(status);
        int preferredCenterX = left + (width - left) / 2;
        int preferredCenterLeft = preferredCenterX - centerWidth / 2;
        int preferredCenterRight = preferredCenterLeft + centerWidth;
        boolean operationalStatus = maps.teleportPending() || SURFACE.pendingCount() > 0;

        if (availableWidth > 0) {
            if (statusWidth + 16 <= availableWidth
                    && preferredCenterLeft >= textLeft + statusWidth + 12
                    && preferredCenterRight <= textRight) {
                context.drawTextWithShadow(textRenderer, Text.literal(status), textLeft, height - 16, LbUi.TEXT_MUTED);
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(center),
                        preferredCenterX, height - 16, LbUi.TEXT_SECONDARY);
            } else if (operationalStatus && statusWidth <= availableWidth) {
                context.drawTextWithShadow(textRenderer, Text.literal(status), textLeft, height - 16, LbUi.TEXT_MUTED);
            } else {
                String compactCenter = trim(center, availableWidth);
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(compactCenter),
                        textLeft + availableWidth / 2, height - 16, LbUi.TEXT_SECONDARY);
            }
        }

        renderZoomControl(context, mouseX, mouseY);
    }

    private void renderZoomControl(DrawContext context, int mouseX, int mouseY) {
        Rect minus = zoomMinusRect();
        Rect plus = zoomPlusRect();
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
        context.fill(rect.left, rect.top, rect.right, rect.bottom, rect.contains(mouseX, mouseY) ? hover : base);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label),
                (rect.left + rect.right) / 2, rect.top + 6, LbUi.TEXT_PRIMARY);
    }

    private void renderContextMenu(DrawContext context, int mouseX, int mouseY) {
        if (!contextOpen || areaMode) return;
        Rect menu = contextMenuRect();
        context.fill(menu.left - 2, menu.top - 2, menu.right + 2, menu.bottom + 2, 0x77000000);
        LbUi.elevatedPanel(context, menu.left, menu.top, menu.width(), menu.height());
        context.drawTextWithShadow(textRenderer, Text.literal("Map actions"), menu.left + 8, menu.top + 7, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("X " + contextBlockX + "  Z " + contextBlockZ),
                menu.left + 8, menu.top + 20, LbUi.TEXT_MUTED);

        renderMenuRow(context, contextTeleportRect(), maps.teleportPending() ? "Teleporting…" : "Teleport here",
                !maps.teleportPending(), mouseX, mouseY);
        renderMenuRow(context, contextExportRect(), "Export area", maps.currentWorld() != null && worlds.canManage(), mouseX, mouseY);
        renderMenuRow(context, contextCopyRect(), "Copy coordinates", true, mouseX, mouseY);
    }

    private void renderMenuRow(DrawContext context, Rect rect, String label, boolean active, int mouseX, int mouseY) {
        if (active && rect.contains(mouseX, mouseY)) context.fill(rect.left, rect.top, rect.right, rect.bottom, LbUi.SURFACE_3);
        context.drawTextWithShadow(textRenderer, Text.literal(label), rect.left + 7, rect.top + 6,
                active ? LbUi.TEXT_PRIMARY : LbUi.TEXT_DISABLED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sidebarCollapsed && button == 0 && mouseX < sidebarWidth()) {
            if (mustCollapseSidebar()) return true;
            sidebarCollapsed = false;
            sidebarManuallyToggled = true;
            invalidateRasterViewport();
            return true;
        }

        if (!sidebarCollapsed && button == 0 && mouseX < sidebarWidth()) {
            if (mouseY < 31 && mouseX > sidebarWidth() - 32) {
                sidebarCollapsed = true;
                sidebarManuallyToggled = true;
                contextOpen = false;
                invalidateRasterViewport();
                return true;
            }
            if (currentWorldRect().contains(mouseX, mouseY)) {
                UUID currentId = currentWorldId();
                if (currentId != null && mouseX >= sidebarWidth() - 32) {
                    NAVIGATION.togglePinned(currentId);
                } else {
                    selectedWorldId = currentId;
                    centerOnPlayer();
                }
                return true;
            }
            if (mouseY >= 86 && mouseY < 104 && mouseX > sidebarWidth() - 72) {
                showAllWorlds = !showAllWorlds;
                worldListOffset = 0;
                return true;
            }

            List<WorldControlWireProtocol.WorldSummary> rows = sidebarWorlds();
            int rowY = 105;
            for (int i = 0; i < rows.size() && i < visibleSidebarRows(); i++) {
                Rect rect = new Rect(8, rowY, sidebarWidth() - 8, rowY + SIDEBAR_ROW_HEIGHT - 2);
                if (rect.contains(mouseX, mouseY)) {
                    WorldControlWireProtocol.WorldSummary world = rows.get(i);
                    if (mouseX < 27) NAVIGATION.togglePinned(world.worldId());
                    else selectedWorldId = world.worldId();
                    return true;
                }
                rowY += SIDEBAR_ROW_HEIGHT;
            }

            if (selectedActionRect().contains(mouseX, mouseY)) {
                WorldControlWireProtocol.WorldSummary selected = findActiveWorld(selectedWorldId);
                if (selected != null) {
                    if (isCurrentWorld(selected.worldId())) {
                        centerOnPlayer();
                    } else if (worlds.canTeleport() && !worlds.teleportPending()) {
                        closeAfterWorldTeleport = true;
                        worlds.teleport(selected.worldId());
                    }
                }
                return true;
            }
            if (manageWorldsRect().contains(mouseX, mouseY)) {
                if (client != null) client.setScreen(new WorldManagerScreen(this, worlds, transfers, maps));
                return true;
            }
            return true;
        }

        if (areaMode) {
            if (button == 0 && areaContinueRect().contains(mouseX, mouseY)) {
                continueAreaExport();
                return true;
            }
            if (button == 0 && areaCancelRect().contains(mouseX, mouseY)) {
                clearAreaSelection();
                return true;
            }
        }

        if (contextOpen) {
            if (button == 0 && contextTeleportRect().contains(mouseX, mouseY)) {
                if (!maps.teleportPending()) {
                    closeAfterMapTeleport = true;
                    maps.teleportCurrent(contextBlockX, contextBlockZ);
                    contextOpen = false;
                }
                return true;
            }
            if (button == 0 && contextExportRect().contains(mouseX, mouseY)) {
                if (maps.currentWorld() != null && worlds.canManage()) beginAreaSelection(contextBlockX, contextBlockZ);
                return true;
            }
            if (button == 0 && contextCopyRect().contains(mouseX, mouseY)) {
                if (client != null) client.keyboard.setClipboard(contextBlockX + ", " + contextBlockZ);
                contextOpen = false;
                return true;
            }
            if (!contextMenuRect().contains(mouseX, mouseY)) contextOpen = false;
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
        if (areaMode) {
            if (button == 1) return true;
            if (button == 0) {
                DragMode hit = hitSelection(mouseX, mouseY);
                int[] chunk = screenToChunk(mouseX, mouseY);
                if (chunk == null) return true;
                if (hit != DragMode.NONE) beginSelectionDrag(hit, chunk[0], chunk[1]);
                else draggingMap = true;
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
        if (!sidebarCollapsed && mouseX < sidebarWidth() && showAllWorlds && verticalAmount != 0) {
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
        if (keyCode == GLFW.GLFW_KEY_M || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeFromToggle();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            centerOnPlayer();
            return true;
        }
        if (areaMode && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            continueAreaExport();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C && hasShiftDown()) {
            copyReviewReference();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void beginAreaSelection(int blockX, int blockZ) {
        var current = maps.currentWorld();
        if (current == null || !worlds.canManage()) return;
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
        invalidateRasterViewport();
    }

    private void continueAreaExport() {
        if (!areaMode || client == null) return;
        var current = maps.currentWorld();
        if (current == null || selectionWorldId == null || !selectionWorldId.equals(current.worldId().value())) {
            clearAreaSelection();
            LazyBuilderClientNetworking.notifyPlayer(
                    "LazyBuilder: the selected area no longer belongs to the current world.");
            return;
        }
        client.setScreen(WorldTransferScreen.forArea(
                this, worlds, transfers, maps, current,
                minBlockX(), minBlockZ(), maxBlockX(), maxBlockZ()));
    }

    void finishAreaExport() {
        clearAreaSelection();
    }

    private void clearAreaSelection() {
        areaMode = false;
        selectionWorldId = null;
        selectionDrag = DragMode.NONE;
        draggingMap = false;
        contextOpen = false;
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
        int color = areaMode ? 0xBB8AA8FF : 0x667F8A98;
        context.fill(mouseX - 5, mouseY, mouseX + 6, mouseY + 1, color);
        context.fill(mouseX, mouseY - 5, mouseX + 1, mouseY + 6, color);
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
                && mouseY > rect.top + HANDLE_RADIUS && mouseY < rect.bottom - HANDLE_RADIUS) return DragMode.MOVE;
        return DragMode.NONE;
    }

    private Handle[] handles(SelectionRect rect) {
        int midX = rect.left + (rect.right - rect.left) / 2;
        int midY = rect.top + (rect.bottom - rect.top) / 2;
        return new Handle[]{
                new Handle(rect.left, rect.top, DragMode.NW), new Handle(midX, rect.top, DragMode.N),
                new Handle(rect.right, rect.top, DragMode.NE), new Handle(rect.right, midY, DragMode.E),
                new Handle(rect.right, rect.bottom, DragMode.SE), new Handle(midX, rect.bottom, DragMode.S),
                new Handle(rect.left, rect.bottom, DragMode.SW), new Handle(rect.left, midY, DragMode.W)
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
        int current = nearestZoomIndex(zoom);
        int next = Math.max(0, Math.min(ZOOM_STEPS.length - 1, current + direction));
        setZoom(ZOOM_STEPS[next], screenX, screenY);
    }

    private void preciseZoom(int direction, double screenX, double screenY) {
        double next = direction > 0 ? zoom * PRECISE_ZOOM_FACTOR : zoom / PRECISE_ZOOM_FACTOR;
        setZoom(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, next)), screenX, screenY);
    }

    private void setZoom(double next, double screenX, double screenY) {
        if (Math.abs(next - zoom) < 0.0001) return;
        double[] anchor = screenToWorldExact(screenX, screenY);
        zoom = next;
        if (anchor != null) {
            Bounds bounds = mapBounds();
            double bpp = blocksPerPixel();
            centerX = anchor[0] - (screenX - bounds.centerX()) * bpp;
            centerZ = anchor[1] - (screenY - bounds.centerY()) * bpp;
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
            centerX = client.player.getX();
            centerZ = client.player.getZ();
        }
    }

    private int mapPixelSize() {
        if (zoom <= 2.0) return 2;
        if (zoom <= 8.0) return 3;
        return 4;
    }

    private double blocksPerPixel() { return zoom / 2.0; }

    private double[] screenToWorldExact(double mouseX, double mouseY) {
        Bounds bounds = mapBounds();
        if (!bounds.contains(mouseX, mouseY)) return null;
        double bpp = blocksPerPixel();
        return new double[]{
                centerX + (mouseX - bounds.centerX()) * bpp,
                centerZ + (mouseY - bounds.centerY()) * bpp
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

    private String zoomLabel() {
        if (Math.abs(zoom - Math.rint(zoom)) < 0.01) {
            return "Zoom " + (int) Math.rint(zoom) + "×";
        }
        return String.format(Locale.ROOT, "Zoom %.1f×", zoom);
    }

    private boolean shouldAutoCollapseSidebar() {
        return width < 520 || height < 260;
    }

    private boolean mustCollapseSidebar() {
        return width < 400 || height < 220;
    }

    private int sidebarWidth() {
        if (sidebarCollapsed) return COLLAPSED_SIDEBAR;
        if (width < 620) return MIN_SIDEBAR;
        return Math.min(MAX_SIDEBAR, Math.max(MIN_SIDEBAR, width / 6));
    }

    private Bounds mapBounds() {
        return new Bounds(sidebarWidth() + SIDEBAR_GAP, 0, width,
                height - (areaMode ? SELECTION_BOTTOM_BAR : BOTTOM_BAR));
    }

    private Rect currentWorldRect() { return new Rect(8, 50, sidebarWidth() - 8, 82); }
    private Rect manageWorldsRect() { return new Rect(8, height - 34, sidebarWidth() - 8, height - 7); }
    private Rect selectedActionRect() { return new Rect(8, height - 67, sidebarWidth() - 8, height - 41); }
    private Rect areaCancelRect() { return new Rect(width - 166, height - 50, width - 94, height - 27); }
    private Rect areaContinueRect() { return new Rect(width - 88, height - 50, width - 10, height - 27); }
    private Rect zoomMinusRect() { return new Rect(width - 48, height - 22, width - 29, height - 3); }
    private Rect zoomPlusRect() { return new Rect(width - 26, height - 22, width - 7, height - 3); }

    private Rect contextMenuRect() {
        int menuWidth = 150;
        int menuHeight = 102;
        Bounds bounds = mapBounds();
        int x = Math.max(bounds.left + 4, Math.min(width - menuWidth - 5, contextScreenX));
        int y = Math.max(5, Math.min(bounds.bottom - menuHeight - 5, contextScreenY));
        return new Rect(x, y, x + menuWidth, y + menuHeight);
    }

    private Rect contextTeleportRect() { Rect m = contextMenuRect(); return new Rect(m.left + 5, m.top + 34, m.right - 5, m.top + 56); }
    private Rect contextExportRect() { Rect m = contextMenuRect(); return new Rect(m.left + 5, m.top + 57, m.right - 5, m.top + 79); }
    private Rect contextCopyRect() { Rect m = contextMenuRect(); return new Rect(m.left + 5, m.top + 80, m.right - 5, m.top + 101); }

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
        for (UUID id : NAVIGATION.pinned()) {
            WorldControlWireProtocol.WorldSummary world = find(active, id);
            if (world != null) result.add(world);
            if (result.size() >= MAX_FAVORITES) break;
        }
        return result;
    }

    private int visibleSidebarRows() {
        if (sidebarCollapsed) return 0;
        int available = height - 105 - 76;
        return Math.max(0, available / SIDEBAR_ROW_HEIGHT);
    }

    private WorldControlWireProtocol.WorldSummary findActiveWorld(UUID id) {
        if (id == null) return null;
        return find(activeWorlds(), id);
    }

    private static WorldControlWireProtocol.WorldSummary find(
            List<WorldControlWireProtocol.WorldSummary> source, UUID id) {
        return source.stream().filter(world -> world.worldId().equals(id)).findFirst().orElse(null);
    }

    private UUID currentWorldId() {
        return maps.currentWorld() == null ? null : maps.currentWorld().worldId().value();
    }

    private boolean isCurrentWorld(UUID id) {
        UUID current = currentWorldId();
        return current != null && current.equals(id);
    }

    private String trim(String value, int maxWidth) {
        if (value == null || maxWidth <= 0) return "";
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String base = value;
        while (base.length() > 1 && textRenderer.getWidth(base + "…") > maxWidth) {
            base = base.substring(0, base.length() - 1);
        }
        return textRenderer.getWidth(base + "…") <= maxWidth ? base + "…" : "";
    }

    private static String friendlyDimension(String raw) {
        return switch (raw) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "The End";
            default -> raw.replace('_', ' ');
        };
    }

    private void invalidateRasterViewport() {
        rasterLeft = Integer.MIN_VALUE;
        rasterRight = Integer.MIN_VALUE;
        rasterTop = Integer.MIN_VALUE;
        rasterBottom = Integer.MIN_VALUE;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        closeFromToggle();
    }

    void closeFromToggle() {
        clearAreaSelection();
        SURFACE.flushAsync();
        if (client != null) client.setScreen(null);
    }

    private enum DragMode { NONE, MOVE, N, NE, E, SE, S, SW, W, NW }
    private record Handle(int x, int y, DragMode mode) {}
    private record SelectionRect(int left, int top, int right, int bottom) {}
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

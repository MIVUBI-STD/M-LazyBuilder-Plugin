package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/** Daily-use managed-world workspace reached from the fullscreen map. */
public final class WorldManagerScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController controller;
    private final ClientTransferController transfers;
    private final ClientMapController maps;
    private UUID selectedWorld;
    private int page;
    private long observedRevision;
    private boolean requestedInitialRefresh;
    private boolean compactDetail;

    public WorldManagerScreen(
            Screen parent,
            ClientWorldController controller,
            ClientTransferController transfers,
            ClientMapController maps
    ) {
        super(Text.literal("Worlds"));
        this.parent = parent;
        this.controller = controller;
        this.transfers = transfers;
        this.maps = maps;
        this.observedRevision = controller.revision();
    }

    public WorldManagerScreen(ClientWorldController controller, ClientTransferController transfers, ClientMapController maps) {
        this(null, controller, transfers, maps);
    }

    @Override
    protected void init() {
        if (!requestedInitialRefresh) {
            requestedInitialRefresh = true;
            controller.refresh();
        }
        observedRevision = controller.revision();

        List<WorldControlWireProtocol.WorldSummary> worlds = controller.worlds();
        Layout l = layout();
        int pageSize = pageSize(l);
        int maxPage = worlds.isEmpty() ? 0 : (worlds.size() - 1) / pageSize;
        page = Math.max(0, Math.min(page, maxPage));

        if (selectedWorld != null && worlds.stream().noneMatch(world -> world.worldId().equals(selectedWorld))) {
            selectedWorld = null;
            compactDetail = false;
        }
        if (selectedWorld == null && !worlds.isEmpty()) selectedWorld = worlds.get(0).worldId();

        boolean busy = operationBusy();
        if (!l.compact || !compactDetail) addWorldListControls(l, worlds, pageSize, maxPage, busy);

        WorldControlWireProtocol.WorldSummary selected = selected();
        if (selected != null && (!l.compact || compactDetail)) addWorldActions(l, selected, busy);

        if (l.compact && compactDetail) {
            addDrawableChild(LbUi.button(l.left + 12, l.bottom + 8, 104, 20, "Back to Worlds",
                    LbButtonWidget.Style.GHOST, () -> {
                        compactDetail = false;
                        clearAndInit();
                    }));
        }

        addDrawableChild(LbUi.button(l.right - 104, l.bottom + 8, 94, 20, "Back to Map",
                LbButtonWidget.Style.GHOST, this::returnToMap));
    }

    private void addWorldListControls(
            Layout l,
            List<WorldControlWireProtocol.WorldSummary> worlds,
            int pageSize,
            int maxPage,
            boolean busy
    ) {
        int paneLeft = l.listLeft();
        int paneWidth = l.listPaneWidth();

        LbButtonWidget add = LbUi.button(paneLeft + 14, 58, 112, 22, "+ Add World",
                LbButtonWidget.Style.PRIMARY,
                () -> { if (client != null) client.setScreen(new AddWorldScreen(this, controller, transfers)); });
        add.active = !busy;
        addDrawableChild(add);

        LbButtonWidget refresh = LbUi.button(paneLeft + 132, 58, Math.max(64, Math.min(76, paneWidth - 146)), 22,
                "Refresh", LbButtonWidget.Style.GHOST, controller::refresh);
        refresh.active = !busy;
        addDrawableChild(refresh);

        int start = page * pageSize;
        int end = Math.min(start + pageSize, worlds.size());
        int y = 96;
        for (int i = start; i < end; i++) {
            WorldControlWireProtocol.WorldSummary world = worlds.get(i);
            boolean selected = world.worldId().equals(selectedWorld);
            String state = "LOADED".equals(world.runtimeState()) ? "   • Loaded" : "";
            LbButtonWidget row = LbUi.button(
                    paneLeft + 14, y, paneWidth - 28, 26,
                    world.displayName() + state,
                    selected ? LbButtonWidget.Style.PRIMARY : LbButtonWidget.Style.SECONDARY,
                    () -> {
                        selectedWorld = world.worldId();
                        if (l.compact) compactDetail = true;
                        clearAndInit();
                    });
            addDrawableChild(row);
            y += 32;
        }

        if (maxPage > 0) {
            addDrawableChild(LbUi.button(paneLeft + 14, l.bottom - 30, 34, 20, "‹",
                    LbButtonWidget.Style.GHOST,
                    () -> { page = Math.max(0, page - 1); clearAndInit(); }));
            addDrawableChild(LbUi.button(paneLeft + 54, l.bottom - 30, 34, 20, "›",
                    LbButtonWidget.Style.GHOST,
                    () -> { page = Math.min(maxPage, page + 1); clearAndInit(); }));
        }
    }

    private void addWorldActions(Layout l, WorldControlWireProtocol.WorldSummary world, boolean busy) {
        int x = l.detailPaneLeft() + 22;
        int contentWidth = Math.max(1, l.detailPaneWidth() - 44);
        boolean narrow = contentWidth < 270;
        int half = narrow ? contentWidth : (contentWidth - 10) / 2;
        int y = 126;

        if ("ACTIVE".equals(world.lifecycle())) {
            LbButtonWidget teleport = LbUi.button(x, y, half, 26, "Teleport",
                    LbButtonWidget.Style.PRIMARY, () -> controller.teleport(world.worldId()));
            teleport.active = !busy;
            addDrawableChild(teleport);

            String loadLabel = "LOADED".equals(world.runtimeState()) ? "Unload" : "Load";
            int loadX = narrow ? x : x + half + 10;
            int loadY = narrow ? y + 34 : y;
            LbButtonWidget load = LbUi.button(loadX, loadY, half, 26, loadLabel,
                    LbButtonWidget.Style.SECONDARY,
                    () -> {
                        if ("LOADED".equals(world.runtimeState())) controller.unload(world.worldId());
                        else controller.load(world.worldId());
                    });
            load.active = !busy;
            addDrawableChild(load);

            y += narrow ? 72 : 38;
            LbButtonWidget export = LbUi.button(x, y, contentWidth, 26, "Export World",
                    LbButtonWidget.Style.SECONDARY,
                    () -> { if (client != null) client.setScreen(new ExportWorldScreen(this, controller, world)); });
            export.active = !busy;
            addDrawableChild(export);

            y += 46;
            addManagementActions(l, world, busy, x, y, contentWidth, half, narrow);
        } else if ("ARCHIVED".equals(world.lifecycle())) {
            LbButtonWidget restore = LbUi.button(x, y, contentWidth, 26, "Restore World",
                    LbButtonWidget.Style.PRIMARY, () -> controller.restore(world.worldId()));
            restore.active = !busy;
            addDrawableChild(restore);

            y += 40;
            LbButtonWidget delete = LbUi.button(x, y, contentWidth, 24, "Delete Permanently",
                    LbButtonWidget.Style.DANGER,
                    () -> { if (client != null) client.setScreen(new DeleteWorldScreen(this, controller, world)); });
            delete.active = !busy;
            addDrawableChild(delete);
        }
    }

    private void addManagementActions(
            Layout l,
            WorldControlWireProtocol.WorldSummary world,
            boolean busy,
            int x,
            int y,
            int contentWidth,
            int half,
            boolean narrow
    ) {
        LbButtonWidget settings = LbUi.button(x, y, half, 24, "Settings",
                LbButtonWidget.Style.GHOST,
                () -> {
                    controller.requestSettings(world.worldId());
                    if (client != null) client.setScreen(new WorldSettingsScreen(this, controller, world));
                });
        settings.active = !busy;
        addDrawableChild(settings);

        int cloneX = narrow ? x : x + half + 10;
        int cloneY = narrow ? y + 32 : y;
        LbButtonWidget clone = LbUi.button(cloneX, cloneY, half, 24, "Clone",
                LbButtonWidget.Style.GHOST,
                () -> { if (client != null) client.setScreen(new CloneWorldScreen(this, controller, world)); });
        clone.active = !busy;
        addDrawableChild(clone);

        int nextY = y + (narrow ? 64 : 34);
        LbButtonWidget archive = LbUi.button(x, nextY, half, 24, "Archive",
                LbButtonWidget.Style.GHOST, () -> confirmArchive(world));
        archive.active = !busy;
        addDrawableChild(archive);

        int deleteX = narrow ? x : x + half + 10;
        int deleteY = narrow ? nextY + 32 : nextY;
        LbButtonWidget delete = LbUi.button(deleteX, deleteY, half, 24, "Delete",
                LbButtonWidget.Style.DANGER,
                () -> { if (client != null) client.setScreen(new DeleteWorldScreen(this, controller, world)); });
        delete.active = !busy;
        addDrawableChild(delete);
    }

    private void confirmArchive(WorldControlWireProtocol.WorldSummary world) {
        if (client == null) return;
        client.setScreen(new ConfirmWorldActionScreen(
                this,
                Text.literal("Archive World"),
                Text.literal("Archive " + world.displayName() + "? The world will be unloaded."),
                "Archive",
                () -> controller.archive(world.worldId())
        ));
    }

    @Override
    public void tick() {
        if (observedRevision != controller.revision()) {
            observedRevision = controller.revision();
            clearAndInit();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LbUi.background(context, width, height);
        Layout l = layout();
        context.drawTextWithShadow(textRenderer, Text.literal("LAZYBUILDER"), l.left, 14, LbUi.TEXT_MUTED);

        if (!l.compact || !compactDetail) renderWorldListPane(context, l);
        if (!l.compact || compactDetail) renderDetailPane(context, l);

        renderOperationStatus(context, l);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderWorldListPane(DrawContext context, Layout l) {
        int paneLeft = l.listLeft();
        int paneWidth = l.listPaneWidth();
        LbUi.panel(context, paneLeft, 34, paneWidth, l.bottom - 34);
        LbUi.divider(context, paneLeft + 14, 88, paneLeft + paneWidth - 14);
        context.drawTextWithShadow(textRenderer, Text.literal("Managed Worlds"), paneLeft + 14, 42, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Choose a world to work on"), paneLeft + 14, 72, LbUi.TEXT_MUTED);

        if (controller.worlds().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("No worlds yet"),
                    paneLeft + paneWidth / 2, 132, LbUi.TEXT_SECONDARY);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Use + Add World to begin"),
                    paneLeft + paneWidth / 2, 150, LbUi.TEXT_MUTED);
        }
    }

    private void renderDetailPane(DrawContext context, Layout l) {
        int detailLeft = l.detailPaneLeft();
        int detailWidth = l.detailPaneWidth();
        LbUi.elevatedPanel(context, detailLeft, 34, detailWidth, l.bottom - 34);

        WorldControlWireProtocol.WorldSummary selected = selected();
        if (selected == null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Choose a world"),
                    detailLeft + detailWidth / 2, 120, LbUi.TEXT_MUTED);
            return;
        }

        int tx = detailLeft + 22;
        context.drawTextWithShadow(textRenderer, Text.literal(selected.displayName()), tx, 52, LbUi.TEXT_PRIMARY);
        String status = titleCase(selected.lifecycle()) + "  •  " + titleCase(selected.runtimeState());
        context.drawTextWithShadow(textRenderer, Text.literal(status), tx, 72,
                "LOADED".equals(selected.runtimeState()) ? LbUi.SUCCESS : LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Type  " + titleCase(selected.kind())), tx, 92, LbUi.TEXT_MUTED);
        if (detailWidth >= 260) {
            context.drawTextWithShadow(textRenderer, Text.literal("Folder  " + selected.folderName()), tx, 106, LbUi.TEXT_MUTED);
        }
        LbUi.divider(context, tx, 114, detailLeft + detailWidth - 22);
        context.drawTextWithShadow(textRenderer, Text.literal("Actions"), tx, 120, LbUi.TEXT_SECONDARY);

        if (controller.lastError() != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.lastError()),
                    detailLeft + detailWidth / 2, l.bottom - 18, LbUi.DANGER_BRIGHT);
        }
    }

    private void renderOperationStatus(DrawContext context, Layout l) {
        String worldActivity = controller.activityMessage();
        ClientTransferController.TransferStatus transfer = transfers.status();
        String label = null;
        int percent = -1;

        if (transfer.active()) {
            label = transfer.message();
            percent = transfer.percent();
        } else if (worldActivity != null) {
            label = worldActivity;
        }
        if (label == null) return;

        int paneLeft = (!l.compact || compactDetail) ? l.detailPaneLeft() : l.listLeft();
        int paneWidth = (!l.compact || compactDetail) ? l.detailPaneWidth() : l.listPaneWidth();
        int y = l.bottom - 42;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), paneLeft + paneWidth / 2, y, LbUi.TEXT_SECONDARY);
        if (percent >= 0) {
            int barWidth = Math.max(80, Math.min(280, paneWidth - 44));
            int x = paneLeft + (paneWidth - barWidth) / 2;
            LbUi.progress(context, x, y + 14, barWidth, percent);
        }
    }

    private boolean operationBusy() {
        return controller.activityMessage() != null || transfers.status().active();
    }

    private int pageSize(Layout l) {
        return Math.max(2, Math.min(7, (l.bottom - 126) / 32));
    }

    private Layout layout() {
        boolean compact = width < 560;
        int totalWidth = Math.max(280, Math.min(820, width - 24));
        int left = (width - totalWidth) / 2;
        int right = left + totalWidth;
        int bottom = Math.max(170, height - 42);

        if (compact) {
            return new Layout(left, right, totalWidth, left, totalWidth, bottom, true);
        }

        int listWidth = Math.min(280, Math.max(210, totalWidth / 3));
        int detailLeft = left + listWidth + 12;
        int detailWidth = right - detailLeft;
        return new Layout(left, right, listWidth, detailLeft, detailWidth, bottom, false);
    }

    private void returnToMap() {
        if (client == null) return;
        if (parent != null) client.setScreen(parent);
        else client.setScreen(new WorldMapScreen(controller, transfers, maps));
    }

    @Override
    public void close() {
        if (layout().compact && compactDetail) {
            compactDetail = false;
            clearAndInit();
            return;
        }
        returnToMap();
    }

    private WorldControlWireProtocol.WorldSummary selected() {
        if (selectedWorld == null) return null;
        return controller.worlds().stream()
                .filter(world -> world.worldId().equals(selectedWorld))
                .findFirst()
                .orElse(null);
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) return "";
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private record Layout(
            int left,
            int right,
            int listWidth,
            int detailLeft,
            int detailWidth,
            int bottom,
            boolean compact
    ) {
        int listLeft() { return left; }
        int listPaneWidth() { return compact ? right - left : listWidth; }
        int detailPaneLeft() { return compact ? left : detailLeft; }
        int detailPaneWidth() { return compact ? right - left : detailWidth; }
    }
}

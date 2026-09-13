package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/** Daily-use managed-world workspace reached from the fullscreen map. */
public final class WorldManagerScreen extends Screen {
    private static final int PAGE_SIZE = 7;

    private final Screen parent;
    private final ClientWorldController controller;
    private final ClientTransferController transfers;
    private final ClientMapController maps;
    private UUID selectedWorld;
    private int page;
    private long observedRevision;
    private boolean requestedInitialRefresh;

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
        int maxPage = worlds.isEmpty() ? 0 : (worlds.size() - 1) / PAGE_SIZE;
        page = Math.max(0, Math.min(page, maxPage));

        if (selectedWorld != null && worlds.stream().noneMatch(world -> world.worldId().equals(selectedWorld))) {
            selectedWorld = null;
        }
        if (selectedWorld == null && !worlds.isEmpty()) selectedWorld = worlds.get(0).worldId();

        Layout l = layout();
        boolean busy = operationBusy();

        LbButtonWidget add = LbUi.button(l.left + 14, 58, 112, 22, "+ Add World",
                LbButtonWidget.Style.PRIMARY,
                () -> { if (client != null) client.setScreen(new AddWorldScreen(this, controller, transfers)); });
        add.active = !busy;
        addDrawableChild(add);

        LbButtonWidget refresh = LbUi.button(l.left + 132, 58, 76, 22, "Refresh",
                LbButtonWidget.Style.GHOST, controller::refresh);
        refresh.active = !busy;
        addDrawableChild(refresh);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, worlds.size());
        int y = 96;
        for (int i = start; i < end; i++) {
            WorldControlWireProtocol.WorldSummary world = worlds.get(i);
            boolean selected = world.worldId().equals(selectedWorld);
            String state = "LOADED".equals(world.runtimeState()) ? "   • Loaded" : "";
            LbButtonWidget row = LbUi.button(
                    l.left + 14, y, l.listWidth - 28, 26,
                    world.displayName() + state,
                    selected ? LbButtonWidget.Style.PRIMARY : LbButtonWidget.Style.SECONDARY,
                    () -> { selectedWorld = world.worldId(); clearAndInit(); });
            addDrawableChild(row);
            y += 32;
        }

        if (maxPage > 0) {
            addDrawableChild(LbUi.button(l.left + 14, l.bottom - 30, 34, 20, "‹",
                    LbButtonWidget.Style.GHOST,
                    () -> { page = Math.max(0, page - 1); clearAndInit(); }));
            addDrawableChild(LbUi.button(l.left + 54, l.bottom - 30, 34, 20, "›",
                    LbButtonWidget.Style.GHOST,
                    () -> { page = Math.min(maxPage, page + 1); clearAndInit(); }));
        }

        WorldControlWireProtocol.WorldSummary selected = selected();
        if (selected != null) addWorldActions(l, selected, busy);

        addDrawableChild(LbUi.button(l.right - 104, l.bottom + 10, 94, 20, "Back to Map",
                LbButtonWidget.Style.GHOST, this::returnToMap));
    }

    private void addWorldActions(Layout l, WorldControlWireProtocol.WorldSummary world, boolean busy) {
        int x = l.detailLeft + 22;
        int contentWidth = Math.max(210, l.detailWidth - 44);
        int half = (contentWidth - 10) / 2;
        int y = 126;

        if ("ACTIVE".equals(world.lifecycle())) {
            LbButtonWidget teleport = LbUi.button(x, y, half, 26, "Teleport",
                    LbButtonWidget.Style.PRIMARY, () -> controller.teleport(world.worldId()));
            teleport.active = !busy;
            addDrawableChild(teleport);

            String loadLabel = "LOADED".equals(world.runtimeState()) ? "Unload" : "Load";
            LbButtonWidget load = LbUi.button(x + half + 10, y, half, 26, loadLabel,
                    LbButtonWidget.Style.SECONDARY,
                    () -> {
                        if ("LOADED".equals(world.runtimeState())) controller.unload(world.worldId());
                        else controller.load(world.worldId());
                    });
            load.active = !busy;
            addDrawableChild(load);

            y += 38;
            LbButtonWidget export = LbUi.button(x, y, contentWidth, 26, "Export World",
                    LbButtonWidget.Style.SECONDARY,
                    () -> { if (client != null) client.setScreen(new ExportWorldScreen(this, controller, world)); });
            export.active = !busy;
            addDrawableChild(export);

            y += 46;
            LbButtonWidget settings = LbUi.button(x, y, half, 24, "Settings",
                    LbButtonWidget.Style.GHOST,
                    () -> {
                        controller.requestSettings(world.worldId());
                        if (client != null) client.setScreen(new WorldSettingsScreen(this, controller, world));
                    });
            settings.active = !busy;
            addDrawableChild(settings);

            LbButtonWidget clone = LbUi.button(x + half + 10, y, half, 24, "Clone",
                    LbButtonWidget.Style.GHOST,
                    () -> { if (client != null) client.setScreen(new CloneWorldScreen(this, controller, world)); });
            clone.active = !busy;
            addDrawableChild(clone);

            y += 34;
            LbButtonWidget archive = LbUi.button(x, y, half, 24, "Archive",
                    LbButtonWidget.Style.GHOST, () -> confirmArchive(world));
            archive.active = !busy;
            addDrawableChild(archive);

            LbButtonWidget delete = LbUi.button(x + half + 10, y, half, 24, "Delete",
                    LbButtonWidget.Style.DANGER,
                    () -> { if (client != null) client.setScreen(new DeleteWorldScreen(this, controller, world)); });
            delete.active = !busy;
            addDrawableChild(delete);
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
        LbUi.panel(context, l.left, 34, l.listWidth, l.bottom - 34);
        LbUi.elevatedPanel(context, l.detailLeft, 34, l.detailWidth, l.bottom - 34);
        LbUi.divider(context, l.left + 14, 88, l.left + l.listWidth - 14);

        context.drawTextWithShadow(textRenderer, Text.literal("LAZYBUILDER"), l.left, 14, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal("Managed Worlds"), l.left + 14, 42, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Daily workspace"), l.left + 14, 72, LbUi.TEXT_MUTED);

        WorldControlWireProtocol.WorldSummary selected = selected();
        if (selected != null) {
            int tx = l.detailLeft + 22;
            context.drawTextWithShadow(textRenderer, Text.literal(selected.displayName()), tx, 52, LbUi.TEXT_PRIMARY);
            String status = titleCase(selected.lifecycle()) + "  •  " + titleCase(selected.runtimeState());
            context.drawTextWithShadow(textRenderer, Text.literal(status), tx, 72,
                    "LOADED".equals(selected.runtimeState()) ? LbUi.SUCCESS : LbUi.TEXT_SECONDARY);
            context.drawTextWithShadow(textRenderer, Text.literal("Type  " + titleCase(selected.kind())), tx, 92, LbUi.TEXT_MUTED);
            context.drawTextWithShadow(textRenderer, Text.literal("Folder  " + selected.folderName()), tx, 106, LbUi.TEXT_MUTED);
            LbUi.divider(context, tx, 114, l.right - 22);
            context.drawTextWithShadow(textRenderer, Text.literal("Primary Actions"), tx, 120, LbUi.TEXT_SECONDARY);
            context.drawTextWithShadow(textRenderer, Text.literal("Management"), tx, 220, LbUi.TEXT_MUTED);
        } else if (controller.worlds().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("No managed worlds yet"),
                    l.left + l.listWidth / 2, 132, LbUi.TEXT_SECONDARY);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Add a world to begin building"),
                    l.left + l.listWidth / 2, 150, LbUi.TEXT_MUTED);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Choose or create a world from the left"),
                    l.detailLeft + l.detailWidth / 2, 120, LbUi.TEXT_MUTED);
        }

        renderOperationStatus(context, l);
        if (controller.lastError() != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.lastError()),
                    l.detailLeft + l.detailWidth / 2, l.bottom - 18, LbUi.DANGER_BRIGHT);
        }

        super.render(context, mouseX, mouseY, delta);
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

        int y = l.bottom - 42;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label),
                l.detailLeft + l.detailWidth / 2, y, LbUi.TEXT_SECONDARY);
        if (percent >= 0) {
            int barWidth = Math.min(280, l.detailWidth - 44);
            int x = l.detailLeft + (l.detailWidth - barWidth) / 2;
            LbUi.progress(context, x, y + 14, barWidth, percent);
        }
    }

    private boolean operationBusy() {
        return controller.activityMessage() != null || transfers.status().active();
    }

    private Layout layout() {
        int totalWidth = Math.min(820, Math.max(560, width - 54));
        int left = (width - totalWidth) / 2;
        int right = left + totalWidth;
        int listWidth = Math.min(280, Math.max(220, totalWidth / 3));
        int detailLeft = left + listWidth + 12;
        int detailWidth = right - detailLeft;
        int bottom = height - 42;
        return new Layout(left, right, listWidth, detailLeft, detailWidth, bottom);
    }

    private void returnToMap() {
        if (client == null) return;
        if (parent != null) client.setScreen(parent);
        else client.setScreen(new WorldMapScreen(controller, transfers, maps));
    }

    @Override
    public void close() {
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

    private record Layout(int left, int right, int listWidth, int detailLeft, int detailWidth, int bottom) {}
}

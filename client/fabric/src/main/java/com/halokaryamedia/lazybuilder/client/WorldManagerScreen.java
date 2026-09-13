package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/** Secondary world-management surface reached from the map. */
public final class WorldManagerScreen extends Screen {
    private static final int PAGE_SIZE = 6;

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

        Layout layout = layout();
        boolean busy = operationBusy();

        ButtonWidget add = ButtonWidget.builder(Text.literal("+ Add World"), button -> {
            if (client != null) client.setScreen(new AddWorldScreen(this, controller, transfers));
        }).dimensions(layout.left + 12, 52, 110, 20).build();
        add.active = !busy;
        addDrawableChild(add);

        ButtonWidget refresh = ButtonWidget.builder(Text.literal("Refresh"), button -> controller.refresh())
                .dimensions(layout.left + 128, 52, 72, 20).build();
        refresh.active = !busy;
        addDrawableChild(refresh);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, worlds.size());
        int y = 84;
        for (int i = start; i < end; i++) {
            WorldControlWireProtocol.WorldSummary world = worlds.get(i);
            String marker = world.worldId().equals(selectedWorld) ? "● " : "  ";
            String state = "LOADED".equals(world.runtimeState()) ? "  •  Loaded" : "";
            String label = marker + world.displayName() + state;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                selectedWorld = world.worldId();
                clearAndInit();
            }).dimensions(layout.left + 12, y, layout.listWidth - 24, 24).build());
            y += 29;
        }

        if (maxPage > 0) {
            addDrawableChild(ButtonWidget.builder(Text.literal("‹"), button -> {
                page = Math.max(0, page - 1);
                clearAndInit();
            }).dimensions(layout.left + 12, layout.bottom - 34, 30, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("›"), button -> {
                page = Math.min(maxPage, page + 1);
                clearAndInit();
            }).dimensions(layout.left + 48, layout.bottom - 34, 30, 20).build());
        }

        WorldControlWireProtocol.WorldSummary selected = selected();
        if (selected != null) addWorldActions(layout, selected, busy);

        addDrawableChild(ButtonWidget.builder(Text.literal("Back to Map"), button -> returnToMap())
                .dimensions(layout.right - 106, layout.bottom + 8, 96, 18).build());
    }

    private void addWorldActions(Layout layout, WorldControlWireProtocol.WorldSummary world, boolean busy) {
        int x = layout.detailLeft + 20;
        int contentWidth = Math.max(190, layout.detailWidth - 40);
        int half = (contentWidth - 8) / 2;
        int y = 118;

        if ("ACTIVE".equals(world.lifecycle())) {
            ButtonWidget teleport = ButtonWidget.builder(Text.literal("Teleport"), button -> controller.teleport(world.worldId()))
                    .dimensions(x, y, half, 24).build();
            teleport.active = !busy;
            addDrawableChild(teleport);

            String loadLabel = "LOADED".equals(world.runtimeState()) ? "Unload" : "Load";
            ButtonWidget load = ButtonWidget.builder(Text.literal(loadLabel), button -> {
                if ("LOADED".equals(world.runtimeState())) controller.unload(world.worldId());
                else controller.load(world.worldId());
            }).dimensions(x + half + 8, y, half, 24).build();
            load.active = !busy;
            addDrawableChild(load);

            y += 34;
            ButtonWidget export = ButtonWidget.builder(Text.literal("Export World"), button -> {
                if (client != null) client.setScreen(new ExportWorldScreen(this, controller, world));
            }).dimensions(x, y, contentWidth, 24).build();
            export.active = !busy;
            addDrawableChild(export);

            y += 38;
            ButtonWidget settings = ButtonWidget.builder(Text.literal("Settings"), button -> {
                controller.requestSettings(world.worldId());
                if (client != null) client.setScreen(new WorldSettingsScreen(this, controller, world));
            }).dimensions(x, y, half, 22).build();
            settings.active = !busy;
            addDrawableChild(settings);

            ButtonWidget clone = ButtonWidget.builder(Text.literal("Clone"), button -> {
                if (client != null) client.setScreen(new CloneWorldScreen(this, controller, world));
            }).dimensions(x + half + 8, y, half, 22).build();
            clone.active = !busy;
            addDrawableChild(clone);

            y += 32;
            ButtonWidget archive = ButtonWidget.builder(Text.literal("Archive"), button -> confirmArchive(world))
                    .dimensions(x, y, half, 22).build();
            archive.active = !busy;
            addDrawableChild(archive);

            ButtonWidget delete = ButtonWidget.builder(Text.literal("Delete"), button -> {
                if (client != null) client.setScreen(new DeleteWorldScreen(this, controller, world));
            }).dimensions(x + half + 8, y, half, 22).build();
            delete.active = !busy;
            addDrawableChild(delete);
        } else if ("ARCHIVED".equals(world.lifecycle())) {
            ButtonWidget restore = ButtonWidget.builder(Text.literal("Restore World"), button -> controller.restore(world.worldId()))
                    .dimensions(x, y, contentWidth, 24).build();
            restore.active = !busy;
            addDrawableChild(restore);

            y += 34;
            ButtonWidget delete = ButtonWidget.builder(Text.literal("Delete Permanently"), button -> {
                if (client != null) client.setScreen(new DeleteWorldScreen(this, controller, world));
            }).dimensions(x, y, contentWidth, 22).build();
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
        renderBackground(context, mouseX, mouseY, delta);
        Layout layout = layout();

        context.fill(layout.left, 24, layout.right, layout.bottom, 0xC5101318);
        context.fill(layout.left + 1, 25, layout.detailLeft - 8, layout.bottom - 1, 0xD414181E);
        context.fill(layout.detailLeft, 25, layout.right - 1, layout.bottom - 1, 0xE0181D24);
        context.fill(layout.left, 24, layout.right, 25, 0xFF343B45);

        super.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(textRenderer, title, layout.left + 12, 10, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("Managed Worlds"), layout.left + 12, 34, 0xD8DEE9);

        if (controller.worlds().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("No managed worlds yet"), layout.left + layout.listWidth / 2, 104, 0xC5CCD5);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Create or import a world to begin"),
                    layout.left + layout.listWidth / 2, 120, 0x8993A0);
        }

        WorldControlWireProtocol.WorldSummary selected = selected();
        if (selected != null) {
            int tx = layout.detailLeft + 20;
            context.drawTextWithShadow(textRenderer, Text.literal("World Details"), tx, 40, 0x9DA7B4);
            context.drawTextWithShadow(textRenderer, Text.literal(selected.displayName()), tx, 58, 0xFFFFFF);

            int statusColor = "LOADED".equals(selected.runtimeState()) ? 0xFFA8E6A3 : 0xFFC1C8D0;
            String status = titleCase(selected.lifecycle()) + "  •  " + titleCase(selected.runtimeState());
            context.drawTextWithShadow(textRenderer, Text.literal(status), tx, 76, statusColor);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Type: " + titleCase(selected.kind())), tx, 92, 0x8993A0);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Folder: " + selected.folderName()), tx, 104, 0x737D8A);
        } else if (!controller.worlds().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Select a world"), layout.detailLeft + layout.detailWidth / 2, 88, 0xAEB7C4);
        }

        renderOperationStatus(context, layout);

        if (controller.lastError() != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(controller.lastError()), layout.detailLeft + layout.detailWidth / 2,
                    layout.bottom - 50, 0xFFFF7777);
        }
    }

    private void renderOperationStatus(DrawContext context, Layout layout) {
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
        if (percent >= 0) label += "  " + percent + "%";
        int y = layout.bottom - 30;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label),
                layout.detailLeft + layout.detailWidth / 2, y, 0xD8DEE9);
        if (percent >= 0) {
            int barWidth = Math.min(280, layout.detailWidth - 40);
            int left = layout.detailLeft + (layout.detailWidth - barWidth) / 2;
            int filled = (int) Math.round(barWidth * (percent / 100.0));
            context.fill(left, y + 13, left + barWidth, y + 18, 0xFF303740);
            context.fill(left, y + 13, left + filled, y + 18, 0xFFD8DEE9);
        }
    }

    private boolean operationBusy() {
        return transfers.status().active() || controller.activityMessage() != null;
    }

    private Layout layout() {
        int totalWidth = Math.min(760, Math.max(520, width - 64));
        int left = (width - totalWidth) / 2;
        int right = left + totalWidth;
        int listWidth = Math.min(270, Math.max(205, totalWidth / 3));
        int detailLeft = left + listWidth + 12;
        int detailWidth = right - detailLeft;
        int bottom = height - 38;
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
        if (value == null || value.isBlank()) return "Unknown";
        String normalized = value.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private record Layout(int left, int right, int listWidth, int detailLeft, int detailWidth, int bottom) {}
}

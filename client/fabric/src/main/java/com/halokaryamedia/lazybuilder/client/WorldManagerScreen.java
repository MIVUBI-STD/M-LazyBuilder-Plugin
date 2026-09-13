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

    public WorldManagerScreen(Screen parent, ClientWorldController controller, ClientTransferController transfers, ClientMapController maps) {
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

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Add World"), button -> {
            if (client != null) client.setScreen(new AddWorldScreen(this, controller, transfers));
        }).dimensions(layout.left, 34, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> controller.refresh())
                .dimensions(layout.left + 116, 34, 72, 20).build());

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, worlds.size());
        int y = 66;
        for (int i = start; i < end; i++) {
            WorldControlWireProtocol.WorldSummary world = worlds.get(i);
            String marker = world.worldId().equals(selectedWorld) ? "● " : "  ";
            String label = marker + world.displayName();
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                selectedWorld = world.worldId();
                clearAndInit();
            }).dimensions(layout.left, y, layout.listWidth, 24).build());
            y += 28;
        }

        if (maxPage > 0) {
            addDrawableChild(ButtonWidget.builder(Text.literal("‹"), button -> {
                page = Math.max(0, page - 1);
                clearAndInit();
            }).dimensions(layout.left, layout.bottom - 54, 30, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("›"), button -> {
                page = Math.min(maxPage, page + 1);
                clearAndInit();
            }).dimensions(layout.left + 36, layout.bottom - 54, 30, 20).build());
        }

        WorldControlWireProtocol.WorldSummary selected = selected();
        if (selected != null) addWorldActions(layout, selected);

        addDrawableChild(ButtonWidget.builder(Text.literal("Back to Map"), button -> returnToMap())
                .dimensions(layout.right - 104, height - 26, 94, 18).build());
    }

    private void addWorldActions(Layout layout, WorldControlWireProtocol.WorldSummary world) {
        int x = layout.detailLeft + 18;
        int contentWidth = Math.max(180, layout.detailWidth - 36);
        int half = (contentWidth - 6) / 2;
        int y = 112;

        if ("ACTIVE".equals(world.lifecycle())) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Teleport"), button -> controller.teleport(world.worldId()))
                    .dimensions(x, y, half, 22).build());

            String loadLabel = "LOADED".equals(world.runtimeState()) ? "Unload" : "Load";
            addDrawableChild(ButtonWidget.builder(Text.literal(loadLabel), button -> {
                if ("LOADED".equals(world.runtimeState())) controller.unload(world.worldId());
                else controller.load(world.worldId());
            }).dimensions(x + half + 6, y, half, 22).build());

            y += 30;
            addDrawableChild(ButtonWidget.builder(Text.literal("Export World"), button -> {
                if (client != null) client.setScreen(new ExportWorldScreen(this, controller, world));
            }).dimensions(x, y, contentWidth, 22).build());

            y += 36;
            addDrawableChild(ButtonWidget.builder(Text.literal("Settings"), button -> {
                controller.requestSettings(world.worldId());
                if (client != null) client.setScreen(new WorldSettingsScreen(this, controller, world));
            }).dimensions(x, y, half, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Clone"), button -> {
                if (client != null) client.setScreen(new CloneWorldScreen(this, controller, world));
            }).dimensions(x + half + 6, y, half, 20).build());

            y += 28;
            addDrawableChild(ButtonWidget.builder(Text.literal("Archive"), button -> confirmArchive(world))
                    .dimensions(x, y, half, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), button -> {
                if (client != null) client.setScreen(new DeleteWorldScreen(this, controller, world));
            }).dimensions(x + half + 6, y, half, 20).build());
        } else if ("ARCHIVED".equals(world.lifecycle())) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Restore"), button -> controller.restore(world.worldId()))
                    .dimensions(x, y, contentWidth, 22).build());
            y += 30;
            addDrawableChild(ButtonWidget.builder(Text.literal("Delete Permanently"), button -> {
                if (client != null) client.setScreen(new DeleteWorldScreen(this, controller, world));
            }).dimensions(x, y, contentWidth, 20).build());
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
        context.fill(layout.left - 8, 26, layout.detailLeft - 8, layout.bottom, 0xA914181E);
        context.fill(layout.detailLeft, 26, layout.right, layout.bottom, 0xB91A1F26);

        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(textRenderer, title, layout.left, 12, 0xFFFFFF);

        if (controller.worlds().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("No managed worlds yet."), layout.left + layout.listWidth / 2, 92, 0xAEB7C4);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Use + Add World to create or import one."),
                    layout.left + layout.listWidth / 2, 108, 0x8F9AA8);
        }

        WorldControlWireProtocol.WorldSummary selected = selected();
        if (selected != null) {
            int tx = layout.detailLeft + 18;
            context.drawTextWithShadow(textRenderer, Text.literal(selected.displayName()), tx, 48, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer,
                    Text.literal(selected.lifecycle() + "  •  " + selected.runtimeState()), tx, 66, 0xAEB7C4);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Folder: " + selected.folderName()), tx, 82, 0x7F8996);
        } else if (!controller.worlds().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Select a world"), layout.detailLeft + layout.detailWidth / 2, 80, 0xAEB7C4);
        }

        renderOperationStatus(context, layout);

        if (controller.lastError() != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(controller.lastError()), width / 2, height - 46, 0xFF7777);
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
        int y = layout.bottom - 28;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label),
                layout.detailLeft + layout.detailWidth / 2, y, 0xD8DEE9);
        if (percent >= 0) {
            int barWidth = Math.min(260, layout.detailWidth - 36);
            int left = layout.detailLeft + (layout.detailWidth - barWidth) / 2;
            int filled = (int) Math.round(barWidth * (percent / 100.0));
            context.fill(left, y + 13, left + barWidth, y + 18, 0xFF303740);
            context.fill(left, y + 13, left + filled, y + 18, 0xFFD8DEE9);
        }
    }

    private Layout layout() {
        int totalWidth = Math.min(720, Math.max(500, width - 70));
        int left = (width - totalWidth) / 2;
        int right = left + totalWidth;
        int listWidth = Math.min(250, Math.max(190, totalWidth / 3));
        int detailLeft = left + listWidth + 16;
        int detailWidth = right - detailLeft;
        int bottom = height - 36;
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

    private record Layout(int left, int right, int listWidth, int detailLeft, int detailWidth, int bottom) {}
}

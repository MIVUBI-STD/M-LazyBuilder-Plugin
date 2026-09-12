package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/** First-party World Manager browser over one canonical world-control protocol. */
public final class WorldManagerScreen extends Screen {
    private static final int PAGE_SIZE = 3;

    private final ClientWorldController controller;
    private final ClientTransferController transfers;
    private UUID selectedWorld;
    private int page;
    private long observedRevision;

    public WorldManagerScreen(ClientWorldController controller, ClientTransferController transfers) {
        super(Text.literal("LazyBuilder World Manager"));
        this.controller = controller;
        this.transfers = transfers;
        this.observedRevision = controller.revision();
    }

    @Override
    protected void init() {
        observedRevision = controller.revision();
        List<WorldControlWireProtocol.WorldSummary> worlds = controller.worlds();
        int maxPage = worlds.isEmpty() ? 0 : (worlds.size() - 1) / PAGE_SIZE;
        page = Math.max(0, Math.min(page, maxPage));

        boolean selectedStillExists = selectedWorld == null || worlds.stream()
                .anyMatch(world -> world.worldId().equals(selectedWorld));
        if (!selectedStillExists) selectedWorld = null;

        int listWidth = Math.min(420, Math.max(280, width - 50));
        int left = (width - listWidth) / 2;
        int y = 42;
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, worlds.size());
        for (int i = start; i < end; i++) {
            WorldControlWireProtocol.WorldSummary world = worlds.get(i);
            String prefix = world.worldId().equals(selectedWorld) ? "> " : "";
            String label = prefix + world.displayName() + "  [" + world.lifecycle() + " / " + world.runtimeState() + "]";
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                selectedWorld = world.worldId();
                clearAndInit();
            }).dimensions(left, y, listWidth, 20).build());
            y += 24;
        }

        int controlsY = 42 + PAGE_SIZE * 24 + 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("Create"), button -> {
            if (client != null) client.setScreen(new CreateWorldScreen(this, controller));
        }).dimensions(left, controlsY, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Import"), button -> {
            if (client != null) client.setScreen(new ImportWorldScreen(this, controller, transfers));
        }).dimensions(left + 74, controlsY, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> controller.refresh())
                .dimensions(left + 148, controlsY, 68, 20).build());

        if (maxPage > 0) {
            addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> {
                page = Math.max(0, page - 1);
                clearAndInit();
            }).dimensions(left + 222, controlsY, 24, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> {
                page = Math.min(maxPage, page + 1);
                clearAndInit();
            }).dimensions(left + 250, controlsY, 24, 20).build());
        }

        WorldControlWireProtocol.WorldSummary selected = selected();
        if (selected != null) {
            int actionY = controlsY + 28;
            int actionWidth = 86;
            int gap = 6;
            int total = actionWidth * 3 + gap * 2;
            int actionX = (width - total) / 2;
            if ("ACTIVE".equals(selected.lifecycle())) {
                addDrawableChild(ButtonWidget.builder(Text.literal("Teleport"), button -> controller.teleport(selected.worldId()))
                        .dimensions(actionX, actionY, actionWidth, 20).build());
                String loadLabel = "LOADED".equals(selected.runtimeState()) ? "Unload" : "Load";
                addDrawableChild(ButtonWidget.builder(Text.literal(loadLabel), button -> {
                    if ("LOADED".equals(selected.runtimeState())) controller.unload(selected.worldId());
                    else controller.load(selected.worldId());
                }).dimensions(actionX + actionWidth + gap, actionY, actionWidth, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Archive"), button -> confirmArchive(selected))
                        .dimensions(actionX + (actionWidth + gap) * 2, actionY, actionWidth, 20).build());

                int secondY = actionY + 24;
                addDrawableChild(ButtonWidget.builder(Text.literal("Clone"), button -> {
                    if (client != null) client.setScreen(new CloneWorldScreen(this, controller, selected));
                }).dimensions(actionX, secondY, actionWidth, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Settings"), button -> {
                    controller.requestSettings(selected.worldId());
                    if (client != null) client.setScreen(new WorldSettingsScreen(this, controller, selected));
                }).dimensions(actionX + actionWidth + gap, secondY, actionWidth, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Export"), button -> {
                    if (client != null) client.setScreen(new ExportWorldScreen(this, controller, selected));
                }).dimensions(actionX + (actionWidth + gap) * 2, secondY, actionWidth, 20).build());

                int thirdY = secondY + 24;
                addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), button -> {
                    if (client != null) client.setScreen(new DeleteWorldScreen(this, controller, selected));
                }).dimensions(actionX + actionWidth + gap, thirdY, actionWidth, 20).build());
            } else if ("ARCHIVED".equals(selected.lifecycle())) {
                addDrawableChild(ButtonWidget.builder(Text.literal("Restore"), button -> controller.restore(selected.worldId()))
                        .dimensions(actionX, actionY, actionWidth, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), button -> {
                    if (client != null) client.setScreen(new DeleteWorldScreen(this, controller, selected));
                }).dimensions(actionX + (actionWidth + gap) * 2, actionY, actionWidth, 20).build());
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                .dimensions(left + listWidth - 72, height - 28, 72, 20).build());
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
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);

        if (controller.worlds().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("No managed worlds returned by the server."), width / 2, 70, 0xAAAAAA);
        }
        if (controller.lastError() != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(controller.lastError()), width / 2, height - 52, 0xFF7777);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(null);
    }

    private WorldControlWireProtocol.WorldSummary selected() {
        if (selectedWorld == null) return null;
        return controller.worlds().stream()
                .filter(world -> world.worldId().equals(selectedWorld))
                .findFirst()
                .orElse(null);
    }
}

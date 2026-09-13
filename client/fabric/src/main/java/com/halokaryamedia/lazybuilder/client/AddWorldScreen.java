package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** One clear entry point for adding a managed world. */
public final class AddWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController worlds;
    private final ClientTransferController transfers;

    public AddWorldScreen(Screen parent, ClientWorldController worlds, ClientTransferController transfers) {
        super(Text.literal("Add World"));
        this.parent = parent;
        this.worlds = worlds;
        this.transfers = transfers;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int buttonWidth = Math.min(320, width - 60);
        int left = center - buttonWidth / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Create New World"), button -> {
            if (client != null) client.setScreen(new CreateWorldScreen(parent, worlds));
        }).dimensions(left, 92, buttonWidth, 28).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Import Existing World"), button -> {
            if (client != null) client.setScreen(new ImportWorldScreen(parent, worlds, transfers));
        }).dimensions(left, 142, buttonWidth, 28).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(center - 50, 200, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 28, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Create a managed build world or bring an existing world into LazyBuilder."),
                width / 2, 54, 0xB8C0CC);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("New empty world managed by this server"), width / 2, 124, 0x8F9AA8);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Choose a .zip or .mcworld from your computer"), width / 2, 174, 0x8F9AA8);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}

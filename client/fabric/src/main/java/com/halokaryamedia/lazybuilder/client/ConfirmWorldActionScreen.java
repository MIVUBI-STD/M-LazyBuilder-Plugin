package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Lightweight confirmation surface for destructive/lifecycle world actions. */
public final class ConfirmWorldActionScreen extends Screen {
    private final Screen parent;
    private final Text message;
    private final String confirmLabel;
    private final Runnable action;

    public ConfirmWorldActionScreen(Screen parent, Text title, Text message, String confirmLabel, Runnable action) {
        super(title);
        this.parent = parent;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.action = action;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int y = height / 2 + 28;
        addDrawableChild(ButtonWidget.builder(Text.literal(confirmLabel), button -> {
            action.run();
            close();
        }).dimensions(center - 104, y, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(center + 4, y, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 36, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, message, width / 2, height / 2 - 8, 0xCCCCCC);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}

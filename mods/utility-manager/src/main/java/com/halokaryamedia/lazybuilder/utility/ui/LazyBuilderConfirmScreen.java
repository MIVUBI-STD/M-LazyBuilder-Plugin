package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Small custom confirmation surface that preserves the LazyBuilder settings visual language. */
final class LazyBuilderConfirmScreen extends Screen {
    private static final int BACKGROUND = 0xF20B0E12;
    private static final int PANEL = 0xF21A1F25;
    private static final int BORDER = 0x66545C66;

    private final Screen parent;
    private final String message;
    private final String confirmLabel;
    private final Runnable confirmAction;

    LazyBuilderConfirmScreen(
            Screen parent,
            String title,
            String message,
            String confirmLabel,
            Runnable confirmAction
    ) {
        super(Text.literal(title));
        this.parent = parent;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.confirmAction = confirmAction;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(360, Math.max(260, width - 32));
        int left = (width - panelWidth) / 2;
        int y = height / 2 + 32;

        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                left + 18,
                y,
                110,
                22,
                Text.literal("Cancel"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::close
        ));
        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                left + panelWidth - 128,
                y,
                110,
                22,
                Text.literal(confirmLabel),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                () -> {
                    confirmAction.run();
                    if (client != null) client.setScreen(parent);
                }
        ));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);

        int panelWidth = Math.min(360, Math.max(260, width - 32));
        int panelHeight = 124;
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;

        context.fill(left, top, left + panelWidth, top + panelHeight, BORDER);
        context.fill(left + 1, top + 1, left + panelWidth - 1, top + panelHeight - 1, PANEL);

        context.drawCenteredTextWithShadow(
                textRenderer,
                title,
                width / 2,
                top + 18,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );

        int textWidth = panelWidth - 36;
        int y = top + 40;
        for (var line : textRenderer.wrapLines(Text.literal(message), textWidth)) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    line,
                    width / 2,
                    y,
                    LazyBuilderSettingsScreen.TEXT_MUTED
            );
            y += 11;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}

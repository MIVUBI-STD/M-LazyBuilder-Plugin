package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Consistent LazyBuilder modal for lifecycle/destructive world actions. */
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
        int panelWidth = Math.min(420, width - 48);
        int left = width / 2 - panelWidth / 2;
        int half = (panelWidth - 66) / 2;
        int y = height / 2 + 34;
        boolean dangerous = confirmLabel.toLowerCase(java.util.Locale.ROOT).contains("delete")
                || confirmLabel.toLowerCase(java.util.Locale.ROOT).contains("reset");

        addDrawableChild(LbUi.button(left + 24, y, half, 26, confirmLabel,
                dangerous ? LbButtonWidget.Style.DANGER : LbButtonWidget.Style.PRIMARY,
                () -> { action.run(); close(); }));
        addDrawableChild(LbUi.button(left + 34 + half, y, half, 26, "Cancel",
                LbButtonWidget.Style.GHOST, this::close));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LbUi.background(context, width, height);
        int panelWidth = Math.min(420, width - 48);
        int panelHeight = 150;
        int left = width / 2 - panelWidth / 2;
        int top = height / 2 - panelHeight / 2;
        LbUi.elevatedPanel(context, left, top, panelWidth, panelHeight);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 26, LbUi.TEXT_PRIMARY);
        context.drawCenteredTextWithShadow(textRenderer, message, width / 2, top + 56, LbUi.TEXT_SECONDARY);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}

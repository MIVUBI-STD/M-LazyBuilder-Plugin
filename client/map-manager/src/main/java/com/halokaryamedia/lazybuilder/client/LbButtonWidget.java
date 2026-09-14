package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

import java.util.Objects;

/** Lightweight first-party LazyBuilder button; avoids vanilla button chrome. */
public final class LbButtonWidget extends PressableWidget {
    public enum Style { PRIMARY, SECONDARY, GHOST, DANGER }

    private final Runnable action;
    private final Style style;

    public LbButtonWidget(int x, int y, int width, int height, Text message, Style style, Runnable action) {
        super(x, y, width, height, message);
        this.style = Objects.requireNonNull(style, "style");
        this.action = Objects.requireNonNull(action, "action");
    }

    @Override
    public void onPress() {
        if (active) action.run();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hot = active && (isHovered() || isFocused());
        int x = getX();
        int y = getY();
        int right = x + getWidth();
        int bottom = y + getHeight();

        int border = switch (style) {
            case PRIMARY -> hot ? LbUi.ACCENT_BRIGHT : LbUi.ACCENT;
            case DANGER -> hot ? LbUi.DANGER_BRIGHT : LbUi.DANGER;
            case SECONDARY -> hot ? LbUi.BORDER_BRIGHT : LbUi.BORDER;
            case GHOST -> hot ? LbUi.BORDER : LbUi.SURFACE_2;
        };
        int fill = switch (style) {
            case PRIMARY -> hot ? LbUi.ACCENT_HOVER : LbUi.ACCENT_FILL;
            case DANGER -> hot ? LbUi.DANGER_HOVER : LbUi.DANGER_FILL;
            case SECONDARY -> hot ? LbUi.SURFACE_3 : LbUi.SURFACE_2;
            case GHOST -> hot ? LbUi.SURFACE_2 : LbUi.SURFACE_1;
        };
        int text = active ? LbUi.TEXT_PRIMARY : LbUi.TEXT_DISABLED;

        context.fill(x, y, right, bottom, border);
        context.fill(x + 1, y + 1, right - 1, bottom - 1, fill);

        var renderer = MinecraftClient.getInstance().textRenderer;
        int textY = y + (getHeight() - 8) / 2;
        context.drawCenteredTextWithShadow(renderer, getMessage(), x + getWidth() / 2, textY, text);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}

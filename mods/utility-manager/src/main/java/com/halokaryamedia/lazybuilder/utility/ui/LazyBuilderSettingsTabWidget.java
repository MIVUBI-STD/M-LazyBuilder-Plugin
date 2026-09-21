package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

import java.util.Objects;

/** Minimal top navigation tab matching the LazyBuilder game-settings shell. */
final class LazyBuilderSettingsTabWidget extends PressableWidget {
    private final Runnable action;
    private final boolean selected;

    LazyBuilderSettingsTabWidget(
            int x,
            int y,
            int width,
            int height,
            Text label,
            boolean selected,
            Runnable action
    ) {
        super(x, y, width, height, label);
        this.selected = selected;
        this.action = Objects.requireNonNull(action, "action");
    }

    @Override
    public void onPress() {
        if (!selected) action.run();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        int textColor = selected
                ? LazyBuilderSettingsScreen.TEXT_PRIMARY
                : isHovered() || isFocused()
                ? LazyBuilderSettingsScreen.TEXT_PRIMARY
                : LazyBuilderSettingsScreen.TEXT_MUTED;

        if (isHovered() || isFocused()) {
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x331F252C);
        }

        Text visible = getMessage();
        int available = Math.max(8, getWidth() - 10);
        if (renderer.getWidth(visible) > available) {
            String ellipsis = "…";
            int labelWidth = Math.max(0, available - renderer.getWidth(ellipsis));
            visible = Text.literal(renderer.trimToWidth(visible.getString(), labelWidth) + ellipsis);
        }

        context.drawCenteredTextWithShadow(
                renderer,
                visible,
                getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2,
                textColor
        );

        if (selected) {
            context.fill(
                    getX() + 8,
                    getY() + getHeight() - 2,
                    getX() + getWidth() - 8,
                    getY() + getHeight(),
                    LazyBuilderSettingsScreen.ACCENT
            );
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}

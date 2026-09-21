package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

import java.util.Objects;

/** Search result row for the unified LazyBuilder Settings search. */
final class LazyBuilderSettingsSearchResultWidget extends PressableWidget {
    private final String path;
    private final Runnable action;

    LazyBuilderSettingsSearchResultWidget(
            int x,
            int y,
            int width,
            int height,
            String title,
            String path,
            Runnable action
    ) {
        super(x, y, width, height, Text.literal(title));
        this.path = Objects.requireNonNull(path, "path");
        this.action = Objects.requireNonNull(action, "action");
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        boolean hot = isHovered() || isFocused();
        int fill = hot ? 0xC521272E : 0xA81A1F25;
        int border = hot ? LazyBuilderSettingsScreen.ACCENT : 0x44545C66;

        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
        context.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, fill);

        int titleMax = Math.max(80, getWidth() / 2);
        context.drawTextWithShadow(
                renderer,
                renderer.trimToWidth(getMessage().getString(), titleMax),
                getX() + 8,
                getY() + 7,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );

        int pathWidth = renderer.getWidth(path);
        context.drawTextWithShadow(
                renderer,
                path,
                getX() + getWidth() - pathWidth - 20,
                getY() + 7,
                hot ? LazyBuilderSettingsScreen.TEXT_SECONDARY : LazyBuilderSettingsScreen.TEXT_MUTED
        );
        context.drawTextWithShadow(
                renderer,
                Text.literal(">"),
                getX() + getWidth() - 11,
                getY() + 7,
                hot ? LazyBuilderSettingsScreen.ACCENT : LazyBuilderSettingsScreen.TEXT_MUTED
        );
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}

package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

import java.util.Objects;

/** Compact right-side control used by game-style settings rows. */
final class LazyBuilderSettingsControlWidget extends PressableWidget {
    private final Runnable action;
    private final boolean interactive;

    LazyBuilderSettingsControlWidget(
            int x,
            int y,
            int width,
            int height,
            Text label,
            boolean interactive,
            Runnable action
    ) {
        super(x, y, width, height, label);
        this.interactive = interactive;
        this.action = Objects.requireNonNull(action, "action");
        this.active = interactive;
    }

    @Override
    public void onPress() {
        if (interactive) action.run();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int right = x + getWidth();
        int bottom = y + getHeight();

        int fill = interactive && (isHovered() || isFocused()) ? 0xDD2A3038 : 0xCC20252C;
        int border = interactive && (isHovered() || isFocused())
                ? LazyBuilderSettingsScreen.ACCENT
                : 0x554A525C;
        int text = interactive
                ? LazyBuilderSettingsScreen.TEXT_PRIMARY
                : LazyBuilderSettingsScreen.TEXT_MUTED;

        context.fill(x, y, right, bottom, border);
        context.fill(x + 1, y + 1, right - 1, bottom - 1, fill);

        var renderer = MinecraftClient.getInstance().textRenderer;
        Text visible = fitted(renderer);
        context.drawCenteredTextWithShadow(
                renderer,
                visible,
                x + getWidth() / 2,
                y + (getHeight() - 8) / 2,
                text
        );
    }

    private Text fitted(net.minecraft.client.font.TextRenderer renderer) {
        int available = Math.max(0, getWidth() - 10);
        if (renderer.getWidth(getMessage()) <= available) return getMessage();
        String ellipsis = "…";
        int labelWidth = Math.max(0, available - renderer.getWidth(ellipsis));
        return Text.literal(renderer.trimToWidth(getMessage().getString(), labelWidth) + ellipsis);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}

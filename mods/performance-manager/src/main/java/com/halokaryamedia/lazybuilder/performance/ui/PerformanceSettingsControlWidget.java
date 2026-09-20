package com.halokaryamedia.lazybuilder.performance.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

import java.util.Objects;

/** Compact control matching the game-style Performance settings surface. */
final class PerformanceSettingsControlWidget extends PressableWidget {
    private static final int TEXT_PRIMARY = 0xFFF2F4F6;
    private static final int TEXT_MUTED = 0xFF858D97;
    private static final int ACCENT = 0xFFF1D21A;

    private final Runnable action;
    private final boolean interactive;

    PerformanceSettingsControlWidget(
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
        int border = interactive && (isHovered() || isFocused()) ? ACCENT : 0x554A525C;
        int fill = interactive && (isHovered() || isFocused()) ? 0xDD2A3038 : 0xCC20252C;
        int text = interactive ? TEXT_PRIMARY : TEXT_MUTED;

        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
        context.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, fill);

        var renderer = MinecraftClient.getInstance().textRenderer;
        Text visible = fitted(renderer);
        context.drawCenteredTextWithShadow(
                renderer,
                visible,
                getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2,
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

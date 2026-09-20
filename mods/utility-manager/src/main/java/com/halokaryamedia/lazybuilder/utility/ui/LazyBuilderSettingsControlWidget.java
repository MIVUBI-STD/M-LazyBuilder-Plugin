package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

import java.util.Objects;

/** Right-side control for the modern game-style settings shell. */
final class LazyBuilderSettingsControlWidget extends PressableWidget {
    enum Kind { TOGGLE, ACTION, STATUS, FOOTER }

    private final Runnable action;
    private final boolean interactive;
    private final Kind kind;

    LazyBuilderSettingsControlWidget(
            int x,
            int y,
            int width,
            int height,
            Text label,
            boolean interactive,
            Kind kind,
            Runnable action
    ) {
        super(x, y, width, height, label);
        this.interactive = interactive;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.action = Objects.requireNonNull(action, "action");
        this.active = interactive;
    }

    @Override
    public void onPress() {
        if (interactive) action.run();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        boolean hot = interactive && (isHovered() || isFocused());

        switch (kind) {
            case TOGGLE -> renderToggle(context, renderer, hot);
            case STATUS -> renderStatus(context, renderer);
            case ACTION -> renderAction(context, renderer, hot);
            case FOOTER -> renderFooter(context, renderer, hot);
        }
    }

    private void renderToggle(
            DrawContext context,
            net.minecraft.client.font.TextRenderer renderer,
            boolean hot
    ) {
        boolean on = "ON".equalsIgnoreCase(getMessage().getString());
        int textColor = hot ? LazyBuilderSettingsScreen.TEXT_PRIMARY : LazyBuilderSettingsScreen.TEXT_SECONDARY;
        int switchWidth = 28;
        int switchHeight = 12;
        int switchX = getX() + getWidth() - switchWidth;
        int switchY = getY() + (getHeight() - switchHeight) / 2;

        String state = on ? "On" : "Off";
        int stateWidth = renderer.getWidth(state);
        context.drawTextWithShadow(
                renderer,
                Text.literal(state),
                switchX - stateWidth - 8,
                getY() + (getHeight() - 8) / 2,
                textColor
        );

        int border = hot ? LazyBuilderSettingsScreen.ACCENT : 0x665D6670;
        int fill = on ? 0xCC1F6558 : 0xCC20252C;
        context.fill(switchX, switchY, switchX + switchWidth, switchY + switchHeight, border);
        context.fill(switchX + 1, switchY + 1, switchX + switchWidth - 1, switchY + switchHeight - 1, fill);

        int knobWidth = 10;
        int knobX = on ? switchX + switchWidth - knobWidth - 2 : switchX + 2;
        context.fill(
                knobX,
                switchY + 2,
                knobX + knobWidth,
                switchY + switchHeight - 2,
                on ? LazyBuilderSettingsScreen.ACCENT : 0xFF9299A2
        );
    }

    private void renderStatus(DrawContext context, net.minecraft.client.font.TextRenderer renderer) {
        Text visible = fitted(renderer, getWidth());
        int textWidth = renderer.getWidth(visible);
        context.drawTextWithShadow(
                renderer,
                visible,
                getX() + getWidth() - textWidth,
                getY() + (getHeight() - 8) / 2,
                LazyBuilderSettingsScreen.TEXT_MUTED
        );
    }

    private void renderAction(
            DrawContext context,
            net.minecraft.client.font.TextRenderer renderer,
            boolean hot
    ) {
        int border = hot ? LazyBuilderSettingsScreen.ACCENT : 0x66545C66;
        int fill = hot ? 0xE02A3038 : 0xD31A1F25;
        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
        context.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, fill);

        Text visible = fitted(renderer, getWidth() - 18);
        context.drawCenteredTextWithShadow(
                renderer,
                visible,
                getX() + getWidth() / 2 - 4,
                getY() + (getHeight() - 8) / 2,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );
        context.drawTextWithShadow(
                renderer,
                Text.literal(">"),
                getX() + getWidth() - 11,
                getY() + (getHeight() - 8) / 2,
                hot ? LazyBuilderSettingsScreen.ACCENT : LazyBuilderSettingsScreen.TEXT_MUTED
        );
    }

    private void renderFooter(
            DrawContext context,
            net.minecraft.client.font.TextRenderer renderer,
            boolean hot
    ) {
        int border = hot ? LazyBuilderSettingsScreen.ACCENT : 0x66545C66;
        int fill = hot ? 0xE02A3038 : 0xD31A1F25;
        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
        context.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, fill);
        context.drawCenteredTextWithShadow(
                renderer,
                fitted(renderer, getWidth() - 10),
                getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2,
                interactive ? LazyBuilderSettingsScreen.TEXT_PRIMARY : LazyBuilderSettingsScreen.TEXT_MUTED
        );
    }

    private Text fitted(net.minecraft.client.font.TextRenderer renderer, int available) {
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

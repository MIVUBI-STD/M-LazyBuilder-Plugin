package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Tool-specific setup lives here. Settings that naturally extend Minecraft (video,
 * controls, interface and performance) stay in their normal Settings categories.
 */
public final class LazyBuilderToolsScreen extends Screen {
    private static final int MAX_CONTENT_WIDTH = 520;
    private static final int SCREEN_MARGIN = 18;
    private static final int TITLE_Y = 18;
    private static final int CONTENT_TOP = 52;
    private static final int ROW_HEIGHT = 36;
    private static final int ROW_GAP = 4;
    private static final int DONE_WIDTH = 120;
    private static final int TEXT_PRIMARY = 0xFFF3F6FA;
    private static final int TEXT_SECONDARY = 0xFFB0BAC7;
    private static final int TEXT_MUTED = 0xFF8B949E;
    private static final int ROW_FILL = 0x88000000;
    private static final int ROW_BORDER = 0x447F8A98;

    private final Screen parent;

    public LazyBuilderToolsScreen(Screen parent) {
        super(Text.literal("Tools"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Editing Tools and Map & Worlds currently own their configuration in their
        // actual workflows. Do not duplicate those settings here until a global setup
        // decision genuinely exists.
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .dimensions(this.width / 2 - DONE_WIDTH / 2, this.height - 32, DONE_WIDTH, 20)
                        .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int left = contentLeft();
        int right = left + contentWidth();

        context.drawTextWithShadow(this.textRenderer, Text.literal("TOOLS"), left, TITLE_Y, TEXT_PRIMARY);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Tool-specific setup and configuration"),
                left,
                TITLE_Y + 14,
                TEXT_SECONDARY
        );

        int y = CONTENT_TOP;
        drawRow(context, left, right, y,
                "Editing Tools",
                "Axiom and LazyBuilder building extensions",
                "Configured in editor");
        y += ROW_HEIGHT + ROW_GAP;

        drawRow(context, left, right, y,
                "Map & Worlds",
                "World map, world management and transfers",
                "Configured per world");

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawRow(
            DrawContext context,
            int left,
            int right,
            int y,
            String label,
            String description,
            String state
    ) {
        context.fill(left, y, right, y + ROW_HEIGHT, ROW_BORDER);
        context.fill(left + 1, y + 1, right - 1, y + ROW_HEIGHT - 1, ROW_FILL);

        context.drawTextWithShadow(this.textRenderer, Text.literal(label), left + 8, y + 7, TEXT_PRIMARY);
        int stateWidth = this.textRenderer.getWidth(state);
        context.drawTextWithShadow(this.textRenderer, Text.literal(state), right - stateWidth - 8, y + 7, TEXT_SECONDARY);

        int maxDescriptionWidth = Math.max(0, contentWidth() - 32);
        String clipped = this.textRenderer.trimToWidth(description, maxDescriptionWidth);
        context.drawTextWithShadow(this.textRenderer, Text.literal(clipped), left + 8, y + 21, TEXT_MUTED);
    }

    private int contentWidth() {
        return Math.min(MAX_CONTENT_WIDTH, Math.max(280, this.width - SCREEN_MARGIN * 2));
    }

    private int contentLeft() {
        return (this.width - contentWidth()) / 2;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}

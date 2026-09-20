package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * LazyBuilder's replacement for a generic Mods screen.
 *
 * It reports the four client tool suites that are present in the workspace and exposes
 * only configuration that is meaningful in-game. A loaded Fabric JAR is reported as ON;
 * this screen does not pretend it can unload a mod at runtime.
 */
public final class LazyBuilderToolsScreen extends Screen {
    private static final int MAX_CONTENT_WIDTH = 520;
    private static final int SCREEN_MARGIN = 18;
    private static final int TITLE_Y = 18;
    private static final int CONTENT_TOP = 48;
    private static final int ROW_HEIGHT = 34;
    private static final int ROW_GAP = 4;
    private static final int STATUS_WIDTH = 78;
    private static final int DONE_WIDTH = 120;

    private static final int ROW_FILL = 0x88000000;
    private static final int ROW_BORDER = 0x447F8A98;
    private static final int TEXT_PRIMARY = 0xFFF3F6FA;
    private static final int TEXT_SECONDARY = 0xFFB0BAC7;
    private static final int TEXT_MUTED = 0xFF8B949E;

    private final Screen parent;

    public LazyBuilderToolsScreen(Screen parent) {
        super(Text.literal("Tools"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int right = contentLeft() + contentWidth();
        int y = CONTENT_TOP;

        addActiveStatus(right, y);
        y += ROW_HEIGHT + ROW_GAP;
        addActiveStatus(right, y);
        y += ROW_HEIGHT + ROW_GAP;
        addActiveStatus(right, y);
        y += ROW_HEIGHT + ROW_GAP;

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Configure >"), button -> {
                            if (this.client != null) {
                                this.client.setScreen(new LazyBuilderUtilityToolsScreen(this));
                            }
                        })
                        .dimensions(right - 96, y + 7, 92, 20)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .dimensions(this.width / 2 - DONE_WIDTH / 2, this.height - 32, DONE_WIDTH, 20)
                        .build()
        );
    }

    private void addActiveStatus(int right, int y) {
        ButtonWidget status = ButtonWidget.builder(Text.literal("ON"), button -> {})
                .dimensions(right - STATUS_WIDTH - 4, y + 7, STATUS_WIDTH, 20)
                .build();
        status.active = false;
        this.addDrawableChild(status);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int left = contentLeft();
        int right = left + contentWidth();

        context.drawTextWithShadow(this.textRenderer, Text.literal("TOOLS"), left, TITLE_Y, TEXT_PRIMARY);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("LazyBuilder client modules"),
                left,
                TITLE_Y + 14,
                TEXT_SECONDARY
        );

        int y = CONTENT_TOP;
        drawModuleRow(context, left, right, y, "Editing Tools", "Axiom-first building extensions");
        y += ROW_HEIGHT + ROW_GAP;
        drawModuleRow(context, left, right, y, "Map", "World map and world-management client tools");
        y += ROW_HEIGHT + ROW_GAP;
        drawModuleRow(context, left, right, y, "Performance", "Automatic rendering and memory optimizations");
        y += ROW_HEIGHT + ROW_GAP;
        drawModuleRow(context, left, right, y, "Utilities", "Client convenience and interface helpers");

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawModuleRow(
            DrawContext context,
            int left,
            int right,
            int y,
            String label,
            String description
    ) {
        context.fill(left, y, right, y + ROW_HEIGHT, ROW_BORDER);
        context.fill(left + 1, y + 1, right - 1, y + ROW_HEIGHT - 1, ROW_FILL);

        context.drawTextWithShadow(this.textRenderer, Text.literal(label), left + 8, y + 7, TEXT_PRIMARY);
        int maxDescriptionWidth = Math.max(0, contentWidth() - 120);
        String clipped = this.textRenderer.trimToWidth(description, maxDescriptionWidth);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(clipped),
                left + 8,
                y + 19,
                TEXT_MUTED
        );
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

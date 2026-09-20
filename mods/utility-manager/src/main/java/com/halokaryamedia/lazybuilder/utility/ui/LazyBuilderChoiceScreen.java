package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/** Lightweight custom choice list used by the permanent LazyBuilder settings UI. */
final class LazyBuilderChoiceScreen extends Screen {
    record Choice(String label, boolean selected, Runnable action) {}

    private static final int BACKGROUND = 0xF20B0E12;
    private static final int TOP_BAR = 0xE813171C;
    private static final int ROW_FILL = 0xA81A1F25;
    private static final int ROW_HOVER = 0xC521272E;
    private static final int DIVIDER = 0x44545C66;
    private static final int ROW_HEIGHT = 30;
    private static final int ROW_GAP = 2;
    private static final int FOOTER_HEIGHT = 40;

    private final Screen parent;
    private final List<Choice> choices;
    private int scrollOffset;
    private int maxScroll;

    LazyBuilderChoiceScreen(Screen parent, String title, List<Choice> choices) {
        super(Text.literal(title));
        this.parent = parent;
        this.choices = List.copyOf(choices);
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int width = panelWidth();
        int viewportBottom = viewportBottom();
        int total = choices.size() * (ROW_HEIGHT + ROW_GAP);
        maxScroll = Math.max(0, total - Math.max(1, viewportBottom - 74));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);
            int y = 74 + i * (ROW_HEIGHT + ROW_GAP) - scrollOffset;
            if (y + ROW_HEIGHT <= 66 || y >= viewportBottom) continue;

            this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                    left,
                    y,
                    width,
                    ROW_HEIGHT,
                    Text.literal(choice.label() + (choice.selected() ? "   ✓" : "")),
                    true,
                    LazyBuilderSettingsControlWidget.Kind.FOOTER,
                    () -> {
                        choice.action().run();
                        if (this.client != null && this.client.currentScreen == this) {
                            this.close();
                        }
                    }
            ));
        }

        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                shellLeft() + shellWidth() - 100,
                height - 30,
                92,
                22,
                Text.literal("Back"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::close
        ));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);
        context.fill(0, 0, width, 34, TOP_BAR);
        context.fill(0, height - FOOTER_HEIGHT, width, height, TOP_BAR);

        int left = panelLeft();
        int right = left + panelWidth();
        context.drawTextWithShadow(textRenderer, Text.literal("SETTINGS"), shellLeft() + 8, 15, LazyBuilderSettingsScreen.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, this.title, left, 50, LazyBuilderSettingsScreen.TEXT_PRIMARY);

        context.enableScissor(left, 66, right, viewportBottom());
        for (int i = 0; i < choices.size(); i++) {
            int y = 74 + i * (ROW_HEIGHT + ROW_GAP) - scrollOffset;
            if (y + ROW_HEIGHT <= 66 || y >= viewportBottom()) continue;
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT;
            context.fill(left, y, right, y + ROW_HEIGHT - 1, hovered ? ROW_HOVER : ROW_FILL);
            context.fill(left, y + ROW_HEIGHT - 1, right, y + ROW_HEIGHT, DIVIDER);
        }
        context.disableScissor();
        renderScrollBar(context, right + 6);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderScrollBar(DrawContext context, int x) {
        if (maxScroll <= 0) return;
        int top = 66;
        int bottom = viewportBottom();
        int trackHeight = Math.max(1, bottom - top);
        int contentHeight = trackHeight + maxScroll;
        int thumbHeight = Math.max(18, trackHeight * trackHeight / contentHeight);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + (int) Math.round((scrollOffset / (double) maxScroll) * travel);
        context.fill(x, top, x + 2, bottom, 0x334A525C);
        context.fill(x, thumbY, x + 2, thumbY + thumbHeight, LazyBuilderSettingsScreen.ACCENT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0 && mouseX >= panelLeft() && mouseX <= panelLeft() + panelWidth()) {
            int next = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(verticalAmount * 24.0)));
            if (next != scrollOffset) {
                scrollOffset = next;
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int viewportBottom() {
        return Math.max(67, height - FOOTER_HEIGHT - 6);
    }

    private int shellWidth() {
        return Math.min(920, Math.max(280, width - 24));
    }

    private int shellLeft() {
        return (width - shellWidth()) / 2;
    }

    private int panelWidth() {
        return Math.min(520, Math.max(264, shellWidth() - 16));
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
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

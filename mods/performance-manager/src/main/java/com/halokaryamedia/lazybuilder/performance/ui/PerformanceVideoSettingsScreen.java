package com.halokaryamedia.lazybuilder.performance.ui;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.PerformancePreferences;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern game-style performance settings.
 * Common controls stay visible first; only engine-level tuning lives in Advanced.
 */
public final class PerformanceVideoSettingsScreen extends Screen {
    private static final int TEXT_PRIMARY = 0xFFF2F4F6;
    private static final int TEXT_SECONDARY = 0xFFB8BEC6;
    private static final int TEXT_MUTED = 0xFF858D97;
    private static final int ACCENT = 0xFFF1D21A;

    private static final int BACKGROUND = 0xD90B0E12;
    private static final int TOP_BAR = 0xE813171C;
    private static final int ROW_FILL = 0xA81A1F25;
    private static final int ROW_HOVER = 0xC521272E;
    private static final int DIVIDER = 0x44545C66;

    private static final int MAX_SHELL_WIDTH = 920;
    private static final int MIN_SIDE_MARGIN = 12;
    private static final int CONTENT_TOP = 88;
    private static final int VIEWPORT_TOP = 78;
    private static final int SECTION_HEIGHT = 18;
    private static final int SECTION_GAP = 12;
    private static final int ROW_HEIGHT = 40;
    private static final int ROW_GAP = 2;
    private static final int CONTROL_WIDTH = 132;
    private static final int CONTROL_HEIGHT = 22;
    private static final int FOOTER_HEIGHT = 40;

    private static final int[] BACKGROUND_LIMITS = {15, 30, 45, 60, 90, 120};
    private static final int[] MINIMIZED_LIMITS = {5, 10, 15, 30};

    private final Screen parent;
    private final List<Section> sections = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;

    public PerformanceVideoSettingsScreen(Screen parent) {
        super(Text.literal("Performance"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        sections.clear();
        PerformancePreferences prefs = PerformanceManagerClient.preferences();

        Section general = new Section("GENERAL");
        general.rows.add(Row.toggle(
                "Reduce FPS in Background",
                "Use less GPU power when Minecraft is not the active window.",
                prefs.backgroundFpsPolicy(),
                value -> update(prefs.withBackgroundFpsPolicy(value))
        ));
        general.rows.add(Row.value(
                "Background FPS",
                "Frame-rate limit while Minecraft is running in the background.",
                prefs.unfocusedFpsLimit() + " FPS",
                () -> update(prefs.withUnfocusedFpsLimit(
                        nextValue(BACKGROUND_LIMITS, prefs.unfocusedFpsLimit())
                ))
        ));
        general.rows.add(Row.value(
                "Minimized FPS",
                "Frame-rate limit while the game window is minimized.",
                prefs.minimizedFpsLimit() + " FPS",
                () -> update(prefs.withMinimizedFpsLimit(
                        nextValue(MINIMIZED_LIMITS, prefs.minimizedFpsLimit())
                ))
        ));
        sections.add(general);

        Section advanced = new Section("ADVANCED");
        advanced.rows.add(Row.toggle(
                "Skip Unseen Objects",
                "Stop drawing entities and special blocks when they are fully hidden.",
                prefs.hiddenObjectSkipping(),
                value -> update(prefs.withHiddenObjectSkipping(value))
        ));
        advanced.rows.add(Row.toggle(
                "Faster World Rendering",
                "Use the optimized world rendering path.",
                prefs.renderingOptimizations(),
                value -> update(prefs.withRenderingOptimizations(value))
        ));
        advanced.rows.add(Row.toggle(
                "Lower Memory Usage",
                "Reuse compatible rendering data to reduce memory pressure.",
                prefs.memoryOptimizations(),
                value -> update(prefs.withMemoryOptimizations(value))
        ));
        sections.add(advanced);

        layoutSections();
        addFooter();
    }

    private void layoutSections() {
        int x = panelLeft();
        int width = panelWidth();
        int viewportBottom = viewportBottom();
        int contentHeight = totalContentHeight();
        int viewportHeight = Math.max(1, viewportBottom - CONTENT_TOP);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        int baseY = CONTENT_TOP;

        for (Section section : sections) {
            section.y = baseY - scrollOffset;
            baseY += SECTION_HEIGHT;

            for (Row row : section.rows) {
                row.x = x;
                row.y = baseY - scrollOffset;
                row.width = width;

                int controlWidth = Math.min(CONTROL_WIDTH, Math.max(96, width / 3));
                if (row.y + ROW_HEIGHT > VIEWPORT_TOP && row.y < viewportBottom) {
                    this.addDrawableChild(new PerformanceSettingsControlWidget(
                            x + width - controlWidth - 8,
                            row.y + (ROW_HEIGHT - CONTROL_HEIGHT) / 2,
                            controlWidth,
                            CONTROL_HEIGHT,
                            Text.literal(row.controlLabel),
                            row.kind,
                            row.action
                    ));
                }

                baseY += ROW_HEIGHT + ROW_GAP;
            }

            baseY += SECTION_GAP;
        }

    }

    private int totalContentHeight() {
        int total = 0;
        for (Section section : sections) {
            total += SECTION_HEIGHT;
            total += section.rows.size() * (ROW_HEIGHT + ROW_GAP);
            total += SECTION_GAP;
        }
        return Math.max(0, total);
    }

    private void addFooter() {
        int y = height - 30;
        int right = shellLeft() + shellWidth();

        this.addDrawableChild(new PerformanceSettingsControlWidget(
                right - 196,
                y,
                92,
                22,
                Text.literal("Back"),
                PerformanceSettingsControlWidget.Kind.FOOTER,
                this::close
        ));

        this.addDrawableChild(new PerformanceSettingsControlWidget(
                right - 96,
                y,
                92,
                22,
                Text.literal("Done"),
                PerformanceSettingsControlWidget.Kind.FOOTER,
                this::close
        ));
    }

    private void update(PerformancePreferences updated) {
        PerformanceManagerClient.updatePreferences(updated);
        if (client != null) {
            client.setScreen(new PerformanceVideoSettingsScreen(parent));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);
        context.fill(0, 0, width, 34, TOP_BAR);
        context.fill(0, height - FOOTER_HEIGHT, width, height, TOP_BAR);

        int panelLeft = panelLeft();
        int panelRight = panelLeft + panelWidth();

        context.drawTextWithShadow(textRenderer, Text.literal("SETTINGS"), shellLeft() + 8, 15, TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("VIDEO"), panelLeft, 49, TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal("PERFORMANCE"), panelLeft + 58, 49, TEXT_PRIMARY);
        context.fill(panelLeft + 58, 63, panelLeft + 126, 65, ACCENT);
        context.fill(panelRight + 14, 76, panelRight + 15, height - FOOTER_HEIGHT - 10, DIVIDER);

        context.enableScissor(panelLeft, VIEWPORT_TOP, panelRight, viewportBottom());
        for (Section section : sections) {
            if (section.y + SECTION_HEIGHT > VIEWPORT_TOP && section.y < viewportBottom()) {
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(section.title),
                        panelLeft,
                        section.y + 4,
                        TEXT_SECONDARY
                );
            }

            for (Row row : section.rows) {
                if (row.y + ROW_HEIGHT <= VIEWPORT_TOP || row.y >= viewportBottom()) continue;

                boolean hovered = mouseX >= row.x && mouseX < row.x + row.width
                        && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
                int fill = hovered ? ROW_HOVER : ROW_FILL;
                context.fill(row.x, row.y + ROW_HEIGHT - 1, row.x + row.width, row.y + ROW_HEIGHT, DIVIDER);
                context.fill(row.x, row.y, row.x + row.width, row.y + ROW_HEIGHT - 1, fill);

                int textMax = Math.max(70, row.width - CONTROL_WIDTH - 34);
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(textRenderer.trimToWidth(row.title, textMax)),
                        row.x + 8,
                        row.y + 8,
                        TEXT_PRIMARY
                );
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(textRenderer.trimToWidth(row.description, textMax)),
                        row.x + 8,
                        row.y + 23,
                        TEXT_MUTED
                );
            }
        }
        context.disableScissor();

        renderScrollBar(context, panelRight + 6);
        renderContextPane(context, panelRight + 30);
        renderStatus(context, panelLeft);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderContextPane(DrawContext context, int x) {
        int available = shellLeft() + shellWidth() - x - 8;
        if (available < 120) return;

        context.drawTextWithShadow(textRenderer, Text.literal("PERFORMANCE"), x, 88, TEXT_PRIMARY);
        List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(
                Text.literal("Performance controls use safe defaults. Advanced options are intended for troubleshooting or tuning."),
                available
        );
        int y = 106;
        for (net.minecraft.text.OrderedText line : lines) {
            context.drawTextWithShadow(textRenderer, line, x, y, TEXT_MUTED);
            y += 11;
        }
    }

    private void renderStatus(DrawContext context, int x) {
        String status = userFacingStatus();
        if (!status.isBlank()) {
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(status),
                    x,
                    height - FOOTER_HEIGHT - 18,
                    0xFFFF9A9A
            );
        }
    }

    private static String userFacingStatus() {
        String status = PerformanceManagerClient.lastPreferenceUpdateStatus();
        if ("rendering-disable-blocked:terrain-recovery-failed".equals(status)) {
            return "Faster World Rendering could not be disabled safely.";
        }
        return "";
    }

    private static int nextValue(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return values[(i + 1) % values.length];
        }
        for (int value : values) {
            if (value > current) return value;
        }
        return values[0];
    }

    private void renderScrollBar(DrawContext context, int x) {
        if (maxScroll <= 0) return;
        int top = VIEWPORT_TOP;
        int bottom = viewportBottom();
        int trackHeight = Math.max(1, bottom - top);
        int contentHeight = trackHeight + maxScroll;
        int thumbHeight = Math.max(18, trackHeight * trackHeight / contentHeight);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + (int) Math.round((scrollOffset / (double) maxScroll) * travel);

        context.fill(x, top, x + 2, bottom, 0x334A525C);
        context.fill(x, thumbY, x + 2, thumbY + thumbHeight, ACCENT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0
                && mouseX >= panelLeft()
                && mouseX <= panelLeft() + panelWidth()
                && mouseY >= VIEWPORT_TOP
                && mouseY <= viewportBottom()) {
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
        return Math.max(VIEWPORT_TOP + 1, height - FOOTER_HEIGHT - 6);
    }

    private int shellWidth() {
        return Math.min(MAX_SHELL_WIDTH, Math.max(280, width - MIN_SIDE_MARGIN * 2));
    }

    private int shellLeft() {
        return (width - shellWidth()) / 2;
    }

    private int panelWidth() {
        int shell = shellWidth();
        if (shell < 560) return shell - 16;
        return Math.min(520, Math.max(390, (int) (shell * 0.68)));
    }

    private int panelLeft() {
        return shellLeft() + 8;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    private static final class Section {
        private final String title;
        private final List<Row> rows = new ArrayList<>();
        private int y;

        private Section(String title) {
            this.title = title;
        }
    }

    private static final class Row {
        private final String title;
        private final String description;
        private final String controlLabel;
        private final PerformanceSettingsControlWidget.Kind kind;
        private final Runnable action;
        private int x;
        private int y;
        private int width;

        private Row(
                String title,
                String description,
                String controlLabel,
                PerformanceSettingsControlWidget.Kind kind,
                Runnable action
        ) {
            this.title = title;
            this.description = description;
            this.controlLabel = controlLabel;
            this.kind = kind;
            this.action = action;
        }

        static Row value(String title, String description, String value, Runnable action) {
            return new Row(title, description, value, PerformanceSettingsControlWidget.Kind.VALUE, action);
        }

        static Row toggle(
                String title,
                String description,
                boolean enabled,
                java.util.function.Consumer<Boolean> setter
        ) {
            return new Row(
                    title,
                    description,
                    enabled ? "ON" : "OFF",
                    PerformanceSettingsControlWidget.Kind.TOGGLE,
                    () -> setter.accept(!enabled)
            );
        }
    }
}

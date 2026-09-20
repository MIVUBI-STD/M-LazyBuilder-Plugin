package com.halokaryamedia.lazybuilder.performance.ui;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.PerformancePreferences;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern game-style performance settings surface.
 * Common controls are first; Advanced remains a simple bottom section.
 */
public final class PerformanceVideoSettingsScreen extends Screen {
    private static final int TEXT_PRIMARY = 0xFFF2F4F6;
    private static final int TEXT_SECONDARY = 0xFFB8BEC6;
    private static final int TEXT_MUTED = 0xFF858D97;
    private static final int ACCENT = 0xFFF1D21A;

    private static final int BACKGROUND = 0xEE0B0E12;
    private static final int TOP_BAR = 0xF013171C;
    private static final int ROW_FILL = 0xC91A1F25;
    private static final int ROW_HOVER = 0xDD21272E;
    private static final int ROW_BORDER = 0x334E5660;

    private static final int MAX_WIDTH = 760;
    private static final int SIDE_MARGIN = 18;
    private static final int ROW_HEIGHT = 46;
    private static final int ROW_GAP = 6;
    private static final int COLUMN_GAP = 12;
    private static final int CONTROL_WIDTH = 108;
    private static final int CONTROL_HEIGHT = 22;
    private static final int CONTENT_TOP = 76;
    private static final int ADVANCED_GAP = 28;
    private static final int FOOTER_HEIGHT = 40;

    private static final int[] BACKGROUND_LIMITS = {15, 30, 45, 60, 90, 120};
    private static final int[] MINIMIZED_LIMITS = {5, 10, 15, 30};

    private final Screen parent;
    private final List<Row> generalRows = new ArrayList<>();
    private final List<Row> advancedRows = new ArrayList<>();

    public PerformanceVideoSettingsScreen(Screen parent) {
        super(Text.literal("Performance"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        generalRows.clear();
        advancedRows.clear();

        PerformancePreferences prefs = PerformanceManagerClient.preferences();

        generalRows.add(Row.toggle(
                "Reduce FPS in Background",
                "Use less GPU power when Minecraft is not the active window.",
                prefs.backgroundFpsPolicy(),
                value -> update(prefs.withBackgroundFpsPolicy(value))
        ));
        generalRows.add(Row.value(
                "Background FPS",
                "Frame-rate limit while Minecraft is running in the background.",
                prefs.unfocusedFpsLimit() + " FPS",
                () -> update(prefs.withUnfocusedFpsLimit(
                        nextValue(BACKGROUND_LIMITS, prefs.unfocusedFpsLimit())
                ))
        ));
        generalRows.add(Row.value(
                "Minimized FPS",
                "Frame-rate limit while the game window is minimized.",
                prefs.minimizedFpsLimit() + " FPS",
                () -> update(prefs.withMinimizedFpsLimit(
                        nextValue(MINIMIZED_LIMITS, prefs.minimizedFpsLimit())
                ))
        ));

        advancedRows.add(Row.toggle(
                "Skip Unseen Objects",
                "Stop drawing entities and special blocks when they are fully hidden.",
                prefs.hiddenObjectSkipping(),
                value -> update(prefs.withHiddenObjectSkipping(value))
        ));
        advancedRows.add(Row.toggle(
                "Faster World Rendering",
                "Use the optimized world rendering path.",
                prefs.renderingOptimizations(),
                value -> update(prefs.withRenderingOptimizations(value))
        ));
        advancedRows.add(Row.toggle(
                "Lower Memory Usage",
                "Reuse compatible rendering data to reduce memory pressure.",
                prefs.memoryOptimizations(),
                value -> update(prefs.withMemoryOptimizations(value))
        ));

        layoutRows();
        addFooter();
    }

    private void layoutRows() {
        int width = contentWidth();
        int left = contentLeft();
        int columns = width >= 560 ? 2 : 1;
        int columnWidth = columns == 2 ? (width - COLUMN_GAP) / 2 : width;

        int generalRowsTall = (generalRows.size() + columns - 1) / columns;
        int advancedTop = CONTENT_TOP + generalRowsTall * (ROW_HEIGHT + ROW_GAP) + ADVANCED_GAP;

        layoutGroup(generalRows, left, CONTENT_TOP, columns, columnWidth);
        layoutGroup(advancedRows, left, advancedTop, columns, columnWidth);
    }

    private void layoutGroup(List<Row> rows, int left, int top, int columns, int columnWidth) {
        for (int i = 0; i < rows.size(); i++) {
            int column = columns == 2 ? i % 2 : 0;
            int rowIndex = columns == 2 ? i / 2 : i;
            int x = left + column * (columnWidth + COLUMN_GAP);
            int y = top + rowIndex * (ROW_HEIGHT + ROW_GAP);
            Row row = rows.get(i);
            row.x = x;
            row.y = y;
            row.width = columnWidth;

            int controlWidth = Math.min(CONTROL_WIDTH, Math.max(82, columnWidth / 3));
            this.addDrawableChild(new PerformanceSettingsControlWidget(
                    x + columnWidth - controlWidth - 8,
                    y + (ROW_HEIGHT - CONTROL_HEIGHT) / 2,
                    controlWidth,
                    CONTROL_HEIGHT,
                    Text.literal(row.controlLabel),
                    true,
                    row.action
            ));
        }
    }

    private void addFooter() {
        int y = height - 30;
        int left = contentLeft();

        this.addDrawableChild(new PerformanceSettingsControlWidget(
                left,
                y,
                92,
                22,
                Text.literal("Reset"),
                true,
                this::resetDefaults
        ));
        this.addDrawableChild(new PerformanceSettingsControlWidget(
                width / 2 - 46,
                y,
                92,
                22,
                Text.literal("Back"),
                true,
                this::close
        ));
        this.addDrawableChild(new PerformanceSettingsControlWidget(
                left + contentWidth() - 92,
                y,
                92,
                22,
                Text.literal("Done"),
                true,
                this::close
        ));
    }

    private void resetDefaults() {
        PerformanceManagerClient.updatePreferences(PerformancePreferences.defaults());
        refresh();
    }

    private void update(PerformancePreferences updated) {
        PerformanceManagerClient.updatePreferences(updated);
        refresh();
    }

    private void refresh() {
        if (client != null) {
            client.setScreen(new PerformanceVideoSettingsScreen(parent));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);
        context.fill(0, 0, width, 34, TOP_BAR);
        context.fill(0, height - FOOTER_HEIGHT, width, height, TOP_BAR);

        int left = contentLeft();
        context.drawTextWithShadow(textRenderer, Text.literal("SETTINGS"), left, 15, TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("VIDEO"), left, 46, TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal("PERFORMANCE"), left + 42, 46, TEXT_PRIMARY);
        context.fill(left + 42, 60, left + 118, 62, ACCENT);

        drawGroup(context, generalRows, mouseX, mouseY);

        int advancedY = advancedRows.isEmpty() ? CONTENT_TOP : advancedRows.get(0).y - 15;
        context.drawTextWithShadow(textRenderer, Text.literal("ADVANCED"), left, advancedY, TEXT_SECONDARY);
        drawGroup(context, advancedRows, mouseX, mouseY);

        String status = userFacingStatus();
        if (!status.isBlank()) {
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(status),
                    left,
                    Math.min(height - 48, advancedRows.get(advancedRows.size() - 1).y + ROW_HEIGHT + 10),
                    0xFFFF9A9A
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawGroup(DrawContext context, List<Row> rows, int mouseX, int mouseY) {
        for (Row row : rows) {
            boolean hovered = mouseX >= row.x && mouseX < row.x + row.width
                    && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
            context.fill(row.x, row.y, row.x + row.width, row.y + ROW_HEIGHT, ROW_BORDER);
            context.fill(
                    row.x + 1,
                    row.y + 1,
                    row.x + row.width - 1,
                    row.y + ROW_HEIGHT - 1,
                    hovered ? ROW_HOVER : ROW_FILL
            );

            int textMax = Math.max(60, row.width - CONTROL_WIDTH - 28);
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(textRenderer.trimToWidth(row.title, textMax)),
                    row.x + 10,
                    row.y + 9,
                    TEXT_PRIMARY
            );
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(textRenderer.trimToWidth(row.description, textMax)),
                    row.x + 10,
                    row.y + 25,
                    TEXT_MUTED
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

    private int contentWidth() {
        return Math.min(MAX_WIDTH, Math.max(280, width - SIDE_MARGIN * 2));
    }

    private int contentLeft() {
        return (width - contentWidth()) / 2;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    private static final class Row {
        private final String title;
        private final String description;
        private final String controlLabel;
        private final Runnable action;
        private int x;
        private int y;
        private int width;

        private Row(String title, String description, String controlLabel, Runnable action) {
            this.title = title;
            this.description = description;
            this.controlLabel = controlLabel;
            this.action = action;
        }

        static Row toggle(
                String title,
                String description,
                boolean enabled,
                java.util.function.Consumer<Boolean> setter
        ) {
            return new Row(title, description, enabled ? "ON" : "OFF", () -> setter.accept(!enabled));
        }

        static Row value(String title, String description, String value, Runnable action) {
            return new Row(title, description, value, action);
        }
    }
}

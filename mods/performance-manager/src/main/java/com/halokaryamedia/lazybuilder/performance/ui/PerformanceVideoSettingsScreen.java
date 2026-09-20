package com.halokaryamedia.lazybuilder.performance.ui;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.PerformancePreferences;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * User-facing performance options that extend Minecraft Video Settings.
 *
 * Common options are first. Compatibility/troubleshooting choices live in the Advanced
 * section at the bottom instead of a separate advanced page.
 */
public final class PerformanceVideoSettingsScreen extends Screen {
    private static final int MAX_CONTENT_WIDTH = 520;
    private static final int SCREEN_MARGIN = 18;
    private static final int TITLE_Y = 18;
    private static final int CONTENT_TOP = 48;
    private static final int ROW_HEIGHT = 28;
    private static final int ROW_GAP = 3;
    private static final int SECTION_GAP = 17;
    private static final int CONTROL_WIDTH = 98;
    private static final int DONE_WIDTH = 120;

    private static final int ROW_FILL = 0x88000000;
    private static final int ROW_BORDER = 0x447F8A98;
    private static final int TEXT_PRIMARY = 0xFFF3F6FA;
    private static final int TEXT_SECONDARY = 0xFFB0BAC7;
    private static final int TEXT_MUTED = 0xFF8B949E;

    private static final int[] BACKGROUND_LIMITS = {15, 30, 45, 60, 90, 120};
    private static final int[] MINIMIZED_LIMITS = {5, 10, 15, 30};

    private final Screen parent;

    public PerformanceVideoSettingsScreen(Screen parent) {
        super(Text.literal("Performance"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PerformancePreferences prefs = PerformanceManagerClient.preferences();
        int y = CONTENT_TOP + 17;

        y = addToggle(y, prefs.backgroundFpsPolicy(),
                enabled -> update(prefs.withBackgroundFpsPolicy(enabled)));

        y = addValue(y, prefs.unfocusedFpsLimit() + " FPS",
                () -> update(prefs.withUnfocusedFpsLimit(nextValue(BACKGROUND_LIMITS, prefs.unfocusedFpsLimit()))));

        y = addValue(y, prefs.minimizedFpsLimit() + " FPS",
                () -> update(prefs.withMinimizedFpsLimit(nextValue(MINIMIZED_LIMITS, prefs.minimizedFpsLimit()))));

        int advancedY = y + SECTION_GAP + 12;

        advancedY = addToggle(advancedY, prefs.hiddenObjectSkipping(),
                enabled -> update(prefs.withHiddenObjectSkipping(enabled)));

        advancedY = addToggle(advancedY, prefs.renderingOptimizations(),
                enabled -> update(prefs.withRenderingOptimizations(enabled)));

        addToggle(advancedY, prefs.memoryOptimizations(),
                enabled -> update(prefs.withMemoryOptimizations(enabled)));

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), button -> close())
                        .dimensions(this.width / 2 - DONE_WIDTH / 2, this.height - 32, DONE_WIDTH, 20)
                        .build()
        );
    }

    private int addToggle(int y, boolean enabled, Consumer<Boolean> setter) {
        int right = contentLeft() + contentWidth();
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal(enabled ? "ON" : "OFF"), button -> setter.accept(!enabled))
                        .dimensions(right - CONTROL_WIDTH - 4, y + 4, CONTROL_WIDTH, 20)
                        .build()
        );
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private int addValue(int y, String value, Runnable action) {
        int right = contentLeft() + contentWidth();
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal(value), button -> action.run())
                        .dimensions(right - CONTROL_WIDTH - 4, y + 4, CONTROL_WIDTH, 20)
                        .build()
        );
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private void update(PerformancePreferences updated) {
        PerformanceManagerClient.updatePreferences(updated);
        if (this.client != null) {
            this.client.setScreen(new PerformanceVideoSettingsScreen(this.parent));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int left = contentLeft();
        int right = left + contentWidth();
        PerformancePreferences prefs = PerformanceManagerClient.preferences();

        context.drawTextWithShadow(this.textRenderer, Text.literal("PERFORMANCE"), left, TITLE_Y, TEXT_PRIMARY);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Simple defaults for a smooth building session"),
                left,
                TITLE_Y + 14,
                TEXT_SECONDARY
        );

        int y = CONTENT_TOP;
        context.drawTextWithShadow(this.textRenderer, Text.literal("GENERAL"), left, y, TEXT_SECONDARY);
        y += 17;

        drawRow(context, left, right, y,
                "Reduce FPS in Background",
                "Use less GPU power when Minecraft is not the active window");
        y += ROW_HEIGHT + ROW_GAP;

        drawRow(context, left, right, y,
                "Background FPS",
                prefs.backgroundFpsPolicy()
                        ? "Frame-rate limit while Minecraft is in the background"
                        : "Used when background limiting is enabled");
        y += ROW_HEIGHT + ROW_GAP;

        drawRow(context, left, right, y,
                "Minimized FPS",
                "Frame-rate limit while the game window is minimized");
        y += ROW_HEIGHT + ROW_GAP;

        y += SECTION_GAP;
        context.drawTextWithShadow(this.textRenderer, Text.literal("ADVANCED"), left, y, TEXT_SECONDARY);
        y += 12;

        drawRow(context, left, right, y,
                "Skip Unseen Objects",
                "Stop drawing entities and special blocks when they are fully hidden");
        y += ROW_HEIGHT + ROW_GAP;

        drawRow(context, left, right, y,
                "Faster World Rendering",
                "Use the optimized world rendering path");
        y += ROW_HEIGHT + ROW_GAP;

        drawRow(context, left, right, y,
                "Lower Memory Usage",
                "Reuse compatible rendering data to reduce memory pressure");

        String status = userFacingStatus();
        if (!status.isBlank()) {
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(status),
                    left,
                    Math.min(this.height - 48, y + ROW_HEIGHT + 10),
                    0xFFFF9A9A
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawRow(DrawContext context, int left, int right, int y, String label, String description) {
        context.fill(left, y, right, y + ROW_HEIGHT, ROW_BORDER);
        context.fill(left + 1, y + 1, right - 1, y + ROW_HEIGHT - 1, ROW_FILL);
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), left + 8, y + 5, TEXT_PRIMARY);

        int maxWidth = Math.max(0, contentWidth() - CONTROL_WIDTH - 24);
        String clipped = this.textRenderer.trimToWidth(description, maxWidth);
        context.drawTextWithShadow(this.textRenderer, Text.literal(clipped), left + 8, y + 16, TEXT_MUTED);
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

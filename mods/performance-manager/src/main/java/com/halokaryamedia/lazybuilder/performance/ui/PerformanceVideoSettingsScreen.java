package com.halokaryamedia.lazybuilder.performance.ui;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.PerformancePreferences;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Performance options rendered with Minecraft's vanilla options layout.
 * Common controls appear first; advanced engine choices remain at the bottom.
 */
public final class PerformanceVideoSettingsScreen extends Screen {
    private static final int OPTION_WIDTH = 150;
    private static final int COLUMNS = 2;
    private static final int[] BACKGROUND_LIMITS = {15, 30, 45, 60, 90, 120};
    private static final int[] MINIMIZED_LIMITS = {5, 10, 15, 30};

    private final Screen parent;
    private final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this);

    public PerformanceVideoSettingsScreen(Screen parent) {
        super(Text.literal("Performance"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.layout.addHeader(this.title, this.textRenderer);

        PerformancePreferences prefs = PerformanceManagerClient.preferences();
        GridWidget grid = new GridWidget().setSpacing(4);
        GridWidget.Adder adder = grid.createAdder(COLUMNS);

        adder.add(toggleButton(
                "Reduce FPS in Background",
                prefs.backgroundFpsPolicy(),
                value -> update(prefs.withBackgroundFpsPolicy(value))
        ));
        adder.add(valueButton(
                "Background FPS",
                prefs.unfocusedFpsLimit() + " FPS",
                () -> update(prefs.withUnfocusedFpsLimit(
                        nextValue(BACKGROUND_LIMITS, prefs.unfocusedFpsLimit())
                ))
        ));
        adder.add(valueButton(
                "Minimized FPS",
                prefs.minimizedFpsLimit() + " FPS",
                () -> update(prefs.withMinimizedFpsLimit(
                        nextValue(MINIMIZED_LIMITS, prefs.minimizedFpsLimit())
                ))
        ));

        adder.add(
                new TextWidget(OPTION_WIDTH * 2 + 4, 20, Text.literal("Advanced"), this.textRenderer)
                        .alignCenter(),
                COLUMNS
        );

        adder.add(toggleButton(
                "Skip Unseen Objects",
                prefs.hiddenObjectSkipping(),
                value -> update(prefs.withHiddenObjectSkipping(value))
        ));
        adder.add(toggleButton(
                "Faster World Rendering",
                prefs.renderingOptimizations(),
                value -> update(prefs.withRenderingOptimizations(value))
        ));
        adder.add(toggleButton(
                "Lower Memory Usage",
                prefs.memoryOptimizations(),
                value -> update(prefs.withMemoryOptimizations(value))
        ));

        String status = userFacingStatus();
        if (!status.isBlank()) {
            adder.add(
                    new TextWidget(OPTION_WIDTH * 2 + 4, 20, Text.literal(status), this.textRenderer)
                            .alignCenter()
                            .setTextColor(0xFFFF9A9A),
                    COLUMNS
            );
        }

        this.layout.addBody(grid);
        this.layout.addFooter(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .width(200)
                        .build()
        );

        this.layout.forEachChild(this::addDrawableChild);
        this.refreshWidgetPositions();
    }

    private ButtonWidget toggleButton(String label, boolean enabled, Consumer<Boolean> setter) {
        return ButtonWidget.builder(
                        Text.literal(label + ": " + (enabled ? "ON" : "OFF")),
                        button -> setter.accept(!enabled)
                )
                .width(OPTION_WIDTH)
                .build();
    }

    private ButtonWidget valueButton(String label, String value, Runnable action) {
        return ButtonWidget.builder(
                        Text.literal(label + ": " + value),
                        button -> action.run()
                )
                .width(OPTION_WIDTH)
                .build();
    }

    private void update(PerformancePreferences updated) {
        PerformanceManagerClient.updatePreferences(updated);
        if (this.client != null) {
            this.client.setScreen(new PerformanceVideoSettingsScreen(this.parent));
        }
    }

    @Override
    protected void refreshWidgetPositions() {
        this.layout.refreshPositions();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
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

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}

package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Compact game-style slider used by the permanent LazyBuilder settings shell. */
final class LazyBuilderSettingsSliderWidget extends SliderWidget {
    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<String> formatter;
    private final DoubleConsumer onChange;

    LazyBuilderSettingsSliderWidget(
            int x,
            int y,
            int width,
            int height,
            double current,
            double min,
            double max,
            double step,
            DoubleFunction<String> formatter,
            DoubleConsumer onChange
    ) {
        super(x, y, width, height, Text.empty(), normalize(current, min, max));
        this.min = min;
        this.max = max;
        this.step = step;
        this.formatter = formatter;
        this.onChange = onChange;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Text.literal(formatter.apply(actualValue())));
    }

    @Override
    protected void applyValue() {
        double actual = actualValue();
        this.value = normalize(actual, min, max);
        updateMessage();
        onChange.accept(actual);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        boolean hot = isHovered() || isFocused();
        int x = getX();
        int y = getY();
        int right = x + getWidth();
        int bottom = y + getHeight();

        int border = hot ? LazyBuilderSettingsScreen.ACCENT : 0x66545C66;
        context.fill(x, y, right, bottom, border);
        context.fill(x + 1, y + 1, right - 1, bottom - 1, 0xD31A1F25);

        int trackLeft = x + 8;
        int trackRight = right - 8;
        int trackY = bottom - 5;
        context.fill(trackLeft, trackY, trackRight, trackY + 2, 0xFF3B424B);

        int knobX = trackLeft + (int) Math.round((trackRight - trackLeft - 4) * value);
        context.fill(trackLeft, trackY, knobX + 2, trackY + 2, LazyBuilderSettingsScreen.ACCENT);
        context.fill(knobX, trackY - 2, knobX + 4, trackY + 4, LazyBuilderSettingsScreen.ACCENT);

        Text visible = fitted(renderer, getWidth() - 12);
        context.drawCenteredTextWithShadow(
                renderer,
                visible,
                x + getWidth() / 2,
                y + 3,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );
    }

    private double actualValue() {
        double raw = min + (max - min) * value;
        if (step > 0.0) {
            raw = Math.round((raw - min) / step) * step + min;
        }
        return Math.max(min, Math.min(max, raw));
    }

    private Text fitted(net.minecraft.client.font.TextRenderer renderer, int available) {
        if (renderer.getWidth(getMessage()) <= available) return getMessage();
        String ellipsis = "…";
        int labelWidth = Math.max(0, available - renderer.getWidth(ellipsis));
        return Text.literal(renderer.trimToWidth(getMessage().getString(), labelWidth) + ellipsis);
    }

    private static double normalize(double value, double min, double max) {
        if (max <= min) return 0.0;
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }
}

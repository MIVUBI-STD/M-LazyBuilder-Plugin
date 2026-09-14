package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Small visual system shared by LazyBuilder Fabric screens. */
public final class LbUi {
    private LbUi() {}

    public static final int BACKGROUND = 0xFF0D1014;
    public static final int SURFACE_1 = 0xFF14181E;
    public static final int SURFACE_2 = 0xFF1A2028;
    public static final int SURFACE_3 = 0xFF222A34;
    public static final int BORDER = 0xFF313A46;
    public static final int BORDER_BRIGHT = 0xFF4A5869;

    public static final int TEXT_PRIMARY = 0xFFF3F6FA;
    public static final int TEXT_SECONDARY = 0xFFB0BAC7;
    public static final int TEXT_MUTED = 0xFF7F8A98;
    public static final int TEXT_DISABLED = 0xFF596370;

    public static final int ACCENT = 0xFF6C91FF;
    public static final int ACCENT_BRIGHT = 0xFF8AA8FF;
    public static final int ACCENT_FILL = 0xFF294579;
    public static final int ACCENT_HOVER = 0xFF355893;

    public static final int DANGER = 0xFFE36A6A;
    public static final int DANGER_BRIGHT = 0xFFFF8A8A;
    public static final int DANGER_FILL = 0xFF572E32;
    public static final int DANGER_HOVER = 0xFF71393F;
    public static final int WARNING = 0xFFF1C56A;
    public static final int SUCCESS = 0xFF6FD0A0;

    public static LbButtonWidget button(
            int x, int y, int width, int height, String label, LbButtonWidget.Style style, Runnable action) {
        return new LbButtonWidget(x, y, width, height, Text.literal(label), style, action);
    }

    public static void background(DrawContext context, int width, int height) {
        context.fill(0, 0, width, height, BACKGROUND);
    }

    public static void panel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, BORDER);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, SURFACE_1);
    }

    public static void elevatedPanel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, 0x99000000);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, BORDER);
        context.fill(x + 2, y + 2, x + width - 2, y + height - 2, SURFACE_2);
    }

    public static void divider(DrawContext context, int x1, int y, int x2) {
        context.fill(x1, y, x2, y + 1, BORDER);
    }

    public static void field(DrawContext context, TextFieldWidget field, boolean error) {
        int border = error ? DANGER : field.isFocused() ? ACCENT : BORDER;
        int x = field.getX() - 1;
        int y = field.getY() - 1;
        int right = field.getX() + field.getWidth() + 1;
        int bottom = field.getY() + field.getHeight() + 1;
        context.fill(x, y, right, bottom, border);
        context.fill(x + 1, y + 1, right - 1, bottom - 1, SURFACE_2);
    }

    public static void progress(DrawContext context, int x, int y, int width, int percent) {
        int p = Math.max(0, Math.min(100, percent));
        context.fill(x, y, x + width, y + 5, SURFACE_3);
        context.fill(x, y, x + (int) Math.round(width * (p / 100.0)), y + 5, ACCENT);
    }
}

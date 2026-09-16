package com.halokaryamedia.lazybuilder.terraformclient;

import net.minecraft.client.gui.DrawContext;

/**
 * Terraform-specific view tokens intentionally mirror the current LazyBuilder
 * Map Manager visual system without importing another Manager implementation.
 */
public final class TerraformUi {
    private TerraformUi() {}

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

    public static final int SUCCESS = 0xFF6FD0A0;
    public static final int WARNING = 0xFFF1C56A;

    public static void elevatedPanel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, 0x99000000);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, BORDER);
        context.fill(x + 2, y + 2, x + width - 2, y + height - 2, SURFACE_2);
    }

    public static void panel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, BORDER);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, SURFACE_1);
    }

    public static void divider(DrawContext context, int x1, int y, int x2) {
        context.fill(x1, y, x2, y + 1, BORDER);
    }
}

package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Presentation state, geometry, rendering, and hit testing for map right-click actions. */
final class MapContextMenuPanel {
    enum Action { NONE, TELEPORT, EXPORT_AREA, COPY_COORDINATES, DISMISS }

    private boolean open;
    private int blockX;
    private int blockZ;
    private int screenX;
    private int screenY;

    boolean isOpen() {
        return open;
    }

    int blockX() {
        return blockX;
    }

    int blockZ() {
        return blockZ;
    }

    void open(int blockX, int blockZ, int screenX, int screenY) {
        this.blockX = blockX;
        this.blockZ = blockZ;
        this.screenX = screenX;
        this.screenY = screenY;
        this.open = true;
    }

    void close() {
        open = false;
    }

    Action actionAt(
            int viewportLeft,
            int viewportTop,
            int viewportRight,
            int viewportBottom,
            double mouseX,
            double mouseY
    ) {
        if (!open) return Action.NONE;
        Rect menu = menuRect(viewportLeft, viewportTop, viewportRight, viewportBottom);
        if (teleportRect(menu).contains(mouseX, mouseY)) return Action.TELEPORT;
        if (exportRect(menu).contains(mouseX, mouseY)) return Action.EXPORT_AREA;
        if (copyRect(menu).contains(mouseX, mouseY)) return Action.COPY_COORDINATES;
        if (!menu.contains(mouseX, mouseY)) return Action.DISMISS;
        return Action.NONE;
    }

    void render(
            DrawContext context,
            TextRenderer textRenderer,
            int viewportLeft,
            int viewportTop,
            int viewportRight,
            int viewportBottom,
            boolean teleportEnabled,
            boolean teleportBusy,
            boolean exportEnabled,
            int mouseX,
            int mouseY
    ) {
        if (!open) return;

        Rect menu = menuRect(viewportLeft, viewportTop, viewportRight, viewportBottom);
        context.fill(menu.left - 2, menu.top - 2, menu.right + 2, menu.bottom + 2, 0x77000000);
        LbUi.elevatedPanel(context, menu.left, menu.top, menu.width(), menu.height());
        context.drawTextWithShadow(textRenderer, Text.literal("Map actions"), menu.left + 8, menu.top + 7, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(
                textRenderer,
                Text.literal("X " + blockX + "  Z " + blockZ),
                menu.left + 8,
                menu.top + 20,
                LbUi.TEXT_MUTED);

        String teleportLabel = teleportBusy ? "Teleporting…"
                : teleportEnabled ? "Teleport here" : "Teleport unavailable";
        renderRow(context, textRenderer, teleportRect(menu), teleportLabel, teleportEnabled, mouseX, mouseY);
        renderRow(context, textRenderer, exportRect(menu), "Export area", exportEnabled, mouseX, mouseY);
        renderRow(context, textRenderer, copyRect(menu), "Copy coordinates", true, mouseX, mouseY);
    }

    private Rect menuRect(int left, int top, int right, int bottom) {
        int menuWidth = 150;
        int menuHeight = 108;
        int x = Math.max(left + 4, Math.min(right - menuWidth - 5, screenX));
        int y = Math.max(top + 5, Math.min(bottom - menuHeight - 5, screenY));
        return new Rect(x, y, x + menuWidth, y + menuHeight);
    }

    private static Rect teleportRect(Rect menu) {
        return new Rect(menu.left + 5, menu.top + 34, menu.right - 5, menu.top + 56);
    }

    private static Rect exportRect(Rect menu) {
        return new Rect(menu.left + 5, menu.top + 57, menu.right - 5, menu.top + 79);
    }

    private static Rect copyRect(Rect menu) {
        return new Rect(menu.left + 5, menu.top + 80, menu.right - 5, menu.top + 101);
    }

    private static void renderRow(
            DrawContext context,
            TextRenderer textRenderer,
            Rect rect,
            String label,
            boolean active,
            int mouseX,
            int mouseY
    ) {
        if (active && rect.contains(mouseX, mouseY)) {
            context.fill(rect.left, rect.top, rect.right, rect.bottom, LbUi.SURFACE_3);
        }
        context.drawTextWithShadow(
                textRenderer,
                Text.literal(label),
                rect.left + 7,
                rect.top + 6,
                active ? LbUi.TEXT_PRIMARY : LbUi.TEXT_DISABLED);
    }

    private record Rect(int left, int top, int right, int bottom) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }
    }
}

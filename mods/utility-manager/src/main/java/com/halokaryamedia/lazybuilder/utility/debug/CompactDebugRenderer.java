package com.halokaryamedia.lazybuilder.utility.debug;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Minecraft-native, compact replacement presentation for the vanilla F3 text wall. */
public final class CompactDebugRenderer {
    private static final int MARGIN = 6;
    private static final int PADDING = 6;
    private static final int ROW_HEIGHT = 11;
    private static final int SECTION_GAP = 5;
    private static final int LABEL_COLUMN_WIDTH = 52;

    private static final int PANEL_BACKGROUND = 0x88000000;
    private static final int COORDINATE_BACKGROUND = 0xA0000000;
    private static final int COORDINATE_HOVER_BACKGROUND = 0xB8202020;
    private static final int TEXT_PRIMARY = 0xFFF2F2F2;
    private static final int TEXT_SECONDARY = 0xFFB8B8B8;
    private static final int TEXT_HEADING = 0xFFFFFFFF;
    private static final int TEXT_HINT = 0xFF9E9E9E;

    private CompactDebugRenderer() {
    }

    public static void render(MinecraftClient client, DrawContext context) {
        if (client == null || context == null) return;

        CompactDebugSnapshot snapshot = CompactDebugMetrics.capture(client);
        TextRenderer text = client.textRenderer;

        int coordinateWidth = coordinatePanelWidth(text, snapshot);
        int coordinateHeight = PADDING * 2 + ROW_HEIGHT * 3;
        CompactDebugInteraction.publishCoordinateBounds(MARGIN, MARGIN, coordinateWidth, coordinateHeight);
        drawCoordinatePanel(context, text, snapshot, client, MARGIN, MARGIN, coordinateWidth, coordinateHeight);

        int leftY = MARGIN + coordinateHeight + SECTION_GAP;
        int leftWidth = leftPanelWidth(text, snapshot);
        // CLIENT = heading + 4 rows, WORLD = heading + 3 rows: 9 rows total.
        int leftHeight = PADDING * 2 + ROW_HEIGHT * 9 + SECTION_GAP;
        drawLeftPanel(context, text, snapshot, MARGIN, leftY, leftWidth, leftHeight);

        int rightWidth = rightPanelWidth(text, snapshot);
        int rightHeight = PADDING * 2 + ROW_HEIGHT * (snapshot.serverMetricsAvailable() ? 4 : 3);
        int rightX = client.getWindow().getScaledWidth() - MARGIN - rightWidth;
        int rightY = MARGIN;

        // On narrow GUI widths, preserve readability by stacking SERVER below the left column.
        int occupiedLeftWidth = Math.max(coordinateWidth, leftWidth);
        if (shouldStackServer(client.getWindow().getScaledWidth(), occupiedLeftWidth, rightWidth)) {
            rightX = MARGIN;
            rightY = leftY + leftHeight + SECTION_GAP;
        }
        drawRightPanel(context, text, snapshot, rightX, rightY, rightWidth, rightHeight);
    }

    static boolean shouldStackServer(int scaledWidth, int occupiedLeftWidth, int rightWidth) {
        int rightX = scaledWidth - MARGIN - rightWidth;
        return rightX < MARGIN + occupiedLeftWidth + SECTION_GAP;
    }

    private static void drawCoordinatePanel(
            DrawContext context,
            TextRenderer text,
            CompactDebugSnapshot snapshot,
            MinecraftClient client,
            int x,
            int y,
            int width,
            int height
    ) {
        int background = CompactDebugInteraction.isCoordinateHovered(client)
                ? COORDINATE_HOVER_BACKGROUND
                : COORDINATE_BACKGROUND;
        context.fill(x, y, x + width, y + height, background);
        int tx = x + PADDING;
        int ty = y + PADDING;
        draw(context, text, "COORDINATE", tx, ty, TEXT_HEADING);
        draw(context, text, coordinateValue(snapshot), tx, ty + ROW_HEIGHT, TEXT_PRIMARY);
        draw(context, text, coordinateHint(client), tx, ty + ROW_HEIGHT * 2, TEXT_HINT);
    }

    private static void drawLeftPanel(
            DrawContext context,
            TextRenderer text,
            CompactDebugSnapshot snapshot,
            int x,
            int y,
            int width,
            int height
    ) {
        context.fill(x, y, x + width, y + height, PANEL_BACKGROUND);
        int tx = x + PADDING;
        int ty = y + PADDING;

        draw(context, text, "CLIENT", tx, ty, TEXT_HEADING);
        ty += ROW_HEIGHT;
        ty = drawRow(context, text, "FPS", Integer.toString(snapshot.fps()), tx, ty);
        ty = drawRow(context, text, "CPU", snapshot.clientCpu(), tx, ty);
        ty = drawRow(context, text, "GPU", snapshot.clientGpu(), tx, ty);
        ty = drawRow(context, text, "RAM", snapshot.clientRam(), tx, ty);

        ty += SECTION_GAP;
        draw(context, text, "WORLD", tx, ty, TEXT_HEADING);
        ty += ROW_HEIGHT;
        ty = drawRow(context, text, "Facing", snapshot.facing(), tx, ty);
        ty = drawRow(context, text, "Biome", snapshot.biome(), tx, ty);
        drawRow(context, text, "Time", snapshot.time(), tx, ty);
    }

    private static void drawRightPanel(
            DrawContext context,
            TextRenderer text,
            CompactDebugSnapshot snapshot,
            int x,
            int y,
            int width,
            int height
    ) {
        context.fill(x, y, x + width, y + height, PANEL_BACKGROUND);
        int tx = x + PADDING;
        int ty = y + PADDING;

        draw(context, text, "SERVER", tx, ty, TEXT_HEADING);
        ty += ROW_HEIGHT;
        ty = drawRow(context, text, "World", snapshot.serverWorld(), tx, ty);
        if (snapshot.serverMetricsAvailable()) {
            ty = drawRow(context, text, "CPU", snapshot.serverCpu(), tx, ty);
            drawRow(context, text, "RAM", snapshot.serverRam(), tx, ty);
        } else {
            drawRow(context, text, "Status", "Metrics unavailable", tx, ty);
        }
    }

    private static int drawRow(
            DrawContext context,
            TextRenderer text,
            String label,
            String value,
            int x,
            int y
    ) {
        draw(context, text, label, x, y, TEXT_SECONDARY);
        draw(context, text, value, x + LABEL_COLUMN_WIDTH, y, TEXT_PRIMARY);
        return y + ROW_HEIGHT;
    }

    private static void draw(DrawContext context, TextRenderer text, String value, int x, int y, int color) {
        context.drawTextWithShadow(text, value, x, y, color);
    }

    private static int coordinatePanelWidth(TextRenderer text, CompactDebugSnapshot snapshot) {
        int content = Math.max(text.getWidth("COORDINATE"), text.getWidth(coordinateValue(snapshot)));
        content = Math.max(content, text.getWidth("Hold Alt · Click to copy"));
        return Math.max(150, content + PADDING * 2);
    }

    private static int leftPanelWidth(TextRenderer text, CompactDebugSnapshot snapshot) {
        int valueWidth = Math.max(text.getWidth(snapshot.clientRam()), text.getWidth(snapshot.facing()));
        valueWidth = Math.max(valueWidth, text.getWidth(snapshot.biome()));
        valueWidth = Math.max(valueWidth, text.getWidth(snapshot.clientCpu()));
        valueWidth = Math.max(valueWidth, text.getWidth(snapshot.clientGpu()));
        return Math.max(170, PADDING * 2 + LABEL_COLUMN_WIDTH + valueWidth);
    }

    private static int rightPanelWidth(TextRenderer text, CompactDebugSnapshot snapshot) {
        int valueWidth = text.getWidth(snapshot.serverWorld());
        if (snapshot.serverMetricsAvailable()) {
            valueWidth = Math.max(valueWidth, text.getWidth(snapshot.serverRam()));
            valueWidth = Math.max(valueWidth, text.getWidth(snapshot.serverCpu()));
        } else {
            valueWidth = Math.max(valueWidth, text.getWidth("Metrics unavailable"));
        }
        return Math.max(160, PADDING * 2 + LABEL_COLUMN_WIDTH + valueWidth);
    }

    private static String coordinateValue(CompactDebugSnapshot snapshot) {
        return CompactDebugCoordinateText.display(snapshot.x(), snapshot.y(), snapshot.z());
    }

    private static String coordinateHint(MinecraftClient client) {
        if (!CompactDebugInteraction.interactionActive()) return "Hold Alt · Click to copy";
        return CompactDebugInteraction.isCoordinateHovered(client) ? "Click to copy" : "Alt interaction active";
    }
}

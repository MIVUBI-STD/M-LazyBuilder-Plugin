package com.halokaryamedia.lazybuilder.utility.debug;

import com.halokaryamedia.lazybuilder.utility.clipboard.UtilityClipboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * Owns the temporary pointer interaction used by Compact Debug.
 *
 * Rendering publishes the current Coordinate hit box. Holding Alt while the HUD
 * was just rendered unlocks the pointer; a left click inside that hit box copies
 * the complete XYZ triplet. Releasing Alt restores normal camera capture.
 */
public final class CompactDebugInteraction {
    private static final long RENDER_FRESHNESS_NANOS = 250_000_000L;

    private static volatile Bounds coordinateBounds = Bounds.empty();
    private static volatile long lastRenderedAtNanos;
    private static boolean interactionActive;
    private static boolean cursorWasLocked;

    private CompactDebugInteraction() {
    }

    public static void publishCoordinateBounds(int x, int y, int width, int height) {
        coordinateBounds = new Bounds(x, y, width, height);
        lastRenderedAtNanos = System.nanoTime();
    }

    public static boolean recentlyRendered() {
        long renderedAt = lastRenderedAtNanos;
        return renderedAt != 0L && System.nanoTime() - renderedAt <= RENDER_FRESHNESS_NANOS;
    }

    public static boolean interactionActive() {
        return interactionActive && recentlyRendered();
    }

    public static void begin(MinecraftClient client) {
        if (client == null || client.currentScreen != null || !recentlyRendered()) return;
        if (interactionActive) return;

        cursorWasLocked = client.mouse.isCursorLocked();
        interactionActive = true;
        if (cursorWasLocked) {
            client.mouse.unlockCursor();
        }
    }

    public static void end(MinecraftClient client) {
        if (!interactionActive) return;

        boolean shouldRelock = cursorWasLocked;
        interactionActive = false;
        cursorWasLocked = false;
        if (client != null && shouldRelock && client.currentScreen == null && !client.mouse.isCursorLocked()) {
            client.mouse.lockCursor();
        }
    }

    public static boolean isCoordinateHovered(MinecraftClient client) {
        if (!interactionActive() || client == null) return false;
        double scaledX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double scaledY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
        return coordinateBounds.contains(scaledX, scaledY);
    }

    public static boolean copyIfCoordinateHit(MinecraftClient client) {
        if (!isCoordinateHovered(client) || client.player == null) return false;

        BlockPos pos = client.player.getBlockPos();
        String coordinate = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        UtilityClipboard.copy(coordinate, "Coordinates copied");
        return true;
    }

    private record Bounds(int x, int y, int width, int height) {
        private static Bounds empty() {
            return new Bounds(0, 0, 0, 0);
        }

        private boolean contains(double px, double py) {
            return width > 0 && height > 0
                    && px >= x && px < x + width
                    && py >= y && py < y + height;
        }
    }
}

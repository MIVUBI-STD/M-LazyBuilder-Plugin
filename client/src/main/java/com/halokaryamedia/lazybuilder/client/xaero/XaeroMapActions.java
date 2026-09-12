package com.halokaryamedia.lazybuilder.client.xaero;

import com.halokaryamedia.lazybuilder.client.LazyBuilderClient;
import com.halokaryamedia.lazybuilder.client.LazyBuilderClientNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;

/**
 * Version-pinned Xaero fullscreen-map input adapter.
 *
 * <p>Xaero remains the renderer/navigation owner. LazyBuilder only samples the
 * already-rendered map camera and explicit player selections, then sends
 * server-authoritative intent through the existing map protocol.</p>
 *
 * <p>The adapter deliberately avoids linking against Xaero's shared ScreenBase
 * hierarchy. Runtime matching uses the exact fullscreen-map class name while
 * the mixin accessor owns the only version-pinned field bridge.</p>
 */
public final class XaeroMapActions {
    private static final String XAERO_MAP_CLASS = "xaero.map.gui.GuiMap";
    private static final String CATEGORY = "key.categories.lazybuilder";
    private static final int BUTTON_X = 4;
    private static final int TELEPORT_BUTTON_Y = 4;
    private static final int EXPORT_BUTTON_Y = 28;
    private static final int BUTTON_WIDTH = 112;
    private static final int BUTTON_HEIGHT = 20;

    // Keyboard shortcuts remain as a fallback if another mod rearranges or hides
    // the injected buttons. The buttons are the primary UX.
    private static final KeyBinding TELEPORT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.lazybuilder.teleport_here", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY));
    private static final KeyBinding EXPORT_CORNER = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.lazybuilder.export_area_corner", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));

    private static SelectionMode mode = SelectionMode.IDLE;
    private static Corner firstCorner;
    private static boolean wasMapOpen;

    private XaeroMapActions() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!isXaeroFullscreenMap(screen)) return;
            installControls(client, screen);
        });
        ClientTickEvents.END_CLIENT_TICK.register(XaeroMapActions::tickFallbackKeys);
    }

    private static void installControls(MinecraftClient client, Screen screen) {
        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Teleport Here"), button -> {
            mode = mode == SelectionMode.TELEPORT ? SelectionMode.IDLE : SelectionMode.TELEPORT;
            firstCorner = null;
            LazyBuilderClientNetworking.notifyPlayer(
                    mode == SelectionMode.TELEPORT
                            ? "LazyBuilder: click a location on the map to teleport."
                            : "LazyBuilder: teleport selection cancelled.");
        }).dimensions(BUTTON_X, TELEPORT_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Export Area"), button -> {
            if (mode == SelectionMode.EXPORT_FIRST || mode == SelectionMode.EXPORT_SECOND) {
                resetSelection();
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: Export Area selection cancelled.");
            } else {
                mode = SelectionMode.EXPORT_FIRST;
                firstCorner = null;
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: click the first Export Area corner.");
            }
        }).dimensions(BUTTON_X, EXPORT_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        ScreenMouseEvents.allowMouseClick(screen).register((target, event) -> {
            if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || mode == SelectionMode.IDLE) return true;
            if (insideControl(event.x(), event.y())) return true;

            try {
                Corner point = mouseWorldPoint(client, target, event.x(), event.y());
                switch (mode) {
                    case TELEPORT -> {
                        resetSelection();
                        LazyBuilderClient.maps().teleportCurrent(point.x(), point.z());
                    }
                    case EXPORT_FIRST -> {
                        firstCorner = point;
                        mode = SelectionMode.EXPORT_SECOND;
                        LazyBuilderClientNetworking.notifyPlayer(
                                "Export Area corner 1: " + point.x() + ", " + point.z()
                                        + ". Click the second corner.");
                    }
                    case EXPORT_SECOND -> {
                        Corner start = firstCorner;
                        resetSelection();
                        if (start == null) throw new IllegalStateException("Export Area first corner is missing");
                        String artifact = "area-" + Instant.now().getEpochSecond();
                        LazyBuilderClient.maps().exportAreaCurrent(
                                start.x(), start.z(), point.x(), point.z(), "JAVA_1_21_4", artifact);
                    }
                    case IDLE -> { return true; }
                }
                // Consume armed selection clicks so Xaero does not also interpret
                // them as map navigation/drag input.
                return false;
            } catch (RuntimeException exception) {
                resetSelection();
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: " + exception.getMessage());
                return false;
            }
        });
    }

    private static void tickFallbackKeys(MinecraftClient client) {
        Screen screen = client.currentScreen;
        boolean mapOpen = isXaeroFullscreenMap(screen);
        if (!mapOpen) {
            if (wasMapOpen) resetSelection();
            wasMapOpen = false;
            return;
        }
        wasMapOpen = true;

        while (TELEPORT.wasPressed()) {
            try {
                Corner point = mouseWorldPointFromCursor(client, screen);
                resetSelection();
                LazyBuilderClient.maps().teleportCurrent(point.x(), point.z());
            } catch (RuntimeException exception) {
                resetSelection();
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: " + exception.getMessage());
            }
        }

        while (EXPORT_CORNER.wasPressed()) {
            try {
                Corner point = mouseWorldPointFromCursor(client, screen);
                if (firstCorner == null) {
                    firstCorner = point;
                    mode = SelectionMode.EXPORT_SECOND;
                    LazyBuilderClientNetworking.notifyPlayer(
                            "Export Area corner 1: " + point.x() + ", " + point.z() + ". Press O again for corner 2.");
                } else {
                    Corner start = firstCorner;
                    resetSelection();
                    String artifact = "area-" + Instant.now().getEpochSecond();
                    LazyBuilderClient.maps().exportAreaCurrent(
                            start.x(), start.z(), point.x(), point.z(), "JAVA_1_21_4", artifact);
                }
            } catch (RuntimeException exception) {
                resetSelection();
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: " + exception.getMessage());
            }
        }
    }

    static boolean isXaeroFullscreenMap(Screen screen) {
        return screen != null && XAERO_MAP_CLASS.equals(screen.getClass().getName());
    }

    private static boolean insideControl(double x, double y) {
        return inside(x, y, BUTTON_X, TELEPORT_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                || inside(x, y, BUTTON_X, EXPORT_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static Corner mouseWorldPointFromCursor(MinecraftClient client, Object map) {
        var window = client.getWindow();
        double guiMouseX = client.mouse.getX() * window.getScaledWidth() / window.getWidth();
        double guiMouseY = client.mouse.getY() * window.getScaledHeight() / window.getHeight();
        return mouseWorldPoint(client, map, guiMouseX, guiMouseY);
    }

    static Corner mouseWorldPoint(MinecraftClient client, Object map, double guiMouseX, double guiMouseY) {
        if (!(map instanceof XaeroMapAccessor accessor)) {
            throw new IllegalStateException("Xaero map accessor is unavailable for this version");
        }
        double mapScale = accessor.lazybuilder$getScale();
        if (!Double.isFinite(mapScale) || mapScale <= 0.0) {
            throw new IllegalStateException("Xaero map scale is unavailable");
        }

        var window = client.getWindow();
        // Matches Xaero's fullscreen camera transform. Screen mouse events are
        // already expressed in GUI-scaled coordinates.
        double guiScale = (double) window.getWidth() / window.getScaledWidth();
        double effectiveScale = mapScale / guiScale;
        if (!Double.isFinite(effectiveScale) || effectiveScale <= 0.0) {
            throw new IllegalStateException("Xaero map transform is unavailable");
        }

        double worldX = (guiMouseX - window.getScaledWidth() / 2.0) / effectiveScale
                + accessor.lazybuilder$getCameraX();
        double worldZ = (guiMouseY - window.getScaledHeight() / 2.0) / effectiveScale
                + accessor.lazybuilder$getCameraZ();
        return new Corner(floorToInt(worldX), floorToInt(worldZ));
    }

    private static void resetSelection() {
        mode = SelectionMode.IDLE;
        firstCorner = null;
    }

    private static int floorToInt(double value) {
        if (!Double.isFinite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Selected map coordinate is outside supported range");
        }
        return (int) Math.floor(value);
    }

    private enum SelectionMode { IDLE, TELEPORT, EXPORT_FIRST, EXPORT_SECOND }
    record Corner(int x, int z) {}
}

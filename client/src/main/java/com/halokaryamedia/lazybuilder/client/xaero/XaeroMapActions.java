package com.halokaryamedia.lazybuilder.client.xaero;

import com.halokaryamedia.lazybuilder.client.LazyBuilderClient;
import com.halokaryamedia.lazybuilder.client.LazyBuilderClientNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import xaero.map.gui.GuiMap;

import java.time.Instant;

/**
 * Version-pinned Xaero fullscreen-map input adapter.
 *
 * <p>Xaero remains the renderer/navigation owner. LazyBuilder only samples the
 * already-rendered map camera and mouse location when an explicit key is pressed,
 * then sends server-authoritative intent through the existing map protocol.</p>
 */
public final class XaeroMapActions {
    private static final String CATEGORY = "key.categories.lazybuilder";
    private static final KeyBinding TELEPORT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.lazybuilder.teleport_here", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY));
    private static final KeyBinding EXPORT_CORNER = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.lazybuilder.export_area_corner", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));

    private static Corner firstCorner;
    private static boolean wasMapOpen;

    private XaeroMapActions() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(XaeroMapActions::tick);
    }

    private static void tick(MinecraftClient client) {
        boolean mapOpen = client.currentScreen instanceof GuiMap;
        if (!mapOpen) {
            if (wasMapOpen) firstCorner = null;
            wasMapOpen = false;
            return;
        }
        wasMapOpen = true;

        while (TELEPORT.wasPressed()) {
            try {
                Corner point = mouseWorldPoint(client, (GuiMap) client.currentScreen);
                LazyBuilderClient.maps().teleportCurrent(point.x(), point.z());
            } catch (RuntimeException exception) {
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: " + exception.getMessage());
            }
        }

        while (EXPORT_CORNER.wasPressed()) {
            try {
                Corner point = mouseWorldPoint(client, (GuiMap) client.currentScreen);
                if (firstCorner == null) {
                    firstCorner = point;
                    LazyBuilderClientNetworking.notifyPlayer(
                            "Export Area corner 1: " + point.x() + ", " + point.z() + ". Press O again for corner 2.");
                } else {
                    Corner start = firstCorner;
                    firstCorner = null;
                    String artifact = "area-" + Instant.now().getEpochSecond();
                    LazyBuilderClient.maps().exportAreaCurrent(
                            start.x(), start.z(), point.x(), point.z(), "JAVA_1_21_4", artifact);
                }
            } catch (RuntimeException exception) {
                firstCorner = null;
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: " + exception.getMessage());
            }
        }
    }

    static Corner mouseWorldPoint(MinecraftClient client, GuiMap map) {
        XaeroMapAccessor accessor = (XaeroMapAccessor) map;
        double mapScale = accessor.lazybuilder$getScale();
        if (!Double.isFinite(mapScale) || mapScale <= 0.0) {
            throw new IllegalStateException("Xaero map scale is unavailable");
        }

        var window = client.getWindow();
        double guiMouseX = client.mouse.getX() * window.getScaledWidth() / window.getWidth();
        double guiMouseY = client.mouse.getY() * window.getScaledHeight() / window.getHeight();

        // Matches Xaero's fullscreen camera transform. Window scaling is included
        // because mouse coordinates arrive in physical window pixels while the map
        // screen is expressed in GUI-scaled coordinates.
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

    private static int floorToInt(double value) {
        if (!Double.isFinite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Selected map coordinate is outside supported range");
        }
        return (int) Math.floor(value);
    }

    record Corner(int x, int z) {}
}

package com.halokaryamedia.lazybuilder.performance.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Adds LazyBuilder performance controls to the normal Minecraft video-settings flow. */
public final class VideoSettingsIntegration {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;

    private VideoSettingsIntegration() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof VideoOptionsScreen)) return;

            Screens.getButtons(screen).add(
                    ButtonWidget.builder(
                                    Text.literal("Performance..."),
                                    button -> client.setScreen(new PerformanceVideoSettingsScreen(screen))
                            )
                            .dimensions(
                                    width / 2 - BUTTON_WIDTH / 2,
                                    Math.max(24, height - 54),
                                    BUTTON_WIDTH,
                                    BUTTON_HEIGHT
                            )
                            .build()
            );
        });
    }
}

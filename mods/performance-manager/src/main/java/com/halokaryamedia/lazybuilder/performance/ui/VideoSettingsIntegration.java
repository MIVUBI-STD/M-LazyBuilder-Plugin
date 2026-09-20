package com.halokaryamedia.lazybuilder.performance.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.List;

/** Adds LazyBuilder performance controls to the normal Minecraft video-settings flow. */
public final class VideoSettingsIntegration {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    private VideoSettingsIntegration() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof VideoOptionsScreen)) return;

            List<ClickableWidget> widgets = Screens.getButtons(screen);
            ButtonWidget done = findButton(widgets, "gui.done");

            int x;
            int y;
            if (done != null) {
                int candidateLeft = done.getX() - BUTTON_WIDTH - GAP;
                if (candidateLeft >= GAP) {
                    x = candidateLeft;
                    y = done.getY();
                } else {
                    x = Math.max(GAP, width / 2 - BUTTON_WIDTH / 2);
                    y = Math.max(GAP, done.getY() - BUTTON_HEIGHT - GAP);
                }
            } else {
                // Fail-soft fallback for unexpected vanilla layout changes.
                x = Math.max(GAP, width / 2 - BUTTON_WIDTH / 2);
                y = Math.max(GAP, height - 54);
            }

            widgets.add(
                    ButtonWidget.builder(
                                    Text.literal("Performance..."),
                                    button -> client.setScreen(new PerformanceVideoSettingsScreen(screen))
                            )
                            .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                            .build()
            );
        });
    }

    private static ButtonWidget findButton(List<ClickableWidget> widgets, String translationKey) {
        for (ClickableWidget widget : widgets) {
            if (!(widget instanceof ButtonWidget button)) continue;
            if (button.getMessage().getContent() instanceof TranslatableTextContent content
                    && translationKey.equals(content.getKey())) {
                return button;
            }
        }
        return null;
    }
}

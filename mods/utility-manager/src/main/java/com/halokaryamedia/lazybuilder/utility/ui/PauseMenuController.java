package com.halokaryamedia.lazybuilder.utility.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Reduces the in-game pause menu to the decisions builders actually need.
 *
 * Existing vanilla Resume/Quit buttons are retained so their original callbacks and
 * singleplayer/multiplayer semantics remain authoritative.
 */
public final class PauseMenuController {
    private static final int BUTTON_WIDTH = 204;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    private PauseMenuController() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof GameMenuScreen)) return;

            List<ClickableWidget> buttons = Screens.getButtons(screen);
            if (buttons.isEmpty()) return;

            List<ClickableWidget> vanillaButtons = new ArrayList<>(buttons);
            ButtonWidget resume = find(vanillaButtons, "menu.returnToGame");
            ButtonWidget quit = findQuit(vanillaButtons);

            // Fail open if the supported vanilla anchors cannot be identified.
            if (resume == null || quit == null) return;

            buttons.clear();

            int x = width / 2 - BUTTON_WIDTH / 2;
            int firstY = Math.max(56, height / 2 - 52);

            position(resume, x, firstY);
            buttons.add(resume);

            buttons.add(ButtonWidget.builder(
                            Text.literal("Settings"),
                            button -> client.setScreen(new LazyBuilderSettingsScreen(screen)))
                    .dimensions(x, firstY + (BUTTON_HEIGHT + GAP), BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());

            position(quit, x, firstY + (BUTTON_HEIGHT + GAP) * 2);
            buttons.add(quit);
        });
    }

    private static ButtonWidget find(List<ClickableWidget> buttons, String key) {
        for (ClickableWidget widget : buttons) {
            if (widget instanceof ButtonWidget button && key.equals(translationKey(button))) {
                return button;
            }
        }
        return null;
    }

    private static ButtonWidget findQuit(List<ClickableWidget> buttons) {
        for (String key : List.of("menu.disconnect", "menu.returnToMenu", "menu.saveAndQuit")) {
            ButtonWidget button = find(buttons, key);
            if (button != null) return button;
        }
        return null;
    }

    private static String translationKey(ButtonWidget button) {
        if (button.getMessage().getContent() instanceof TranslatableTextContent content) {
            return content.getKey();
        }
        return "";
    }

    private static void position(ButtonWidget button, int x, int y) {
        button.setX(x);
        button.setY(y);
        button.setWidth(BUTTON_WIDTH);
    }
}

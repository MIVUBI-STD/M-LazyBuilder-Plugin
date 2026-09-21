package com.halokaryamedia.lazybuilder.utility.screenshot;

import com.halokaryamedia.lazybuilder.utility.ui.LazyBuilderSettingsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Compatibility entry point for older screenshot-settings callers.
 *
 * Screenshot preferences now live in the unified LazyBuilder Interface settings.
 * Keeping this redirect avoids duplicate settings UI while preserving old call sites.
 */
public final class ScreenshotSettingsScreen extends Screen {
    private final Screen parent;

    public ScreenshotSettingsScreen(Screen parent) {
        super(Text.literal("Screenshot Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (client != null) {
            client.setScreen(new LazyBuilderSettingsScreen(
                    parent,
                    LazyBuilderSettingsScreen.Category.CAPTURE,
                    LazyBuilderSettingsScreen.VideoPage.QUALITY,
                    "Screenshot Quality"
            ));
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

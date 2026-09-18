package com.halokaryamedia.lazybuilder.utility.screenshot;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Small pre-capture settings surface for Utility Manager screenshot behavior. */
public final class ScreenshotSettingsScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 24;

    private final Screen parent;
    private ButtonWidget contextualNamesButton;

    public ScreenshotSettingsScreen(Screen parent) {
        super(Text.literal("Screenshot Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int firstY = Math.max(70, this.height / 2 - 20);

        this.contextualNamesButton = this.addDrawableChild(
                ButtonWidget.builder(this.contextualNamesLabel(), button -> this.toggleContextualNames())
                        .dimensions(centerX - BUTTON_WIDTH / 2, firstY, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .dimensions(centerX - BUTTON_WIDTH / 2, firstY + ROW_GAP, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                this.width / 2,
                34,
                0xFFFFFF
        );
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Configure screenshot behavior, then use the normal screenshot key to capture."),
                this.width / 2,
                50,
                0xA0A0A0
        );
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void toggleContextualNames() {
        boolean enabled = !UtilityManagerClient.preferences().contextualScreenshotNames();
        UtilityManagerClient.updatePreferences(
                UtilityManagerClient.preferences().withContextualScreenshotNames(enabled)
        );
        if (this.contextualNamesButton != null) {
            this.contextualNamesButton.setMessage(this.contextualNamesLabel());
        }
    }

    private Text contextualNamesLabel() {
        return Text.literal(
                "Contextual filenames: "
                        + (UtilityManagerClient.preferences().contextualScreenshotNames() ? "On" : "Off")
        );
    }
}

package com.halokaryamedia.lazybuilder.utility.ui;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.UtilityPreferences;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/** User-facing configuration for the Utility tool suite. */
public final class LazyBuilderUtilityToolsScreen extends Screen {
    private static final int MAX_CONTENT_WIDTH = 520;
    private static final int SCREEN_MARGIN = 18;
    private static final int TITLE_Y = 18;
    private static final int CONTENT_TOP = 50;
    private static final int ROW_HEIGHT = 28;
    private static final int ROW_GAP = 3;
    private static final int CONTROL_WIDTH = 82;
    private static final int DONE_WIDTH = 120;

    private static final int ROW_FILL = 0x88000000;
    private static final int ROW_BORDER = 0x447F8A98;
    private static final int TEXT_PRIMARY = 0xFFF3F6FA;
    private static final int TEXT_SECONDARY = 0xFFB0BAC7;
    private static final int TEXT_MUTED = 0xFF8B949E;

    private final Screen parent;

    public LazyBuilderUtilityToolsScreen(Screen parent) {
        super(Text.literal("Utilities"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = CONTENT_TOP;
        y = addToggle(y, preferences().instantCreativeSearch(),
                value -> update(preferences().withInstantCreativeSearch(value)));
        y = addToggle(y, preferences().compactDebugHud(),
                value -> update(preferences().withCompactDebugHud(value)));
        addToggle(y, preferences().contextualScreenshotNames(),
                value -> update(preferences().withContextualScreenshotNames(value)));

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .dimensions(this.width / 2 - DONE_WIDTH / 2, this.height - 32, DONE_WIDTH, 20)
                        .build()
        );
    }

    private int addToggle(int y, boolean enabled, Consumer<Boolean> setter) {
        int right = contentLeft() + contentWidth();
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal(enabled ? "ON" : "OFF"), button -> {
                            setter.accept(!enabled);
                            if (this.client != null) {
                                this.client.setScreen(new LazyBuilderUtilityToolsScreen(this.parent));
                            }
                        })
                        .dimensions(right - CONTROL_WIDTH - 4, y + 4, CONTROL_WIDTH, 20)
                        .build()
        );
        return y + ROW_HEIGHT + ROW_GAP;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int left = contentLeft();
        int right = left + contentWidth();

        context.drawTextWithShadow(this.textRenderer, Text.literal("UTILITIES"), left, TITLE_Y, TEXT_PRIMARY);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Enabled by default. Change only what you need."),
                left,
                TITLE_Y + 14,
                TEXT_SECONDARY
        );

        int y = CONTENT_TOP;
        drawRow(context, left, right, y, "Instant Creative Search", "Type immediately to search Creative inventory");
        y += ROW_HEIGHT + ROW_GAP;
        drawRow(context, left, right, y, "Compact Debug HUD", "Builder-focused replacement for the full F3 information wall");
        y += ROW_HEIGHT + ROW_GAP;
        drawRow(context, left, right, y, "Contextual Screenshot Names", "Add world or server context to automatic screenshot names");

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawRow(DrawContext context, int left, int right, int y, String label, String description) {
        context.fill(left, y, right, y + ROW_HEIGHT, ROW_BORDER);
        context.fill(left + 1, y + 1, right - 1, y + ROW_HEIGHT - 1, ROW_FILL);
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), left + 8, y + 5, TEXT_PRIMARY);

        int maxWidth = Math.max(0, contentWidth() - CONTROL_WIDTH - 24);
        String clipped = this.textRenderer.trimToWidth(description, maxWidth);
        context.drawTextWithShadow(this.textRenderer, Text.literal(clipped), left + 8, y + 16, TEXT_MUTED);
    }

    private static UtilityPreferences preferences() {
        return UtilityManagerClient.preferences();
    }

    private static void update(UtilityPreferences preferences) {
        UtilityManagerClient.updatePreferences(preferences);
    }

    private int contentWidth() {
        return Math.min(MAX_CONTENT_WIDTH, Math.max(280, this.width - SCREEN_MARGIN * 2));
    }

    private int contentLeft() {
        return (this.width - contentWidth()) / 2;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}

package com.halokaryamedia.lazybuilder.utility.ui;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.UtilityPreferences;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Familiar game-style settings surface for LazyBuilder.
 *
 * The screen exposes user concepts only: horizontal categories, full-width option rows,
 * labels on the left and controls on the right. Internal mod ownership and automatic
 * engine policy intentionally stay out of this surface.
 */
public final class LazyBuilderSettingsScreen extends Screen {
    public enum Category {
        VIDEO("Video"),
        CONTROLS("Controls"),
        INTERFACE("Interface");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        Text text() {
            return Text.literal(this.label);
        }
    }

    private static final int MAX_CONTENT_WIDTH = 520;
    private static final int SCREEN_MARGIN = 18;
    private static final int TITLE_Y = 18;
    private static final int TAB_Y = 38;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 4;
    private static final int CONTENT_TOP = 76;
    private static final int SECTION_GAP = 18;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 2;
    private static final int CONTROL_WIDTH = 92;
    private static final int DONE_WIDTH = 120;

    private static final int ROW_FILL = 0x88000000;
    private static final int ROW_BORDER = 0x447F8A98;
    private static final int DIVIDER = 0x66FFFFFF;
    private static final int TEXT_PRIMARY = 0xFFF3F6FA;
    private static final int TEXT_SECONDARY = 0xFFB0BAC7;
    private static final int TEXT_MUTED = 0xFF8B949E;

    private final Screen parent;
    private final Category category;

    public LazyBuilderSettingsScreen(Screen parent) {
        this(parent, Category.VIDEO);
    }

    public LazyBuilderSettingsScreen(Screen parent, Category category) {
        super(Text.literal("Settings"));
        this.parent = parent;
        this.category = category;
    }

    @Override
    protected void init() {
        int left = contentLeft();
        int width = contentWidth();
        int tabWidth = Math.max(62, (width - TAB_GAP * (Category.values().length - 1)) / Category.values().length);

        int tabX = left;
        for (Category value : Category.values()) {
            int actualWidth = value == Category.values()[Category.values().length - 1]
                    ? left + width - tabX
                    : tabWidth;
            ButtonWidget tab = ButtonWidget.builder(value.text(), button -> this.openCategory(value))
                    .dimensions(tabX, TAB_Y, actualWidth, TAB_HEIGHT)
                    .build();
            tab.active = value != this.category;
            this.addDrawableChild(tab);
            tabX += actualWidth + TAB_GAP;
        }

        int rowY = CONTENT_TOP + SECTION_GAP;
        switch (this.category) {
            case VIDEO -> this.addNavigationRow(rowY, "Open", () -> {
                if (this.client != null) {
                    this.client.setScreen(new VideoOptionsScreen(this, this.client.options));
                }
            });
            case CONTROLS -> this.addNavigationRow(rowY, "Open", () -> {
                if (this.client != null) {
                    this.client.setScreen(new ControlsOptionsScreen(this, this.client.options));
                }
            });
            case INTERFACE -> {
                int next = this.addToggleRow(
                        rowY,
                        preferences().compactDebugHud(),
                        value -> update(preferences().withCompactDebugHud(value))
                );
                this.addToggleRow(
                        next,
                        preferences().contextualScreenshotNames(),
                        value -> update(preferences().withContextualScreenshotNames(value))
                );
            }
        }

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .dimensions(
                                this.width / 2 - DONE_WIDTH / 2,
                                this.height - 32,
                                DONE_WIDTH,
                                20
                        )
                        .build()
        );
    }

    private int addToggleRow(int y, boolean enabled, Consumer<Boolean> setter) {
        int right = contentLeft() + contentWidth();
        this.addDrawableChild(
                ButtonWidget.builder(
                                Text.literal(enabled ? "ON" : "OFF"),
                                button -> {
                                    setter.accept(!enabled);
                                    this.refreshCategory();
                                })
                        .dimensions(right - CONTROL_WIDTH - 4, y + 2, CONTROL_WIDTH, ROW_HEIGHT - 4)
                        .build()
        );
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private void addNavigationRow(int y, String label, Runnable action) {
        int right = contentLeft() + contentWidth();
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal(label + " >"), button -> action.run())
                        .dimensions(right - CONTROL_WIDTH - 4, y + 2, CONTROL_WIDTH, ROW_HEIGHT - 4)
                        .build()
        );
    }

    private void openCategory(Category next) {
        if (this.client != null && next != this.category) {
            this.client.setScreen(new LazyBuilderSettingsScreen(this.parent, next));
        }
    }

    private void refreshCategory() {
        if (this.client != null) {
            this.client.setScreen(new LazyBuilderSettingsScreen(this.parent, this.category));
        }
    }

    private static UtilityPreferences preferences() {
        return UtilityManagerClient.preferences();
    }

    private static void update(UtilityPreferences preferences) {
        UtilityManagerClient.updatePreferences(preferences);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int left = contentLeft();
        int right = left + contentWidth();

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("SETTINGS"),
                left,
                TITLE_Y,
                TEXT_PRIMARY
        );

        context.fill(left, TAB_Y + TAB_HEIGHT + 8, right, TAB_Y + TAB_HEIGHT + 9, DIVIDER);

        context.drawTextWithShadow(
                this.textRenderer,
                sectionTitle(),
                left,
                CONTENT_TOP,
                TEXT_SECONDARY
        );

        renderRows(context, left, right, CONTENT_TOP + SECTION_GAP);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderRows(DrawContext context, int left, int right, int firstY) {
        switch (this.category) {
            case VIDEO -> {
                drawRow(context, left, right, firstY, "Video Settings", "Display, graphics and render distance");
            }
            case CONTROLS -> {
                drawRow(context, left, right, firstY, "Controls", "Keyboard, mouse and key bindings");
            }
            case INTERFACE -> {
                drawRow(context, left, right, firstY, "Compact Debug HUD", "Keep useful build information visible without the full F3 wall");
                drawRow(context, left, right, firstY + ROW_HEIGHT + ROW_GAP,
                        "Contextual Screenshot Names", "Add world or server context to automatic screenshot names");
            }
        }
    }

    private void drawRow(
            DrawContext context,
            int left,
            int right,
            int y,
            String label,
            String description
    ) {
        context.fill(left, y, right, y + ROW_HEIGHT, ROW_BORDER);
        context.fill(left + 1, y + 1, right - 1, y + ROW_HEIGHT - 1, ROW_FILL);

        int textY = y + 4;
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), left + 8, textY, TEXT_PRIMARY);

        int descriptionX = left + 8;
        int descriptionY = textY + 10;
        int maxDescriptionWidth = Math.max(0, contentWidth() - CONTROL_WIDTH - 24);
        String clipped = this.textRenderer.trimToWidth(description, maxDescriptionWidth);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(clipped),
                descriptionX,
                descriptionY,
                TEXT_MUTED
        );
    }

    private Text sectionTitle() {
        return switch (this.category) {
            case VIDEO -> Text.literal("VIDEO");
            case CONTROLS -> Text.literal("CONTROLS");
            case INTERFACE -> Text.literal("INTERFACE");
        };
    }

    private int contentWidth() {
        return Math.min(MAX_CONTENT_WIDTH, Math.max(280, this.width - SCREEN_MARGIN * 2));
    }

    private int contentLeft() {
        return (this.width - contentWidth()) / 2;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}

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
 * Familiar, low-density settings shell for the LazyBuilder client.
 *
 * This screen intentionally presents user concepts instead of internal mod/module names.
 * It only exposes choices backed by implemented behavior; deeper cross-module settings
 * are added here only after their ownership and UX have been audited.
 */
public final class LazyBuilderSettingsScreen extends Screen {
    public enum Category {
        GENERAL("General"),
        VIDEO("Video"),
        CONTROLS("Controls"),
        INTERFACE("Interface"),
        TOOLS("Tools"),
        PERFORMANCE("Performance"),
        ADVANCED("Advanced");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        Text text() {
            return Text.literal(this.label);
        }
    }

    private static final int PANEL_MAX_WIDTH = 460;
    private static final int PANEL_MARGIN = 18;
    private static final int SIDEBAR_WIDTH = 104;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 4;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int CONTROL_WIDTH = 112;

    private final Screen parent;
    private final Category category;

    public LazyBuilderSettingsScreen(Screen parent) {
        this(parent, Category.GENERAL);
    }

    public LazyBuilderSettingsScreen(Screen parent, Category category) {
        super(Text.literal("Settings"));
        this.parent = parent;
        this.category = category;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(280, this.width - PANEL_MARGIN * 2));
        int panelLeft = (this.width - panelWidth) / 2;
        int top = 48;

        int tabY = top;
        for (Category value : Category.values()) {
            ButtonWidget tab = ButtonWidget.builder(value.text(), button -> this.openCategory(value))
                    .dimensions(panelLeft, tabY, SIDEBAR_WIDTH, TAB_HEIGHT)
                    .build();
            tab.active = value != this.category;
            this.addDrawableChild(tab);
            tabY += TAB_HEIGHT + TAB_GAP;
        }

        int contentLeft = panelLeft + SIDEBAR_WIDTH + 16;
        int contentRight = panelLeft + panelWidth;
        int contentWidth = Math.max(140, contentRight - contentLeft);
        int rowY = top;

        switch (this.category) {
            case GENERAL -> this.addGeneralRows(contentLeft, contentWidth, rowY);
            case VIDEO -> this.addVideoRows(contentLeft, contentWidth, rowY);
            case CONTROLS -> this.addControlsRows(contentLeft, contentWidth, rowY);
            case INTERFACE -> this.addInterfaceRows(contentLeft, contentWidth, rowY);
            case TOOLS -> this.addToolsRows(contentLeft, contentWidth, rowY);
            case PERFORMANCE -> this.addPerformanceRows(contentLeft, contentWidth, rowY);
            case ADVANCED -> this.addAdvancedRows(contentLeft, contentWidth, rowY);
        }

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .dimensions(contentLeft, this.height - 32, Math.min(200, contentWidth), ROW_HEIGHT)
                        .build()
        );
    }

    private void addGeneralRows(int x, int width, int y) {
        int next = y;
        next = this.addToggleRow(x, width, next, "Borderless Window",
                preferences().borderlessWindow(),
                value -> update(preferences().withBorderlessWindow(value)));
        next = this.addToggleRow(x, width, next, "Reconnect Button",
                preferences().reconnectButton(),
                value -> update(preferences().withReconnectButton(value)));
        this.addToggleRow(x, width, next, "Contextual Screenshot Names",
                preferences().contextualScreenshotNames(),
                value -> update(preferences().withContextualScreenshotNames(value)));
    }

    private void addVideoRows(int x, int width, int y) {
        this.addWideButton(x, width, y, "Open Video Settings", () -> {
            if (this.client != null) {
                this.client.setScreen(new VideoOptionsScreen(this, this.client.options));
            }
        });
    }

    private void addControlsRows(int x, int width, int y) {
        this.addWideButton(x, width, y, "Open Controls", () -> {
            if (this.client != null) {
                this.client.setScreen(new ControlsOptionsScreen(this, this.client.options));
            }
        });
    }

    private void addInterfaceRows(int x, int width, int y) {
        int next = y;
        next = this.addToggleRow(x, width, next, "Compact Debug HUD",
                preferences().compactDebugHud(),
                value -> update(preferences().withCompactDebugHud(value)));
        next = this.addToggleRow(x, width, next, "Chat Timestamps",
                preferences().chatTimestamps(),
                value -> update(preferences().withChatTimestamps(value)));
        next = this.addToggleRow(x, width, next, "Extended Chat History",
                preferences().extendedChatHistory(),
                value -> update(preferences().withExtendedChatHistory(value)));
        this.addToggleRow(x, width, next, "Keep Chat Draft",
                preferences().keepChatDraft(),
                value -> update(preferences().withKeepChatDraft(value)));
    }

    private void addToolsRows(int x, int width, int y) {
        this.addToggleRow(x, width, y, "Instant Creative Search",
                preferences().instantCreativeSearch(),
                value -> update(preferences().withInstantCreativeSearch(value)));
    }

    private void addPerformanceRows(int x, int width, int y) {
        this.addWideButton(x, width, y, "Video & Frame Settings", () -> {
            if (this.client != null) {
                this.client.setScreen(new VideoOptionsScreen(this, this.client.options));
            }
        });
    }

    private void addAdvancedRows(int x, int width, int y) {
        int next = y;
        next = this.addToggleRow(x, width, next, "Chat Search",
                preferences().chatSearch(),
                value -> update(preferences().withChatSearch(value)));
        next = this.addToggleRow(x, width, next, "Hide Signing Indicator",
                preferences().hideChatSigningIndicators(),
                value -> update(preferences().withHideChatSigningIndicators(value)));
        next = this.addToggleRow(x, width, next, "Hide Report Button",
                preferences().hideChatReportButton(),
                value -> update(preferences().withHideChatReportButton(value)));
        this.addToggleRow(x, width, next, "Suppress Narrator",
                preferences().suppressNarrator(),
                value -> update(preferences().withSuppressNarrator(value)));
    }

    private int addToggleRow(
            int x,
            int width,
            int y,
            String label,
            boolean enabled,
            Consumer<Boolean> setter
    ) {
        int buttonWidth = Math.min(CONTROL_WIDTH, Math.max(72, width / 3));
        int buttonX = x + width - buttonWidth;

        this.addDrawableChild(
                ButtonWidget.builder(
                                Text.literal(enabled ? "On" : "Off"),
                                button -> {
                                    setter.accept(!enabled);
                                    this.refreshCategory();
                                })
                        .dimensions(buttonX, y, buttonWidth, ROW_HEIGHT)
                        .build()
        );

        return y + ROW_HEIGHT + ROW_GAP;
    }

    private void addWideButton(int x, int width, int y, String label, Runnable action) {
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal(label), button -> action.run())
                        .dimensions(x, y, Math.min(width, 220), ROW_HEIGHT)
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

        int panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(280, this.width - PANEL_MARGIN * 2));
        int panelLeft = (this.width - panelWidth) / 2;
        int contentLeft = panelLeft + SIDEBAR_WIDTH + 16;
        int contentRight = panelLeft + panelWidth;

        context.drawTextWithShadow(this.textRenderer, this.title, panelLeft, 24, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, this.category.text(), contentLeft, 34, 0xFFFFFF);
        context.fill(contentLeft, 44, contentRight, 45, 0x55FFFFFF);

        this.renderLabels(context, contentLeft, 48);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderLabels(DrawContext context, int x, int y) {
        switch (this.category) {
            case GENERAL -> {
                drawRowLabel(context, x, y, "Borderless Window");
                drawRowLabel(context, x, y + 24, "Reconnect Button");
                drawRowLabel(context, x, y + 48, "Contextual Screenshot Names");
            }
            case INTERFACE -> {
                drawRowLabel(context, x, y, "Compact Debug HUD");
                drawRowLabel(context, x, y + 24, "Chat Timestamps");
                drawRowLabel(context, x, y + 48, "Extended Chat History");
                drawRowLabel(context, x, y + 72, "Keep Chat Draft");
            }
            case TOOLS -> drawRowLabel(context, x, y, "Instant Creative Search");
            case PERFORMANCE -> context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal("Core rendering optimizations stay automatic."),
                    x,
                    y + 30,
                    0xA0A0A0
            );
            case ADVANCED -> {
                drawRowLabel(context, x, y, "Chat Search");
                drawRowLabel(context, x, y + 24, "Hide Signing Indicator");
                drawRowLabel(context, x, y + 48, "Hide Report Button");
                drawRowLabel(context, x, y + 72, "Suppress Narrator");
            }
            case VIDEO, CONTROLS -> {
                // Vanilla sub-screens own their established option rows.
            }
        }
    }

    private void drawRowLabel(DrawContext context, int x, int y, String label) {
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), x, y + 6, 0xE0E0E0);
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

package com.halokaryamedia.lazybuilder.utility.ui;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.UtilityPreferences;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Full game-style LazyBuilder settings surface inspired by modern PC game settings:
 * horizontal tabs, dense two-column rows, clear active state and a persistent footer.
 */
public final class LazyBuilderSettingsScreen extends Screen {
    public enum Category {
        VIDEO("Video"),
        CONTROLS("Controls"),
        INTERFACE("Interface"),
        TOOLS("Tools");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        Text text() {
            return Text.literal(label);
        }
    }

    static final int TEXT_PRIMARY = 0xFFF2F4F6;
    static final int TEXT_SECONDARY = 0xFFB8BEC6;
    static final int TEXT_MUTED = 0xFF858D97;
    static final int ACCENT = 0xFFF1D21A;

    private static final int BACKGROUND = 0xEE0B0E12;
    private static final int TOP_BAR = 0xF013171C;
    private static final int ROW_FILL = 0xC91A1F25;
    private static final int ROW_HOVER = 0xDD21272E;
    private static final int ROW_BORDER = 0x334E5660;

    private static final int MAX_WIDTH = 760;
    private static final int SIDE_MARGIN = 18;
    private static final int HEADER_HEIGHT = 34;
    private static final int TAB_HEIGHT = 24;
    private static final int TAB_TOP = 42;
    private static final int CONTENT_TOP = 82;
    private static final int ROW_HEIGHT = 46;
    private static final int ROW_GAP = 6;
    private static final int COLUMN_GAP = 12;
    private static final int CONTROL_WIDTH = 108;
    private static final int CONTROL_HEIGHT = 22;
    private static final int FOOTER_HEIGHT = 40;

    private final Screen parent;
    private final Category category;
    private final List<Row> rows = new ArrayList<>();

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
        rows.clear();
        addTabs();

        switch (category) {
            case VIDEO -> buildVideoRows();
            case CONTROLS -> buildControlsRows();
            case INTERFACE -> buildInterfaceRows();
            case TOOLS -> buildToolsRows();
        }

        layoutRows();
        addFooter();
    }

    private void addTabs() {
        int left = contentLeft();
        int width = contentWidth();
        Category[] values = Category.values();
        int gap = 2;
        int tabWidth = Math.max(58, (width - gap * (values.length - 1)) / values.length);
        int x = left;

        for (int i = 0; i < values.length; i++) {
            Category value = values[i];
            int actual = i == values.length - 1 ? left + width - x : tabWidth;
            this.addDrawableChild(new LazyBuilderSettingsTabWidget(
                    x,
                    TAB_TOP,
                    actual,
                    TAB_HEIGHT,
                    value.text(),
                    value == category,
                    () -> openCategory(value)
            ));
            x += actual + gap;
        }
    }

    private void buildVideoRows() {
        rows.add(Row.action(
                "Video Settings",
                "Display, graphics, render distance and Minecraft video options.",
                "Open",
                () -> {
                    if (client != null) {
                        client.setScreen(new VideoOptionsScreen(this, client, client.options));
                    }
                }
        ));
        rows.add(Row.status(
                "Performance",
                "Extra rendering and efficiency controls live inside Video Settings.",
                "In Video"
        ));
    }

    private void buildControlsRows() {
        rows.add(Row.action(
                "Controls & Keybinds",
                "Keyboard, mouse and all registered Minecraft/Fabric key bindings.",
                "Open",
                () -> {
                    if (client != null) {
                        client.setScreen(new ControlsOptionsScreen(this, client.options));
                    }
                }
        ));
    }

    private void buildInterfaceRows() {
        UtilityPreferences prefs = UtilityManagerClient.preferences();

        rows.add(Row.toggle(
                "Compact Debug HUD",
                "Keep builder-relevant debug information without the full F3 wall.",
                prefs.compactDebugHud(),
                enabled -> updateInterface(prefs.withCompactDebugHud(enabled))
        ));
        rows.add(Row.toggle(
                "Contextual Screenshot Names",
                "Add world or server context to automatic screenshot names.",
                prefs.contextualScreenshotNames(),
                enabled -> updateInterface(prefs.withContextualScreenshotNames(enabled))
        ));
        rows.add(Row.toggle(
                "Quick Creative Search",
                "Start typing in Creative inventory to search immediately.",
                prefs.instantCreativeSearch(),
                enabled -> updateInterface(prefs.withInstantCreativeSearch(enabled))
        ));
    }

    private void buildToolsRows() {
        rows.add(Row.status(
                "Editing Tools",
                "Axiom and LazyBuilder building extensions.",
                "In Editor"
        ));
        rows.add(Row.status(
                "Map & Worlds",
                "World map, world management and transfer workflows.",
                "Per World"
        ));
    }

    private void layoutRows() {
        int width = contentWidth();
        int left = contentLeft();
        int columns = width >= 560 ? 2 : 1;
        int columnWidth = columns == 2 ? (width - COLUMN_GAP) / 2 : width;

        for (int i = 0; i < rows.size(); i++) {
            int column = columns == 2 ? i % 2 : 0;
            int rowIndex = columns == 2 ? i / 2 : i;
            int x = left + column * (columnWidth + COLUMN_GAP);
            int y = CONTENT_TOP + rowIndex * (ROW_HEIGHT + ROW_GAP);
            Row row = rows.get(i);
            row.x = x;
            row.y = y;
            row.width = columnWidth;

            int controlWidth = Math.min(CONTROL_WIDTH, Math.max(82, columnWidth / 3));
            int controlX = x + columnWidth - controlWidth - 8;
            int controlY = y + (ROW_HEIGHT - CONTROL_HEIGHT) / 2;

            this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                    controlX,
                    controlY,
                    controlWidth,
                    CONTROL_HEIGHT,
                    Text.literal(row.controlLabel()),
                    row.interactive(),
                    row.action()
            ));
        }
    }

    private void addFooter() {
        int y = this.height - 30;
        int left = contentLeft();

        boolean canReset = category == Category.INTERFACE;
        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                left,
                y,
                92,
                22,
                Text.literal("Reset"),
                canReset,
                this::resetCurrentCategory
        ));

        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                this.width / 2 - 46,
                y,
                92,
                22,
                Text.literal("Back"),
                true,
                this::close
        ));

        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                left + contentWidth() - 92,
                y,
                92,
                22,
                Text.literal("Done"),
                true,
                this::close
        ));
    }

    private void resetCurrentCategory() {
        if (category != Category.INTERFACE) return;
        UtilityPreferences defaults = UtilityPreferences.defaults();
        UtilityPreferences current = UtilityManagerClient.preferences()
                .withCompactDebugHud(defaults.compactDebugHud())
                .withContextualScreenshotNames(defaults.contextualScreenshotNames())
                .withInstantCreativeSearch(defaults.instantCreativeSearch());
        UtilityManagerClient.updatePreferences(current);
        refreshCategory();
    }

    private void updateInterface(UtilityPreferences updated) {
        UtilityManagerClient.updatePreferences(updated);
        refreshCategory();
    }

    private void openCategory(Category next) {
        if (client != null && next != category) {
            client.setScreen(new LazyBuilderSettingsScreen(parent, next));
        }
    }

    private void refreshCategory() {
        if (client != null) {
            client.setScreen(new LazyBuilderSettingsScreen(parent, category));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);
        context.fill(0, 0, width, HEADER_HEIGHT, TOP_BAR);
        context.fill(0, height - FOOTER_HEIGHT, width, height, TOP_BAR);

        int left = contentLeft();
        context.drawTextWithShadow(textRenderer, Text.literal("SETTINGS"), left, 15, TEXT_PRIMARY);
        String categoryLabel = category.label.toUpperCase(java.util.Locale.ROOT);
        context.drawTextWithShadow(textRenderer, Text.literal(categoryLabel), left, 70, TEXT_SECONDARY);

        for (Row row : rows) {
            boolean hovered = mouseX >= row.x && mouseX < row.x + row.width
                    && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
            int fill = hovered ? ROW_HOVER : ROW_FILL;
            context.fill(row.x, row.y, row.x + row.width, row.y + ROW_HEIGHT, ROW_BORDER);
            context.fill(row.x + 1, row.y + 1, row.x + row.width - 1, row.y + ROW_HEIGHT - 1, fill);

            int textMax = Math.max(60, row.width - CONTROL_WIDTH - 28);
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(textRenderer.trimToWidth(row.title, textMax)),
                    row.x + 10,
                    row.y + 9,
                    TEXT_PRIMARY
            );
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(textRenderer.trimToWidth(row.description, textMax)),
                    row.x + 10,
                    row.y + 25,
                    TEXT_MUTED
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private int contentWidth() {
        return Math.min(MAX_WIDTH, Math.max(280, width - SIDE_MARGIN * 2));
    }

    private int contentLeft() {
        return (width - contentWidth()) / 2;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    private static final class Row {
        private final String title;
        private final String description;
        private final java.util.function.Supplier<String> controlText;
        private final boolean interactive;
        private final Runnable action;
        private int x;
        private int y;
        private int width;

        private Row(
                String title,
                String description,
                java.util.function.Supplier<String> controlText,
                boolean interactive,
                Runnable action
        ) {
            this.title = title;
            this.description = description;
            this.controlText = controlText;
            this.interactive = interactive;
            this.action = action;
        }

        static Row action(String title, String description, String control, Runnable action) {
            return new Row(title, description, () -> control, true, action);
        }

        static Row status(String title, String description, String status) {
            return new Row(title, description, () -> status, false, () -> {});
        }

        static Row toggle(
                String title,
                String description,
                boolean enabled,
                java.util.function.Consumer<Boolean> setter
        ) {
            return new Row(
                    title,
                    description,
                    () -> enabled ? "ON" : "OFF",
                    true,
                    () -> setter.accept(!enabled)
            );
        }

        String controlLabel() {
            return controlText.get();
        }

        boolean interactive() {
            return interactive;
        }

        Runnable action() {
            return action;
        }
    }
}

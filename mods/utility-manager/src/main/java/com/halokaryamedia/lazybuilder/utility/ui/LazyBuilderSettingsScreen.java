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
 * Full game-style LazyBuilder settings surface.
 *
 * Presentation follows modern PC game settings: horizontal category tabs, sectioned
 * single-column rows, right-aligned controls, a restrained context pane and a fixed footer.
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
    static final int ACCENT = 0xFF4FD0B0;

    private static final int BACKGROUND = 0xF20B0E12;
    private static final int TOP_BAR = 0xE813171C;
    private static final int ROW_FILL = 0xA81A1F25;
    private static final int ROW_HOVER = 0xC521272E;
    private static final int DIVIDER = 0x44545C66;

    private static final int MAX_SHELL_WIDTH = 920;
    private static final int MIN_SIDE_MARGIN = 12;
    private static final int HEADER_HEIGHT = 34;
    private static final int TAB_HEIGHT = 24;
    private static final int TAB_TOP = 42;
    private static final int CONTENT_TOP = 88;
    private static final int VIEWPORT_TOP = 78;
    private static final int SECTION_HEIGHT = 18;
    private static final int SECTION_GAP = 12;
    private static final int ROW_HEIGHT = 40;
    private static final int ROW_GAP = 2;
    private static final int CONTROL_WIDTH = 132;
    private static final int CONTROL_HEIGHT = 22;
    private static final int FOOTER_HEIGHT = 40;

    private final Screen parent;
    private final Category category;
    private final List<Section> sections = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;

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
        sections.clear();
        addTabs();

        switch (category) {
            case VIDEO -> buildVideoSections();
            case CONTROLS -> buildControlsSections();
            case INTERFACE -> buildInterfaceSections();
            case TOOLS -> buildToolsSections();
        }

        layoutSections();
        addFooter();
    }

    private void addTabs() {
        int left = panelLeft();
        int width = panelWidth();
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

    private void buildVideoSections() {
        Section display = new Section("DISPLAY");
        display.rows.add(Row.action(
                "Video Settings",
                "Display, graphics, render distance and Minecraft video options.",
                "Open",
                () -> {
                    if (client != null) {
                        client.setScreen(new VideoOptionsScreen(this, client, client.options));
                    }
                }
        ));
        sections.add(display);

        Section performance = new Section("PERFORMANCE");
        performance.rows.add(Row.status(
                "Performance Settings",
                "Extra frame-rate, rendering and memory controls are integrated into Video Settings.",
                "In Video"
        ));
        sections.add(performance);
    }

    private void buildControlsSections() {
        Section input = new Section("INPUT");
        input.rows.add(Row.action(
                "Controls & Keybinds",
                "Keyboard, mouse and all registered Minecraft/Fabric key bindings.",
                "Open",
                () -> {
                    if (client != null) {
                        client.setScreen(new ControlsOptionsScreen(this, client.options));
                    }
                }
        ));
        sections.add(input);
    }

    private void buildInterfaceSections() {
        UtilityPreferences prefs = UtilityManagerClient.preferences();

        Section hud = new Section("HUD");
        hud.rows.add(Row.toggle(
                "Compact Debug HUD",
                "Keep builder-relevant debug information without the full F3 wall.",
                prefs.compactDebugHud(),
                enabled -> updateInterface(prefs.withCompactDebugHud(enabled))
        ));
        sections.add(hud);

        Section capture = new Section("CAPTURE");
        capture.rows.add(Row.toggle(
                "Contextual Screenshot Names",
                "Add world or server context to automatic screenshot names.",
                prefs.contextualScreenshotNames(),
                enabled -> updateInterface(prefs.withContextualScreenshotNames(enabled))
        ));
        sections.add(capture);

        Section creative = new Section("CREATIVE");
        creative.rows.add(Row.toggle(
                "Quick Creative Search",
                "Start typing in Creative inventory to search immediately.",
                prefs.instantCreativeSearch(),
                enabled -> updateInterface(prefs.withInstantCreativeSearch(enabled))
        ));
        sections.add(creative);
    }

    private void buildToolsSections() {
        Section building = new Section("BUILDING");
        building.rows.add(Row.status(
                "Editing Tools",
                "Axiom and LazyBuilder building extensions.",
                "In Editor"
        ));
        sections.add(building);

        Section worlds = new Section("WORLD MANAGEMENT");
        worlds.rows.add(Row.status(
                "Map & Worlds",
                "World map, world management and transfer workflows.",
                "Per World"
        ));
        sections.add(worlds);
    }

    private void layoutSections() {
        int x = panelLeft();
        int width = panelWidth();
        int viewportBottom = viewportBottom();
        int contentHeight = totalContentHeight();
        int viewportHeight = Math.max(1, viewportBottom - CONTENT_TOP);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        int baseY = CONTENT_TOP;

        for (Section section : sections) {
            section.y = baseY - scrollOffset;
            baseY += SECTION_HEIGHT;

            for (Row row : section.rows) {
                row.x = x;
                row.y = baseY - scrollOffset;
                row.width = width;

                int controlWidth = Math.min(CONTROL_WIDTH, Math.max(96, width / 3));
                int controlX = x + width - controlWidth - 8;
                int controlY = row.y + (ROW_HEIGHT - CONTROL_HEIGHT) / 2;

                if (row.y + ROW_HEIGHT > VIEWPORT_TOP && row.y < viewportBottom) {
                    this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                            controlX,
                            controlY,
                            controlWidth,
                            CONTROL_HEIGHT,
                            Text.literal(row.controlLabel()),
                            row.interactive(),
                            row.kind,
                            row.action()
                    ));
                }

                baseY += ROW_HEIGHT + ROW_GAP;
            }

            baseY += SECTION_GAP;
        }

    }

    private int totalContentHeight() {
        int total = 0;
        for (Section section : sections) {
            total += SECTION_HEIGHT;
            total += section.rows.size() * (ROW_HEIGHT + ROW_GAP);
            total += SECTION_GAP;
        }
        return Math.max(0, total);
    }

    private void addFooter() {
        int y = height - 30;
        int left = shellLeft();
        int shellRight = left + shellWidth();

        boolean canReset = category == Category.INTERFACE;
        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                left + 8,
                y,
                92,
                22,
                Text.literal("Reset"),
                canReset,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::resetCurrentCategory
        ));

        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                shellRight - 100,
                y,
                92,
                22,
                Text.literal("Back"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
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
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Custom shell owns its background. Prevent Screen.render() from applying
        // Minecraft's blur/darkening a second time over our labels and section text.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);
        context.fill(0, 0, width, HEADER_HEIGHT, TOP_BAR);
        context.fill(0, height - FOOTER_HEIGHT, width, height, TOP_BAR);

        int panelLeft = panelLeft();
        int panelRight = panelLeft + panelWidth();

        context.drawTextWithShadow(textRenderer, Text.literal("SETTINGS"), shellLeft() + 8, 15, TEXT_PRIMARY);
        context.fill(panelRight + 14, 76, panelRight + 15, height - FOOTER_HEIGHT - 10, DIVIDER);

        context.enableScissor(panelLeft, VIEWPORT_TOP, panelRight, viewportBottom());
        for (Section section : sections) {
            if (section.y + SECTION_HEIGHT > VIEWPORT_TOP && section.y < viewportBottom()) {
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(section.title),
                        panelLeft,
                        section.y + 4,
                        TEXT_SECONDARY
                );
            }

            for (Row row : section.rows) {
                if (row.y + ROW_HEIGHT <= VIEWPORT_TOP || row.y >= viewportBottom()) continue;

                boolean hovered = mouseX >= row.x && mouseX < row.x + row.width
                        && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
                int fill = hovered ? ROW_HOVER : ROW_FILL;

                context.fill(row.x, row.y + ROW_HEIGHT - 1, row.x + row.width, row.y + ROW_HEIGHT, DIVIDER);
                context.fill(row.x, row.y, row.x + row.width, row.y + ROW_HEIGHT - 1, fill);

                int textMax = Math.max(70, row.width - CONTROL_WIDTH - 34);
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(textRenderer.trimToWidth(row.title, textMax)),
                        row.x + 8,
                        row.y + 8,
                        TEXT_PRIMARY
                );
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(textRenderer.trimToWidth(row.description, textMax)),
                        row.x + 8,
                        row.y + 23,
                        TEXT_MUTED
                );
            }
        }
        context.disableScissor();

        renderScrollBar(context, panelRight + 6);
        renderContextPane(context, panelRight + 30);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderContextPane(DrawContext context, int x) {
        int available = shellLeft() + shellWidth() - x - 8;
        if (available < 120) return;

        context.drawTextWithShadow(
                textRenderer,
                Text.literal(category.label.toUpperCase(java.util.Locale.ROOT)),
                x,
                88,
                TEXT_PRIMARY
        );

        String description = switch (category) {
            case VIDEO -> "Display and rendering options stay integrated with Minecraft.";
            case CONTROLS -> "All key bindings remain in the standard Minecraft controls screen.";
            case INTERFACE -> "Only builder-facing interface preferences are exposed here.";
            case TOOLS -> "Tool-specific configuration stays close to the workflow that owns it.";
        };

        List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(Text.literal(description), available);
        int y = 106;
        for (net.minecraft.text.OrderedText line : lines) {
            context.drawTextWithShadow(textRenderer, line, x, y, TEXT_MUTED);
            y += 11;
        }
    }

    private void renderScrollBar(DrawContext context, int x) {
        if (maxScroll <= 0) return;
        int top = VIEWPORT_TOP;
        int bottom = viewportBottom();
        int trackHeight = Math.max(1, bottom - top);
        int contentHeight = trackHeight + maxScroll;
        int thumbHeight = Math.max(18, trackHeight * trackHeight / contentHeight);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + (int) Math.round((scrollOffset / (double) maxScroll) * travel);

        context.fill(x, top, x + 2, bottom, 0x334A525C);
        context.fill(x, thumbY, x + 2, thumbY + thumbHeight, ACCENT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0
                && mouseX >= panelLeft()
                && mouseX <= panelLeft() + panelWidth()
                && mouseY >= VIEWPORT_TOP
                && mouseY <= viewportBottom()) {
            int next = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(verticalAmount * 24.0)));
            if (next != scrollOffset) {
                scrollOffset = next;
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int viewportBottom() {
        return Math.max(VIEWPORT_TOP + 1, height - FOOTER_HEIGHT - 6);
    }

    private int shellWidth() {
        return Math.min(MAX_SHELL_WIDTH, Math.max(280, width - MIN_SIDE_MARGIN * 2));
    }

    private int shellLeft() {
        return (width - shellWidth()) / 2;
    }

    private int panelWidth() {
        int shell = shellWidth();
        if (shell < 560) return shell - 16;
        return Math.min(520, Math.max(390, (int) (shell * 0.68)));
    }

    private int panelLeft() {
        return shellLeft() + 8;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    private static final class Section {
        private final String title;
        private final List<Row> rows = new ArrayList<>();
        private int y;

        private Section(String title) {
            this.title = title;
        }
    }

    private static final class Row {
        private final String title;
        private final String description;
        private final java.util.function.Supplier<String> controlText;
        private final boolean interactive;
        private final LazyBuilderSettingsControlWidget.Kind kind;
        private final Runnable action;
        private int x;
        private int y;
        private int width;

        private Row(
                String title,
                String description,
                java.util.function.Supplier<String> controlText,
                boolean interactive,
                LazyBuilderSettingsControlWidget.Kind kind,
                Runnable action
        ) {
            this.title = title;
            this.description = description;
            this.controlText = controlText;
            this.interactive = interactive;
            this.kind = kind;
            this.action = action;
        }

        static Row action(String title, String description, String control, Runnable action) {
            return new Row(
                    title,
                    description,
                    () -> control,
                    true,
                    LazyBuilderSettingsControlWidget.Kind.ACTION,
                    action
            );
        }

        static Row status(String title, String description, String status) {
            return new Row(
                    title,
                    description,
                    () -> status,
                    false,
                    LazyBuilderSettingsControlWidget.Kind.STATUS,
                    () -> {}
            );
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
                    LazyBuilderSettingsControlWidget.Kind.TOGGLE,
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

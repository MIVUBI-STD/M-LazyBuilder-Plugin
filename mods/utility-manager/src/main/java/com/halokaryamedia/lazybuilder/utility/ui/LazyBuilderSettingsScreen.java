package com.halokaryamedia.lazybuilder.utility.ui;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.UtilityPreferences;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Permanent LazyBuilder settings shell.
 *
 * Minecraft's GameOptions / KeyBinding objects remain the authority and persistence owner;
 * this class replaces only the user-facing settings presentation.
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
    private static final int CONTENT_TOP = 82;
    private static final int VIEWPORT_TOP = 74;
    private static final int SECTION_HEIGHT = 16;
    private static final int SECTION_GAP = 8;
    private static final int ROW_HEIGHT = 32;
    private static final int ROW_GAP = 2;
    private static final int CONTROL_WIDTH = 138;
    private static final int CONTROL_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 40;

    private static final String PERFORMANCE_SCREEN_SHARE =
            "lazybuilder-performance-manager:settings-screen";

    private final Screen parent;
    private final Category category;
    private final List<Section> sections = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;
    private DropdownState dropdown;

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
        if (client == null) return;

        Section display = new Section("DISPLAY");
        display.rows.add(Row.toggle(
                "Fullscreen",
                "Use Minecraft in fullscreen mode.",
                client.options.getFullscreen().getValue(),
                value -> setOption(client.options.getFullscreen(), value)
        ));
        display.rows.add(Row.toggle(
                "V-Sync",
                "Synchronize frame output with the display refresh cycle.",
                client.options.getEnableVsync().getValue(),
                value -> setOption(client.options.getEnableVsync(), value)
        ));
        display.rows.add(Row.value(
                "Max Frame Rate",
                "Upper frame-rate limit while Minecraft is active.",
                client.options.getMaxFps().getValue() + " FPS",
                () -> openIntegerChoice(
                        "Max Frame Rate",
                        client.options.getMaxFps(),
                        new int[]{30, 60, 90, 120, 144, 165, 240, 260},
                        value -> value + " FPS"
                )
        ));
        display.rows.add(Row.value(
                "GUI Scale",
                "Scale Minecraft interface elements.",
                client.options.getGuiScale().getValue() == 0
                        ? "Auto"
                        : Integer.toString(client.options.getGuiScale().getValue()),
                () -> openIntegerChoice(
                        "GUI Scale",
                        client.options.getGuiScale(),
                        new int[]{0, 1, 2, 3, 4},
                        value -> value == 0 ? "Auto" : Integer.toString(value)
                )
        ));
        display.rows.add(Row.slider(
                "Brightness",
                "Adjust visibility in dark areas.",
                client.options.getGamma().getValue(),
                0.0,
                1.0,
                0.05,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(client.options.getGamma(), value)
        ));
        display.rows.add(Row.slider(
                "Field of View",
                "Adjust the horizontal view angle.",
                client.options.getFov().getValue(),
                30.0,
                110.0,
                1.0,
                value -> Math.round(value) + "°",
                value -> setOptionLive(client.options.getFov(), (int) Math.round(value))
        ));
        sections.add(display);

        Section world = new Section("WORLD");
        world.rows.add(Row.slider(
                "Render Distance",
                "How far terrain is rendered around the player.",
                client.options.getViewDistance().getValue(),
                2.0,
                32.0,
                1.0,
                value -> Math.round(value) + " Chunks",
                value -> setOptionLive(client.options.getViewDistance(), (int) Math.round(value))
        ));
        world.rows.add(Row.slider(
                "Simulation Distance",
                "How far world simulation remains active.",
                client.options.getSimulationDistance().getValue(),
                5.0,
                32.0,
                1.0,
                value -> Math.round(value) + " Chunks",
                value -> setOptionLive(client.options.getSimulationDistance(), (int) Math.round(value))
        ));
        world.rows.add(Row.slider(
                "Entity Distance",
                "Scale the distance at which entities are rendered.",
                client.options.getEntityDistanceScaling().getValue(),
                0.5,
                2.0,
                0.05,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(client.options.getEntityDistanceScaling(), value)
        ));
        sections.add(world);

        Section quality = new Section("QUALITY");
        quality.rows.add(Row.value(
                "Graphics",
                "Choose the overall Minecraft graphics mode.",
                humanize(client.options.getGraphicsMode().getValue()),
                () -> openEnumChoice(
                        "Graphics",
                        client.options.getGraphicsMode(),
                        GraphicsMode.values()
                )
        ));
        quality.rows.add(Row.value(
                "Clouds",
                "Choose how clouds are rendered.",
                humanize(client.options.getCloudRenderMode().getValue()),
                () -> openEnumChoice(
                        "Clouds",
                        client.options.getCloudRenderMode(),
                        CloudRenderMode.values()
                )
        ));
        quality.rows.add(Row.value(
                "Particles",
                "Control particle density.",
                humanize(client.options.getParticles().getValue()),
                () -> openEnumChoice(
                        "Particles",
                        client.options.getParticles(),
                        ParticlesMode.values()
                )
        ));
        quality.rows.add(Row.value(
                "Mipmap Levels",
                "Texture filtering detail for distant blocks.",
                Integer.toString(client.options.getMipmapLevels().getValue()),
                () -> openIntegerChoice(
                        "Mipmap Levels",
                        client.options.getMipmapLevels(),
                        new int[]{0, 1, 2, 3, 4},
                        Object::toString
                )
        ));
        sections.add(quality);

        Section performance = new Section("PERFORMANCE");
        performance.rows.add(Row.action(
                "Performance Tuning",
                "Background FPS, rendering, visibility and memory controls.",
                performanceProviderAvailable() ? "Open" : "Unavailable",
                this::openPerformanceSettings
        ));
        sections.add(performance);
    }

    private void buildControlsSections() {
        if (client == null) return;

        Section mouse = new Section("MOUSE");
        mouse.rows.add(Row.slider(
                "Sensitivity",
                "Mouse look sensitivity.",
                client.options.getMouseSensitivity().getValue(),
                0.0,
                1.0,
                0.01,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(client.options.getMouseSensitivity(), value)
        ));
        mouse.rows.add(Row.toggle(
                "Invert Mouse",
                "Invert vertical mouse movement.",
                client.options.getInvertYMouse().getValue(),
                value -> setOption(client.options.getInvertYMouse(), value)
        ));
        mouse.rows.add(Row.toggle(
                "Raw Input",
                "Read mouse movement directly from the operating system.",
                client.options.getRawMouseInput().getValue(),
                value -> setOption(client.options.getRawMouseInput(), value)
        ));
        mouse.rows.add(Row.toggle(
                "Discrete Mouse Scroll",
                "Use discrete steps for mouse-wheel scrolling.",
                client.options.getDiscreteMouseScroll().getValue(),
                value -> setOption(client.options.getDiscreteMouseScroll(), value)
        ));
        mouse.rows.add(Row.slider(
                "Mouse Wheel Sensitivity",
                "Adjust scroll-wheel input sensitivity.",
                client.options.getMouseWheelSensitivity().getValue(),
                0.01,
                3.0,
                0.01,
                value -> String.format(Locale.ROOT, "%.2f", value),
                value -> setOptionLive(client.options.getMouseWheelSensitivity(), value)
        ));
        sections.add(mouse);

        Section movement = new Section("MOVEMENT");
        movement.rows.add(Row.toggle(
                "Auto Jump",
                "Automatically jump when moving into a one-block obstacle.",
                client.options.getAutoJump().getValue(),
                value -> setOption(client.options.getAutoJump(), value)
        ));
        movement.rows.add(Row.toggle(
                "Toggle Sneak",
                "Press once to remain sneaking until toggled again.",
                client.options.getSneakToggled().getValue(),
                value -> setOption(client.options.getSneakToggled(), value)
        ));
        movement.rows.add(Row.toggle(
                "Toggle Sprint",
                "Press once to remain sprinting until toggled again.",
                client.options.getSprintToggled().getValue(),
                value -> setOption(client.options.getSprintToggled(), value)
        ));
        sections.add(movement);

        Section bindings = new Section("KEY BINDINGS");
        bindings.rows.add(Row.action(
                "Key Bindings",
                "Configure every vanilla and Fabric-registered key binding.",
                "Open",
                () -> {
                    if (client != null) {
                        client.setScreen(new LazyBuilderKeybindSettingsScreen(this));
                    }
                }
        ));
        sections.add(bindings);
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

        Section capture = new Section("SCREENSHOTS");
        capture.rows.add(Row.toggle(
                "Contextual Screenshot Names",
                "Add world or server context to automatic screenshot names.",
                prefs.contextualScreenshotNames(),
                enabled -> updateInterface(prefs.withContextualScreenshotNames(enabled))
        ));
        sections.add(capture);

        Section creative = new Section("CREATIVE MODE");
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
                "Managed in Editor"
        ));
        sections.add(building);

        Section worlds = new Section("WORLD & MAP");
        worlds.rows.add(Row.status(
                "Map & Worlds",
                "World map, world management and transfer workflows.",
                "Managed per World"
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

                int controlWidth = Math.min(CONTROL_WIDTH, Math.max(100, width / 3));
                int controlX = x + width - controlWidth - 8;
                int controlY = row.y + (ROW_HEIGHT - CONTROL_HEIGHT) / 2;

                if (row.y + ROW_HEIGHT > VIEWPORT_TOP && row.y < viewportBottom) {
                    if (row.slider != null) {
                        this.addDrawableChild(new LazyBuilderSettingsSliderWidget(
                                controlX,
                                controlY,
                                controlWidth,
                                CONTROL_HEIGHT,
                                row.slider.current(),
                                row.slider.min(),
                                row.slider.max(),
                                row.slider.step(),
                                row.slider.formatter(),
                                row.slider.onChange()
                        ));
                    } else {
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

                    if (dropdown != null && dropdown.anchor.equals(row.title)) {
                        int popupHeight = dropdown.choices.size() * DropdownState.ITEM_HEIGHT;
                        int below = controlY + CONTROL_HEIGHT + 2;
                        int popupY = below + popupHeight <= viewportBottom
                                ? below
                                : Math.max(VIEWPORT_TOP, controlY - popupHeight - 2);
                        dropdown.position(controlX, popupY, controlWidth);
                    }
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
                this::confirmResetCurrentCategory
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

    private void confirmResetCurrentCategory() {
        if (category != Category.INTERFACE || client == null) return;
        client.setScreen(new LazyBuilderConfirmScreen(
                this,
                "Reset Interface",
                "Restore all Interface settings to their defaults?",
                "Reset",
                this::resetCurrentCategory
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

    private <T> void setOption(SimpleOption<T> option, T value) {
        if (client == null) return;
        option.setValue(value);
        client.options.write();
        client.options.sendClientSettings();
        refreshCategory();
    }

    private <T> void setOptionLive(SimpleOption<T> option, T value) {
        if (client == null) return;
        option.setValue(value);
        client.options.write();
        client.options.sendClientSettings();
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

    private void openIntegerChoice(
            String title,
            SimpleOption<Integer> option,
            int[] values,
            Function<Integer, String> label
    ) {
        if (client == null) return;
        List<DropdownChoice> choices = new ArrayList<>();
        int current = option.getValue();
        for (int value : values) {
            choices.add(new DropdownChoice(
                    label.apply(value),
                    value == current,
                    () -> setOption(option, value)
            ));
        }
        openDropdown(title, choices);
    }

    private void openDoubleChoice(
            String title,
            SimpleOption<Double> option,
            double[] values,
            Function<Double, String> label
    ) {
        if (client == null) return;
        List<DropdownChoice> choices = new ArrayList<>();
        double current = option.getValue();
        for (double value : values) {
            choices.add(new DropdownChoice(
                    label.apply(value),
                    Math.abs(value - current) < 0.0001,
                    () -> setOption(option, value)
            ));
        }
        openDropdown(title, choices);
    }

    private <E extends Enum<E>> void openEnumChoice(
            String title,
            SimpleOption<E> option,
            E[] values
    ) {
        if (client == null) return;
        List<DropdownChoice> choices = new ArrayList<>();
        E current = option.getValue();
        for (E value : values) {
            choices.add(new DropdownChoice(
                    humanize(value),
                    value == current,
                    () -> setOption(option, value)
            ));
        }
        openDropdown(title, choices);
    }

    private void openDropdown(String anchor, List<DropdownChoice> choices) {
        dropdown = new DropdownState(anchor, choices);
        clearAndInit();
    }

    private boolean performanceProviderAvailable() {
        return FabricLoader.getInstance().getObjectShare().get(PERFORMANCE_SCREEN_SHARE) instanceof Function<?, ?>;
    }

    @SuppressWarnings("unchecked")
    private void openPerformanceSettings() {
        if (client == null) return;
        Object shared = FabricLoader.getInstance().getObjectShare().get(PERFORMANCE_SCREEN_SHARE);
        if (shared instanceof Function<?, ?> raw) {
            Function<Screen, Screen> provider = (Function<Screen, Screen>) raw;
            client.setScreen(provider.apply(this));
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);
        context.fill(0, 0, width, HEADER_HEIGHT, TOP_BAR);
        context.fill(0, height - FOOTER_HEIGHT, width, height, TOP_BAR);

        int panelLeft = panelLeft();
        int panelRight = panelLeft + panelWidth();

        context.drawTextWithShadow(textRenderer, Text.literal("SETTINGS"), shellLeft() + 8, 15, TEXT_PRIMARY);
        if (hasContextPane()) {
            context.fill(panelRight + 14, VIEWPORT_TOP, panelRight + 15, height - FOOTER_HEIGHT - 10, DIVIDER);
        }

        Row hoveredRow = null;
        context.enableScissor(panelLeft, VIEWPORT_TOP, panelRight, viewportBottom());
        for (Section section : sections) {
            if (section.y + SECTION_HEIGHT > VIEWPORT_TOP && section.y < viewportBottom()) {
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(section.title),
                        panelLeft,
                        section.y + 3,
                        TEXT_SECONDARY
                );
            }

            for (Row row : section.rows) {
                if (row.y + ROW_HEIGHT <= VIEWPORT_TOP || row.y >= viewportBottom()) continue;

                boolean hovered = mouseX >= row.x && mouseX < row.x + row.width
                        && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
                if (hovered) hoveredRow = row;

                context.fill(row.x, row.y, row.x + row.width, row.y + ROW_HEIGHT - 1, hovered ? ROW_HOVER : ROW_FILL);
                context.fill(row.x, row.y + ROW_HEIGHT - 1, row.x + row.width, row.y + ROW_HEIGHT, DIVIDER);

                int textMax = Math.max(70, row.width - CONTROL_WIDTH - 34);
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(textRenderer.trimToWidth(row.title, textMax)),
                        row.x + 8,
                        row.y + 12,
                        TEXT_PRIMARY
                );
            }
        }
        context.disableScissor();

        renderScrollBar(context, panelRight + 6);
        renderContextPane(context, panelRight + 30, hoveredRow);
        super.render(context, mouseX, mouseY, delta);
        renderDropdown(context, mouseX, mouseY);
    }

    private void renderContextPane(DrawContext context, int x, Row hoveredRow) {
        if (!hasContextPane()) return;
        int available = shellLeft() + shellWidth() - x - 8;
        if (available < 120) return;

        String title;
        String description;
        if (hoveredRow != null) {
            title = hoveredRow.title.toUpperCase(Locale.ROOT);
            description = hoveredRow.description;
        } else {
            title = category.label.toUpperCase(Locale.ROOT);
            description = switch (category) {
                case VIDEO -> "Display, world rendering, quality and performance controls.";
                case CONTROLS -> "Mouse, movement and all registered key bindings.";
                case INTERFACE -> "Builder-facing HUD, screenshot and Creative-mode preferences.";
                case TOOLS -> "Tool-specific setup stays close to the workflow that owns it.";
            };
        }

        context.drawTextWithShadow(textRenderer, Text.literal(title), x, 84, TEXT_PRIMARY);
        int y = 102;
        for (var line : textRenderer.wrapLines(Text.literal(description), available)) {
            context.drawTextWithShadow(textRenderer, line, x, y, TEXT_MUTED);
            y += 11;
        }
    }

    private void renderDropdown(DrawContext context, int mouseX, int mouseY) {
        if (dropdown == null || !dropdown.positioned()) return;

        int left = dropdown.x;
        int top = dropdown.y;
        int right = left + dropdown.width;
        int bottom = top + dropdown.choices.size() * DropdownState.ITEM_HEIGHT;

        context.fill(left - 1, top - 1, right + 1, bottom + 1, 0xAA4F5964);
        context.fill(left, top, right, bottom, 0xFF151A20);

        for (int i = 0; i < dropdown.choices.size(); i++) {
            DropdownChoice choice = dropdown.choices.get(i);
            int itemY = top + i * DropdownState.ITEM_HEIGHT;
            boolean hovered = mouseX >= left && mouseX < right
                    && mouseY >= itemY && mouseY < itemY + DropdownState.ITEM_HEIGHT;

            int fill = choice.selected
                    ? 0xCC1F6558
                    : hovered ? 0xFF252C34 : 0xFF1A1F25;
            context.fill(left, itemY, right, itemY + DropdownState.ITEM_HEIGHT - 1, fill);
            context.fill(left, itemY + DropdownState.ITEM_HEIGHT - 1, right, itemY + DropdownState.ITEM_HEIGHT, DIVIDER);

            int textColor = choice.selected || hovered ? TEXT_PRIMARY : TEXT_SECONDARY;
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(textRenderer.trimToWidth(choice.label, dropdown.width - 28)),
                    left + 7,
                    itemY + 6,
                    textColor
            );

            if (choice.selected) {
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal("✓"),
                        right - 14,
                        itemY + 6,
                        ACCENT
                );
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dropdown != null && dropdown.positioned()) {
            if (mouseX >= dropdown.x && mouseX < dropdown.x + dropdown.width
                    && mouseY >= dropdown.y && mouseY < dropdown.bottom()) {
                int index = (int) ((mouseY - dropdown.y) / DropdownState.ITEM_HEIGHT);
                if (index >= 0 && index < dropdown.choices.size()) {
                    DropdownChoice choice = dropdown.choices.get(index);
                    dropdown = null;
                    choice.action.run();
                    return true;
                }
            }

            dropdown = null;
            clearAndInit();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (dropdown != null && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            dropdown = null;
            clearAndInit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        if (dropdown != null) return true;
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

    private boolean hasContextPane() {
        return shellWidth() >= 760;
    }

    private int panelWidth() {
        int shell = shellWidth();
        if (!hasContextPane()) return shell - 16;
        return Math.min(540, Math.max(410, (int) (shell * 0.64)));
    }

    private int panelLeft() {
        return shellLeft() + 8;
    }

    private static String percentage(double value) {
        return Math.round(value * 100.0) + "%";
    }

    private static String humanize(Object value) {
        String raw = String.valueOf(value).replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(raw.length());
        boolean capitalize = true;
        for (char c : raw.toCharArray()) {
            if (capitalize && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                result.append(c);
            }
            if (c == ' ') capitalize = true;
        }
        return result.toString();
    }

    private static int[] range(int start, int end, int step) {
        int size = ((end - start) / step) + 1;
        int[] values = new int[size];
        for (int i = 0; i < size; i++) values[i] = start + i * step;
        return values;
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
        private final SliderSpec slider;
        private int x;
        private int y;
        private int width;

        private Row(
                String title,
                String description,
                java.util.function.Supplier<String> controlText,
                boolean interactive,
                LazyBuilderSettingsControlWidget.Kind kind,
                Runnable action,
                SliderSpec slider
        ) {
            this.title = title;
            this.description = description;
            this.controlText = controlText;
            this.interactive = interactive;
            this.kind = kind;
            this.action = action;
            this.slider = slider;
        }

        static Row action(String title, String description, String control, Runnable action) {
            return new Row(title, description, () -> control, true, LazyBuilderSettingsControlWidget.Kind.ACTION, action, null);
        }

        static Row value(String title, String description, String value, Runnable action) {
            return new Row(title, description, () -> value, true, LazyBuilderSettingsControlWidget.Kind.VALUE, action, null);
        }

        static Row status(String title, String description, String status) {
            return new Row(title, description, () -> status, false, LazyBuilderSettingsControlWidget.Kind.STATUS, () -> {}, null);
        }

        static Row toggle(
                String title,
                String description,
                boolean enabled,
                Consumer<Boolean> setter
        ) {
            return new Row(
                    title,
                    description,
                    () -> enabled ? "ON" : "OFF",
                    true,
                    LazyBuilderSettingsControlWidget.Kind.TOGGLE,
                    () -> setter.accept(!enabled),
                    null
            );
        }

        static Row slider(
                String title,
                String description,
                double current,
                double min,
                double max,
                double step,
                java.util.function.DoubleFunction<String> formatter,
                java.util.function.DoubleConsumer onChange
        ) {
            SliderSpec spec = new SliderSpec(current, min, max, step, formatter, onChange);
            return new Row(
                    title,
                    description,
                    () -> formatter.apply(current),
                    true,
                    LazyBuilderSettingsControlWidget.Kind.VALUE,
                    () -> {},
                    spec
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

    private record DropdownChoice(String label, boolean selected, Runnable action) {}

    private static final class DropdownState {
        private static final int ITEM_HEIGHT = 20;

        private final String anchor;
        private final List<DropdownChoice> choices;
        private int x;
        private int y;
        private int width;

        private DropdownState(String anchor, List<DropdownChoice> choices) {
            this.anchor = anchor;
            this.choices = List.copyOf(choices);
        }

        private void position(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
        }

        private boolean positioned() {
            return width > 0;
        }

        private int bottom() {
            return y + choices.size() * ITEM_HEIGHT;
        }
    }

    private record SliderSpec(
            double current,
            double min,
            double max,
            double step,
            java.util.function.DoubleFunction<String> formatter,
            java.util.function.DoubleConsumer onChange
    ) {}
}

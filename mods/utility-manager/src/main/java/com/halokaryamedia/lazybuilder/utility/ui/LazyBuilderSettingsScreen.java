package com.halokaryamedia.lazybuilder.utility.ui;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.UtilityPreferences;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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

    public enum VideoPage {
        DISPLAY("Display"),
        QUALITY("Quality"),
        VIEW("View"),
        PERFORMANCE("Performance");

        private final String label;

        VideoPage(String label) {
            this.label = label;
        }

        Text text() {
            return Text.literal(label);
        }
    }

    private enum GraphicsPreset {
        LOW("Low", 8, 5, 0.75, GraphicsMode.FAST, CloudRenderMode.OFF, ParticlesMode.MINIMAL, 1),
        MEDIUM("Medium", 16, 8, 1.00, GraphicsMode.FANCY, CloudRenderMode.FAST, ParticlesMode.DECREASED, 3),
        HIGH("High", 24, 12, 1.25, GraphicsMode.FABULOUS, CloudRenderMode.FANCY, ParticlesMode.ALL, 4),
        CUSTOM("Custom", -1, -1, -1.0, GraphicsMode.FANCY, CloudRenderMode.FANCY, ParticlesMode.ALL, -1);

        private final String label;
        private final int renderDistance;
        private final int simulationDistance;
        private final double entityDistance;
        private final GraphicsMode graphicsMode;
        private final CloudRenderMode cloudMode;
        private final ParticlesMode particlesMode;
        private final int mipmapLevels;

        GraphicsPreset(
                String label,
                int renderDistance,
                int simulationDistance,
                double entityDistance,
                GraphicsMode graphicsMode,
                CloudRenderMode cloudMode,
                ParticlesMode particlesMode,
                int mipmapLevels
        ) {
            this.label = label;
            this.renderDistance = renderDistance;
            this.simulationDistance = simulationDistance;
            this.entityDistance = entityDistance;
            this.graphicsMode = graphicsMode;
            this.cloudMode = cloudMode;
            this.particlesMode = particlesMode;
            this.mipmapLevels = mipmapLevels;
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
    private static final int VIDEO_TAB_TOP = 72;
    private static final int DEFAULT_CONTENT_TOP = 82;
    private static final int VIDEO_CONTENT_TOP = 112;
    private static final int DEFAULT_VIEWPORT_TOP = 74;
    private static final int VIDEO_VIEWPORT_TOP = 104;
    private static final int SECTION_HEIGHT = 16;
    private static final int SECTION_GAP = 8;
    private static final int ROW_HEIGHT = 32;
    private static final int ROW_GAP = 2;
    private static final int CONTROL_WIDTH = 138;
    private static final int CONTROL_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 40;

    private final Screen parent;
    private final Category category;
    private final VideoPage videoPage;
    private final List<Section> sections = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;
    private DropdownState dropdown;

    public LazyBuilderSettingsScreen(Screen parent) {
        this(parent, Category.VIDEO, VideoPage.QUALITY);
    }

    public LazyBuilderSettingsScreen(Screen parent, Category category) {
        this(parent, category, VideoPage.QUALITY);
    }

    private LazyBuilderSettingsScreen(Screen parent, Category category, VideoPage videoPage) {
        super(Text.literal("Settings"));
        this.parent = parent;
        this.category = category;
        this.videoPage = videoPage;
    }

    @Override
    protected void init() {
        sections.clear();
        addTabs();
        if (category == Category.VIDEO) addVideoTabs();

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

    private void addVideoTabs() {
        int left = panelLeft();
        int width = panelWidth();
        VideoPage[] values = VideoPage.values();
        int gap = 2;
        int tabWidth = Math.max(58, (width - gap * (values.length - 1)) / values.length);
        int x = left;

        for (int i = 0; i < values.length; i++) {
            VideoPage value = values[i];
            int actual = i == values.length - 1 ? left + width - x : tabWidth;
            this.addDrawableChild(new LazyBuilderSettingsTabWidget(
                    x,
                    VIDEO_TAB_TOP,
                    actual,
                    TAB_HEIGHT,
                    value.text(),
                    value == videoPage,
                    () -> openVideoPage(value)
            ));
            x += actual + gap;
        }
    }

    private void buildVideoSections() {
        if (client == null) return;
        switch (videoPage) {
            case DISPLAY -> buildVideoDisplaySections();
            case QUALITY -> buildVideoQualitySections();
            case VIEW -> buildVideoViewSections();
            case PERFORMANCE -> buildVideoPerformanceSections();
        }
    }

    private void buildVideoDisplaySections() {
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
                "Frame Rate Limit",
                "Set the maximum frame rate while Minecraft is active.",
                client.options.getMaxFps().getValue() + " FPS",
                () -> openIntegerChoice(
                        "Frame Rate Limit",
                        client.options.getMaxFps(),
                        new int[]{30, 60, 90, 120, 144, 165, 240, 260},
                        value -> value + " FPS"
                )
        ));
        display.rows.add(Row.slider(
                "Brightness",
                "Adjust visibility in darker areas without changing world lighting.",
                client.options.getGamma().getValue(),
                0.0,
                1.0,
                0.05,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(client.options.getGamma(), value)
        ));
        sections.add(display);
    }

    private void buildVideoQualitySections() {
        GraphicsPreset preset = detectGraphicsPreset();

        Section overall = new Section("OVERALL QUALITY");
        overall.rows.add(Row.value(
                "Quality Preset",
                "Apply a coordinated starting point for visual detail and view distance. Changing any controlled option makes the preset Custom.",
                preset.label,
                this::openGraphicsPresetChoice
        ));
        sections.add(overall);

        Section detail = new Section("VISUAL DETAIL");
        detail.rows.add(Row.value(
                "Graphics Mode",
                "Minecraft's base rendering style: Fast, Fancy, or Fabulous.",
                humanize(client.options.getGraphicsMode().getValue()),
                () -> openEnumChoice("Graphics Mode", client.options.getGraphicsMode(), GraphicsMode.values())
        ));
        detail.rows.add(Row.value(
                "Cloud Quality",
                "Choose whether clouds are disabled, simplified, or fully rendered.",
                humanize(client.options.getCloudRenderMode().getValue()),
                () -> openEnumChoice("Cloud Quality", client.options.getCloudRenderMode(), CloudRenderMode.values())
        ));
        detail.rows.add(Row.value(
                "Particles",
                "Control the amount of visual particle effects.",
                humanize(client.options.getParticles().getValue()),
                () -> openEnumChoice("Particles", client.options.getParticles(), ParticlesMode.values())
        ));
        detail.rows.add(Row.value(
                "Mipmap Levels",
                "Control texture filtering detail for blocks viewed at a distance.",
                Integer.toString(client.options.getMipmapLevels().getValue()),
                () -> openIntegerChoice(
                        "Mipmap Levels",
                        client.options.getMipmapLevels(),
                        new int[]{0, 1, 2, 3, 4},
                        Object::toString
                )
        ));
        sections.add(detail);
    }

    private void buildVideoViewSections() {
        Section distance = new Section("VIEW DISTANCE");
        distance.rows.add(Row.slider(
                "Render Distance",
                "Set how far terrain is drawn around the player.",
                client.options.getViewDistance().getValue(),
                2.0, 32.0, 1.0,
                value -> Math.round(value) + " Chunks",
                value -> setOptionLive(client.options.getViewDistance(), (int) Math.round(value))
        ));
        distance.rows.add(Row.slider(
                "Simulation Distance",
                "Set how far world simulation remains active around the player.",
                client.options.getSimulationDistance().getValue(),
                5.0, 32.0, 1.0,
                value -> Math.round(value) + " Chunks",
                value -> setOptionLive(client.options.getSimulationDistance(), (int) Math.round(value))
        ));
        distance.rows.add(Row.slider(
                "Entity Distance",
                "Scale the distance at which entities remain visible.",
                client.options.getEntityDistanceScaling().getValue(),
                0.5, 2.0, 0.05,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(client.options.getEntityDistanceScaling(), value)
        ));
        sections.add(distance);

        Section camera = new Section("CAMERA");
        camera.rows.add(Row.slider(
                "Field of View",
                "Adjust the camera viewing angle.",
                client.options.getFov().getValue(),
                30.0, 110.0, 1.0,
                value -> Math.round(value) + "°",
                value -> setOptionLive(client.options.getFov(), (int) Math.round(value))
        ));
        sections.add(camera);
    }

    private void buildVideoPerformanceSections() {
        PerformanceState state = performanceState();
        if (state == null) {
            Section unavailable = new Section("PERFORMANCE");
            unavailable.rows.add(Row.status(
                    "Performance Manager",
                    "The optional LazyBuilder performance component is not available in this client package.",
                    "Unavailable"
            ));
            sections.add(unavailable);
            return;
        }

        Section background = new Section("BACKGROUND LIMITS");
        background.rows.add(Row.toggle(
                "Reduce FPS in Background",
                "Use less GPU power when Minecraft is not the active window.",
                state.backgroundFpsPolicy(),
                enabled -> updatePerformance(value -> value.withBackgroundFpsPolicy(enabled))
        ));
        background.rows.add(Row.value(
                "Background FPS",
                "Frame-rate limit while Minecraft is running in the background.",
                state.unfocusedFpsLimit() + " FPS",
                () -> openPerformanceIntegerChoice(
                        "Background FPS",
                        state.unfocusedFpsLimit(),
                        new int[]{15, 30, 45, 60, 90, 120},
                        value -> updatePerformance(current -> current.withUnfocusedFpsLimit(value))
                )
        ));
        background.rows.add(Row.value(
                "Minimized FPS",
                "Frame-rate limit while the Minecraft window is minimized.",
                state.minimizedFpsLimit() + " FPS",
                () -> openPerformanceIntegerChoice(
                        "Minimized FPS",
                        state.minimizedFpsLimit(),
                        new int[]{5, 10, 15, 30},
                        value -> updatePerformance(current -> current.withMinimizedFpsLimit(value))
                )
        ));
        sections.add(background);

        Section optimization = new Section("OPTIMIZATION");
        optimization.rows.add(Row.toggle(
                "Skip Hidden Objects",
                "Avoid drawing supported entities and special blocks when they are fully hidden.",
                state.hiddenObjectSkipping(),
                enabled -> updatePerformance(value -> value.withHiddenObjectSkipping(enabled))
        ));
        optimization.rows.add(Row.toggle(
                "Optimized World Rendering",
                "Use LazyBuilder's optimized world-rendering path when compatible.",
                state.renderingOptimizations(),
                enabled -> updatePerformance(value -> value.withRenderingOptimizations(enabled))
        ));
        optimization.rows.add(Row.toggle(
                "Memory Optimization",
                "Reuse compatible rendering data to reduce memory pressure.",
                state.memoryOptimizations(),
                enabled -> updatePerformance(value -> value.withMemoryOptimizations(enabled))
        ));
        sections.add(optimization);
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

        if (client != null) {
            Section interfaceScale = new Section("INTERFACE");
            interfaceScale.rows.add(Row.value(
                    "GUI Scale",
                    "Adjust the size of Minecraft menus, HUD elements, and text.",
                    client.options.getGuiScale().getValue() == 0 ? "Auto" : Integer.toString(client.options.getGuiScale().getValue()),
                    () -> openIntegerChoice(
                            "GUI Scale",
                            client.options.getGuiScale(),
                            new int[]{0, 1, 2, 3, 4},
                            value -> value == 0 ? "Auto" : Integer.toString(value)
                    )
            ));
            sections.add(interfaceScale);
        }

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
        int viewportHeight = Math.max(1, viewportBottom - contentTop());
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        int baseY = contentTop();

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

                if (row.y + ROW_HEIGHT > viewportTop() && row.y < viewportBottom) {
                    if (row.slider != null) {
                        LazyBuilderSettingsSliderWidget sliderWidget = new LazyBuilderSettingsSliderWidget(
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
                        );
                        row.controlWidget = sliderWidget;
                        this.addDrawableChild(sliderWidget);
                    } else {
                        LazyBuilderSettingsControlWidget controlWidget = new LazyBuilderSettingsControlWidget(
                                controlX,
                                controlY,
                                controlWidth,
                                CONTROL_HEIGHT,
                                Text.literal(row.controlLabel()),
                                row.interactive(),
                                row.kind,
                                row.action()
                        );
                        row.controlWidget = controlWidget;
                        this.addDrawableChild(controlWidget);
                    }

                    if (dropdown != null && dropdown.anchor.equals(row.title)) {
                        dropdown.position(
                                controlX,
                                controlY,
                                controlWidth,
                                viewportTop(),
                                viewportBottom
                        );
                    }
                }

                baseY += ROW_HEIGHT + ROW_GAP;
            }

            baseY += SECTION_GAP;
        }

        suppressWidgetsBehindDropdown();
    }

    private void suppressWidgetsBehindDropdown() {
        if (dropdown == null || !dropdown.positioned()) return;

        int popupLeft = dropdown.x;
        int popupTop = dropdown.y;
        int popupRight = popupLeft + dropdown.width;
        int popupBottom = dropdown.bottom();

        for (Element element : children()) {
            if (!(element instanceof ClickableWidget widget)) continue;

            int left = widget.getX();
            int top = widget.getY();
            int right = left + widget.getWidth();
            int bottom = top + widget.getHeight();

            boolean overlaps = left < popupRight
                    && right > popupLeft
                    && top < popupBottom
                    && bottom > popupTop;
            if (overlaps) {
                widget.visible = false;
            }
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

        if (category == Category.INTERFACE) {
            this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                    left + 8,
                    y,
                    92,
                    22,
                    Text.literal("Reset"),
                    true,
                    LazyBuilderSettingsControlWidget.Kind.FOOTER,
                    this::confirmResetCurrentCategory
            ));
        }

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
            client.setScreen(new LazyBuilderSettingsScreen(parent, next, VideoPage.QUALITY));
        }
    }

    private void openVideoPage(VideoPage next) {
        if (client != null && category == Category.VIDEO && next != videoPage) {
            client.setScreen(new LazyBuilderSettingsScreen(parent, Category.VIDEO, next));
        }
    }

    private void refreshCategory() {
        if (client != null) {
            client.setScreen(new LazyBuilderSettingsScreen(parent, category, videoPage));
        }
    }

    private GraphicsPreset detectGraphicsPreset() {
        if (client == null) return GraphicsPreset.CUSTOM;
        for (GraphicsPreset preset : List.of(GraphicsPreset.LOW, GraphicsPreset.MEDIUM, GraphicsPreset.HIGH)) {
            if (matchesGraphicsPreset(preset)) return preset;
        }
        return GraphicsPreset.CUSTOM;
    }

    private boolean matchesGraphicsPreset(GraphicsPreset preset) {
        if (client == null || preset == GraphicsPreset.CUSTOM) return false;
        return client.options.getViewDistance().getValue() == preset.renderDistance
                && client.options.getSimulationDistance().getValue() == preset.simulationDistance
                && Math.abs(client.options.getEntityDistanceScaling().getValue() - preset.entityDistance) < 0.001
                && client.options.getGraphicsMode().getValue() == preset.graphicsMode
                && client.options.getCloudRenderMode().getValue() == preset.cloudMode
                && client.options.getParticles().getValue() == preset.particlesMode
                && client.options.getMipmapLevels().getValue() == preset.mipmapLevels;
    }

    private void openGraphicsPresetChoice() {
        GraphicsPreset current = detectGraphicsPreset();
        List<DropdownChoice> choices = new ArrayList<>();
        for (GraphicsPreset preset : List.of(GraphicsPreset.LOW, GraphicsPreset.MEDIUM, GraphicsPreset.HIGH)) {
            choices.add(new DropdownChoice(preset.label, preset == current, () -> applyGraphicsPreset(preset)));
        }
        openDropdown("Quality Preset", choices);
    }

    private void applyGraphicsPreset(GraphicsPreset preset) {
        if (client == null || preset == GraphicsPreset.CUSTOM) return;
        client.options.getViewDistance().setValue(preset.renderDistance);
        client.options.getSimulationDistance().setValue(preset.simulationDistance);
        client.options.getEntityDistanceScaling().setValue(preset.entityDistance);
        client.options.getGraphicsMode().setValue(preset.graphicsMode);
        client.options.getCloudRenderMode().setValue(preset.cloudMode);
        client.options.getParticles().setValue(preset.particlesMode);
        client.options.getMipmapLevels().setValue(preset.mipmapLevels);
        client.options.write();
        client.options.sendClientSettings();
        refreshCategory();
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

    private PerformanceState performanceState() {
        Object shared = FabricLoader.getInstance().getObjectShare()
                .get("lazybuilder-performance-manager:settings-snapshot");
        if (!(shared instanceof Supplier<?> supplier)) return null;

        Object value = supplier.get();
        if (!(value instanceof Map<?, ?> values)) return null;

        return new PerformanceState(
                booleanValue(values, "backgroundFpsPolicy", true),
                intValue(values, "unfocusedFpsLimit", 30),
                intValue(values, "minimizedFpsLimit", 10),
                booleanValue(values, "hiddenObjectSkipping", false),
                booleanValue(values, "renderingOptimizations", true),
                booleanValue(values, "memoryOptimizations", true)
        );
    }

    @SuppressWarnings("unchecked")
    private void updatePerformance(Function<PerformanceState, PerformanceState> update) {
        PerformanceState current = performanceState();
        if (current == null) return;

        Object shared = FabricLoader.getInstance().getObjectShare()
                .get("lazybuilder-performance-manager:settings-update");
        if (!(shared instanceof Consumer<?> rawConsumer)) return;

        Consumer<Map<String, Object>> consumer = (Consumer<Map<String, Object>>) rawConsumer;
        PerformanceState updated = update.apply(current);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("backgroundFpsPolicy", updated.backgroundFpsPolicy());
        values.put("unfocusedFpsLimit", updated.unfocusedFpsLimit());
        values.put("minimizedFpsLimit", updated.minimizedFpsLimit());
        values.put("hiddenObjectSkipping", updated.hiddenObjectSkipping());
        values.put("renderingOptimizations", updated.renderingOptimizations());
        values.put("memoryOptimizations", updated.memoryOptimizations());
        consumer.accept(Map.copyOf(values));
        refreshCategory();
    }

    private static boolean booleanValue(Map<?, ?> values, String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    private static int intValue(Map<?, ?> values, String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private void openPerformanceIntegerChoice(
            String title,
            int current,
            int[] values,
            java.util.function.IntConsumer setter
    ) {
        List<DropdownChoice> choices = new ArrayList<>();
        for (int value : values) {
            choices.add(new DropdownChoice(
                    value + " FPS",
                    value == current,
                    () -> setter.accept(value)
            ));
        }
        openDropdown(title, choices);
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

        String heading = category == Category.VIDEO
                ? "SETTINGS  /  VIDEO  /  " + videoPage.label.toUpperCase(Locale.ROOT)
                : "SETTINGS  /  " + category.label.toUpperCase(Locale.ROOT);
        context.drawTextWithShadow(textRenderer, Text.literal(heading), shellLeft() + 8, 15, TEXT_PRIMARY);
        if (hasContextPane()) {
            context.fill(panelRight + 14, viewportTop(), panelRight + 15, height - FOOTER_HEIGHT - 10, DIVIDER);
        }

        Row highlightedRow = null;
        context.enableScissor(panelLeft, viewportTop(), panelRight, viewportBottom());
        for (Section section : sections) {
            if (section.y + SECTION_HEIGHT > viewportTop() && section.y < viewportBottom()) {
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(section.title),
                        panelLeft,
                        section.y + 3,
                        TEXT_SECONDARY
                );
            }

            for (Row row : section.rows) {
                if (row.y + ROW_HEIGHT <= viewportTop() || row.y >= viewportBottom()) continue;

                boolean hovered = mouseX >= row.x && mouseX < row.x + row.width
                        && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
                if (hovered || (row.controlWidget != null && row.controlWidget.isFocused())) {
                    highlightedRow = row;
                }

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
        renderContextPane(context, panelRight + 30, highlightedRow);
        super.render(context, mouseX, mouseY, delta);
        // Flush widget/text render layers before painting the popup so values from
        // rows behind the dropdown cannot bleed through due to batched GUI layers.
        context.draw();
        renderDropdown(context, mouseX, mouseY);
    }

    private void renderContextPane(DrawContext context, int x, Row highlightedRow) {
        if (!hasContextPane()) return;
        int available = shellLeft() + shellWidth() - x - 8;
        if (available < 120) return;

        String title;
        String description;
        if (highlightedRow != null) {
            title = highlightedRow.title.toUpperCase(Locale.ROOT);
            description = highlightedRow.description;
        } else {
            title = category.label.toUpperCase(Locale.ROOT);
            description = switch (category) {
                case VIDEO -> switch (videoPage) {
                    case DISPLAY -> "Window, frame pacing, and screen visibility settings.";
                    case QUALITY -> "Visual detail settings. Start with a preset, then fine-tune only when needed.";
                    case VIEW -> "World distance and camera settings.";
                    case PERFORMANCE -> "Efficiency controls that stay separate from visual-quality presets.";
                };
                case CONTROLS -> "Mouse, movement and all registered key bindings.";
                case INTERFACE -> "Builder-facing HUD, screenshot and Creative-mode preferences.";
                case TOOLS -> "Tool-specific setup stays close to the workflow that owns it.";
            };
        }

        int contextY = category == Category.VIDEO ? 114 : 84;
        context.drawTextWithShadow(textRenderer, Text.literal(title), x, contextY, TEXT_PRIMARY);
        int y = contextY + 18;
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
        int bottom = dropdown.bottom();

        context.fill(left - 1, top - 1, right + 1, bottom + 1, 0xAA4F5964);
        context.fill(left, top, right, bottom, 0xFF151A20);

        for (int visibleIndex = 0; visibleIndex < dropdown.visibleCount; visibleIndex++) {
            int choiceIndex = dropdown.firstVisible + visibleIndex;
            DropdownChoice choice = dropdown.choices.get(choiceIndex);
            int itemY = top + visibleIndex * DropdownState.ITEM_HEIGHT;
            boolean hovered = mouseX >= left && mouseX < right
                    && mouseY >= itemY && mouseY < itemY + DropdownState.ITEM_HEIGHT;

            int fill = choice.selected()
                    ? 0xCC1F6558
                    : hovered ? 0xFF252C34 : 0xFF1A1F25;
            context.fill(left, itemY, right, itemY + DropdownState.ITEM_HEIGHT - 1, fill);
            context.fill(left, itemY + DropdownState.ITEM_HEIGHT - 1, right, itemY + DropdownState.ITEM_HEIGHT, DIVIDER);

            int textColor = choice.selected() || hovered ? TEXT_PRIMARY : TEXT_SECONDARY;
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(textRenderer.trimToWidth(choice.label(), dropdown.width - 30)),
                    left + 7,
                    itemY + 6,
                    textColor
            );

            if (choice.selected()) {
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal("✓"),
                        right - 14,
                        itemY + 6,
                        ACCENT
                );
            }
        }

        if (dropdown.visibleCount < dropdown.choices.size()) {
            int trackTop = top + 3;
            int trackBottom = bottom - 3;
            int trackHeight = Math.max(1, trackBottom - trackTop);
            int thumbHeight = Math.max(10, trackHeight * dropdown.visibleCount / dropdown.choices.size());
            int maxFirst = Math.max(1, dropdown.choices.size() - dropdown.visibleCount);
            int travel = Math.max(1, trackHeight - thumbHeight);
            int thumbY = trackTop + (int) Math.round((dropdown.firstVisible / (double) maxFirst) * travel);
            context.fill(right - 3, trackTop, right - 1, trackBottom, 0x334A525C);
            context.fill(right - 3, thumbY, right - 1, thumbY + thumbHeight, ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dropdown != null && dropdown.positioned()) {
            if (mouseX >= dropdown.x && mouseX < dropdown.x + dropdown.width
                    && mouseY >= dropdown.y && mouseY < dropdown.bottom()) {
                int visibleIndex = (int) ((mouseY - dropdown.y) / DropdownState.ITEM_HEIGHT);
                int index = dropdown.firstVisible + visibleIndex;
                if (visibleIndex >= 0 && visibleIndex < dropdown.visibleCount
                        && index >= 0 && index < dropdown.choices.size()) {
                    DropdownChoice choice = dropdown.choices.get(index);
                    dropdown = null;
                    choice.action().run();
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
        int top = viewportTop();
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
        if (dropdown != null) {
            if (dropdown.positioned()
                    && mouseX >= dropdown.x && mouseX < dropdown.x + dropdown.width
                    && mouseY >= dropdown.y && mouseY < dropdown.bottom()) {
                dropdown.scroll(verticalAmount);
            }
            return true;
        }
        if (maxScroll > 0
                && mouseX >= panelLeft()
                && mouseX <= panelLeft() + panelWidth()
                && mouseY >= viewportTop()
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

    private int contentTop() {
        return category == Category.VIDEO ? VIDEO_CONTENT_TOP : DEFAULT_CONTENT_TOP;
    }

    private int viewportTop() {
        return category == Category.VIDEO ? VIDEO_VIEWPORT_TOP : DEFAULT_VIEWPORT_TOP;
    }

    private int viewportBottom() {
        return Math.max(viewportTop() + 1, height - FOOTER_HEIGHT - 6);
    }

    private int shellWidth() {
        return Math.min(MAX_SHELL_WIDTH, Math.max(280, width - MIN_SIDE_MARGIN * 2));
    }

    private int shellLeft() {
        return (width - shellWidth()) / 2;
    }

    private boolean hasContextPane() {
        return shellWidth() >= 640;
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
        private ClickableWidget controlWidget;

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

    private record PerformanceState(
            boolean backgroundFpsPolicy,
            int unfocusedFpsLimit,
            int minimizedFpsLimit,
            boolean hiddenObjectSkipping,
            boolean renderingOptimizations,
            boolean memoryOptimizations
    ) {
        PerformanceState withBackgroundFpsPolicy(boolean enabled) {
            return new PerformanceState(
                    enabled,
                    unfocusedFpsLimit,
                    minimizedFpsLimit,
                    hiddenObjectSkipping,
                    renderingOptimizations,
                    memoryOptimizations
            );
        }

        PerformanceState withUnfocusedFpsLimit(int fps) {
            return new PerformanceState(
                    backgroundFpsPolicy,
                    fps,
                    minimizedFpsLimit,
                    hiddenObjectSkipping,
                    renderingOptimizations,
                    memoryOptimizations
            );
        }

        PerformanceState withMinimizedFpsLimit(int fps) {
            return new PerformanceState(
                    backgroundFpsPolicy,
                    unfocusedFpsLimit,
                    fps,
                    hiddenObjectSkipping,
                    renderingOptimizations,
                    memoryOptimizations
            );
        }

        PerformanceState withHiddenObjectSkipping(boolean enabled) {
            return new PerformanceState(
                    backgroundFpsPolicy,
                    unfocusedFpsLimit,
                    minimizedFpsLimit,
                    enabled,
                    renderingOptimizations,
                    memoryOptimizations
            );
        }

        PerformanceState withRenderingOptimizations(boolean enabled) {
            return new PerformanceState(
                    backgroundFpsPolicy,
                    unfocusedFpsLimit,
                    minimizedFpsLimit,
                    hiddenObjectSkipping,
                    enabled,
                    memoryOptimizations
            );
        }

        PerformanceState withMemoryOptimizations(boolean enabled) {
            return new PerformanceState(
                    backgroundFpsPolicy,
                    unfocusedFpsLimit,
                    minimizedFpsLimit,
                    hiddenObjectSkipping,
                    renderingOptimizations,
                    enabled
            );
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
        private int firstVisible;
        private int visibleCount;

        private DropdownState(String anchor, List<DropdownChoice> choices) {
            this.anchor = anchor;
            this.choices = List.copyOf(choices);
        }

        private void position(
                int x,
                int controlY,
                int width,
                int viewportTop,
                int viewportBottom
        ) {
            this.x = x;
            this.width = width;

            int viewportHeight = Math.max(ITEM_HEIGHT * 2, viewportBottom - viewportTop);
            int maxVisible = Math.max(2, viewportHeight / ITEM_HEIGHT);
            this.visibleCount = Math.min(choices.size(), maxVisible);

            int selected = selectedIndex();
            int maxFirst = Math.max(0, choices.size() - visibleCount);
            if (selected >= 0 && (selected < firstVisible || selected >= firstVisible + visibleCount)) {
                firstVisible = Math.max(0, Math.min(maxFirst, selected - visibleCount / 2));
            } else {
                firstVisible = Math.max(0, Math.min(maxFirst, firstVisible));
            }

            int popupHeight = visibleCount * ITEM_HEIGHT;
            int below = controlY + CONTROL_HEIGHT + 2;
            int above = controlY - popupHeight - 2;
            if (below + popupHeight <= viewportBottom) {
                this.y = below;
            } else if (above >= viewportTop) {
                this.y = above;
            } else {
                this.y = Math.max(viewportTop, Math.min(viewportBottom - popupHeight, below));
            }
        }

        private int selectedIndex() {
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).selected()) return i;
            }
            return -1;
        }

        private void scroll(double verticalAmount) {
            if (visibleCount >= choices.size()) return;
            int maxFirst = choices.size() - visibleCount;
            int delta = verticalAmount > 0 ? -1 : verticalAmount < 0 ? 1 : 0;
            firstVisible = Math.max(0, Math.min(maxFirst, firstVisible + delta));
        }

        private boolean positioned() {
            return width > 0 && visibleCount > 0;
        }

        private int bottom() {
            return y + visibleCount * ITEM_HEIGHT;
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

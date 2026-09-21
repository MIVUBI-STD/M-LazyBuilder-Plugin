package com.halokaryamedia.lazybuilder.utility.ui;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.UtilityPreferences;
import com.halokaryamedia.lazybuilder.utility.notification.UtilityNotifications;
import com.halokaryamedia.lazybuilder.utility.mixin.SimpleOptionAccessor;
import com.halokaryamedia.lazybuilder.utility.window.BorderlessWindowController;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.NarratorMode;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.sound.SoundCategory;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;
import net.minecraft.resource.ResourcePackProfile;

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
        AUDIO("Audio"),
        CONTROLS("Controls"),
        CHAT("Chat"),
        INTERFACE("Interface"),
        ACCESSIBILITY("Accessibility"),
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
        PERFORMANCE("Performance"),
        VISUAL("Visual");

        private final String label;

        VideoPage(String label) {
            this.label = label;
        }

        Text text() {
            return Text.literal(label);
        }
    }

    private enum GraphicsPreset {
        LOW("Low", GraphicsMode.FAST, CloudRenderMode.OFF, ParticlesMode.MINIMAL, 1),
        MEDIUM("Medium", GraphicsMode.FANCY, CloudRenderMode.FAST, ParticlesMode.DECREASED, 3),
        HIGH("High", GraphicsMode.FANCY, CloudRenderMode.FANCY, ParticlesMode.ALL, 4),
        CUSTOM("Custom", GraphicsMode.FANCY, CloudRenderMode.FANCY, ParticlesMode.ALL, -1);

        private final String label;
        private final GraphicsMode graphicsMode;
        private final CloudRenderMode cloudMode;
        private final ParticlesMode particlesMode;
        private final int mipmapLevels;

        GraphicsPreset(
                String label,
                GraphicsMode graphicsMode,
                CloudRenderMode cloudMode,
                ParticlesMode particlesMode,
                int mipmapLevels
        ) {
            this.label = label;
            this.graphicsMode = graphicsMode;
            this.cloudMode = cloudMode;
            this.particlesMode = particlesMode;
            this.mipmapLevels = mipmapLevels;
        }
    }

    private enum WindowMode {
        WINDOWED("Windowed"),
        BORDERLESS("Borderless"),
        FULLSCREEN("Fullscreen");

        private final String label;

        WindowMode(String label) {
            this.label = label;
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
    private final String focusTarget;
    private final List<Section> sections = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;
    private int contextlessFooterLegendX = -1;
    private DropdownState dropdown;

    public LazyBuilderSettingsScreen(Screen parent) {
        this(parent, Category.VIDEO, VideoPage.QUALITY, null);
    }

    public LazyBuilderSettingsScreen(Screen parent, Category category) {
        this(parent, category, VideoPage.QUALITY, null);
    }

    public LazyBuilderSettingsScreen(Screen parent, Category category, VideoPage videoPage) {
        this(parent, category, videoPage, null);
    }

    public LazyBuilderSettingsScreen(
            Screen parent,
            Category category,
            VideoPage videoPage,
            String focusTarget
    ) {
        super(Text.literal("Settings"));
        this.parent = parent;
        this.category = category;
        this.videoPage = videoPage;
        this.focusTarget = focusTarget;
    }

    @Override
    protected void init() {
        sections.clear();
        addTabs();
        if (category == Category.VIDEO) addVideoTabs();

        switch (category) {
            case VIDEO -> buildVideoSections();
            case AUDIO -> buildAudioSections();
            case CONTROLS -> buildControlsSections();
            case CHAT -> buildChatSections();
            case INTERFACE -> buildInterfaceSections();
            case ACCESSIBILITY -> buildAccessibilitySections();
            case TOOLS -> buildToolsSections();
        }

        prepareFocusTarget();
        layoutSections();
        addFooter();
    }

    private void addTabs() {
        int left = panelLeft();
        int width = panelWidth();
        Category[] values = Category.values();
        int gap = 2;
        int rows = categoryTabRows();
        int firstRowCount = rows == 1 ? values.length : (values.length + 1) / 2;

        for (int row = 0; row < rows; row++) {
            int start = row == 0 ? 0 : firstRowCount;
            int count = row == 0 ? firstRowCount : values.length - firstRowCount;
            if (count <= 0) continue;

            int x = left;
            int tabWidth = Math.max(1, (width - gap * (count - 1)) / count);
            int y = TAB_TOP + row * (TAB_HEIGHT + gap);

            for (int index = 0; index < count; index++) {
                Category value = values[start + index];
                int actual = index == count - 1 ? left + width - x : tabWidth;
                this.addDrawableChild(new LazyBuilderSettingsTabWidget(
                        x,
                        y,
                        actual,
                        TAB_HEIGHT,
                        value.text(),
                        value == category,
                        () -> openCategory(value)
                ));
                x += actual + gap;
            }
        }
    }

    private void addVideoTabs() {
        int left = panelLeft();
        int width = panelWidth();
        VideoPage[] values = VideoPage.values();
        int gap = 2;
        int rows = videoTabRows();
        int firstRowCount = rows == 1 ? values.length : (values.length + 1) / 2;

        for (int row = 0; row < rows; row++) {
            int start = row == 0 ? 0 : firstRowCount;
            int count = row == 0 ? firstRowCount : values.length - firstRowCount;
            if (count <= 0) continue;

            int x = left;
            int tabWidth = Math.max(1, (width - gap * (count - 1)) / count);
            int y = videoTabTop() + row * (TAB_HEIGHT + gap);

            for (int index = 0; index < count; index++) {
                VideoPage value = values[start + index];
                int actual = index == count - 1 ? left + width - x : tabWidth;
                this.addDrawableChild(new LazyBuilderSettingsTabWidget(
                        x,
                        y,
                        actual,
                        TAB_HEIGHT,
                        value.text(),
                        value == videoPage,
                        () -> openVideoPage(value)
                ));
                x += actual + gap;
            }
        }
    }

    private void buildVideoSections() {
        if (client == null) return;
        switch (videoPage) {
            case DISPLAY -> buildVideoDisplaySections();
            case QUALITY -> buildVideoQualitySections();
            case VIEW -> buildVideoViewSections();
            case PERFORMANCE -> buildVideoPerformanceSections();
            case VISUAL -> buildVideoVisualSections();
        }
    }

    private void buildVideoDisplaySections() {
        Section display = new Section("DISPLAY");
        display.rows.add(Row.value(
                "Window Mode",
                "Choose Windowed, Borderless, or Fullscreen. Borderless mode is applied on the next game launch.",
                windowModeDisplayLabel(),
                this::openWindowModeChoice
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

        Section overall = new Section("PRESET");
        overall.rows.add(Row.value(
                "Graphics Preset",
                "Set visual detail and the matching LazyBuilder optimization level together. Manual overrides change the preset to Custom.",
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
                    "Performance Features",
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

        Section optimization = new Section("ADVANCED PERFORMANCE");
        optimization.rows.add(Row.toggle(
                "Skip Hidden Objects",
                "Skip supported objects only when LazyBuilder can confirm they are fully hidden. Low enables this automatically.",
                state.hiddenObjectSkipping(),
                enabled -> updatePerformance(value -> value.withHiddenObjectSkipping(enabled))
        ));
        optimization.rows.add(Row.toggle(
                "Optimized World Rendering",
                "Keep LazyBuilder rendering optimizations enabled when the active renderer is compatible. Managed automatically by presets.",
                state.renderingOptimizations(),
                enabled -> updatePerformance(value -> value.withRenderingOptimizations(enabled))
        ));
        optimization.rows.add(Row.toggle(
                "Memory Optimization",
                "Reduce avoidable rendering-memory use without lowering visual detail. Managed automatically by presets.",
                state.memoryOptimizations(),
                enabled -> updatePerformance(value -> value.withMemoryOptimizations(enabled))
        ));
        sections.add(optimization);
    }

    private void buildVideoVisualSections() {
        Section resourcePacks = new Section("RESOURCE PACK");
        resourcePacks.rows.add(Row.action(
                "Resource Packs",
                "Manage available and active Resource Packs. Higher active packs take priority over packs below them.",
                resourcePackSummary(),
                this::openResourcePackManager
        ));
        sections.add(resourcePacks);

        Section shaders = new Section("SHADER");
        if (shaderSupportAvailable()) {
            shaders.rows.add(Row.action(
                    "Shaders",
                    "Manage LazyBuilder shader packs, compilation, post-processing, and terrain shader integration.",
                    shaderSummary(),
                    this::openShaderManager
            ));
        } else {
            shaders.rows.add(Row.status(
                    "Shaders",
                    "First-party shader runtime is unavailable in this client package.",
                    "Unavailable"
            ));
        }
        sections.add(shaders);
    }

    private String resourcePackSummary() {
        if (client == null) return "Default";
        List<String> selected = client.options.resourcePacks;
        if (selected == null || selected.isEmpty()) return "Default";

        if (selected.size() == 1) {
            ResourcePackProfile profile = client.getResourcePackManager().getProfile(selected.getFirst());
            if (profile != null) {
                String name = profile.getDisplayName().getString().trim();
                if (!name.isEmpty()) return name;
            }
            return "1 Active";
        }

        return selected.size() + " Active";
    }

    private void openResourcePackManager() {
        if (client == null) return;
        client.setScreen(new LazyBuilderResourcePackScreen(
                this,
                client.getResourcePackManager(),
                manager -> {
                    UtilityNotifications.show(
                            "Resource Packs",
                            "Applying resource pack changes..."
                    );
                    client.options.refreshResourcePacks(manager);
                    client.reloadResources().whenComplete((ignored, error) -> {
                        if (error != null) {
                            UtilityNotifications.show(
                                    "Resource Packs",
                                    "Could not apply resource pack changes. Previous resources remain in use where possible."
                            );
                        }
                    });
                },
                client.getResourcePackDir()
        ));
    }

    private static boolean shaderSupportAvailable() {
        Object shared = FabricLoader.getInstance().getObjectShare()
                .get("lazybuilder-performance-manager:shader-snapshot");
        return shared instanceof Supplier<?>;
    }

    private String shaderSummary() {
        Object shared = FabricLoader.getInstance().getObjectShare()
                .get("lazybuilder-performance-manager:shader-snapshot");
        if (!(shared instanceof Supplier<?> supplier)) {
            return FabricLoader.getInstance().isModLoaded("iris")
                    ? "Iris Compatibility"
                    : "Unavailable";
        }

        Object value = supplier.get();
        if (!(value instanceof Map<?, ?> map)) return "Available";

        boolean terrain = booleanValue(map, "terrainIntegrated", false);
        boolean rendering = booleanValue(map, "renderingReady", false);
        boolean compiled = booleanValue(map, "compiledReady", false);
        String active = stringValue(map, "activePackName", "");
        String selected = stringValue(map, "selectedPackName", "");
        String stage = stringValue(map, "stage", "");

        if (terrain && !active.isBlank()) return active;
        if (rendering && !active.isBlank()) return active + " (Post)";
        if (compiled && !active.isBlank()) return active + " (Compiled)";
        if (!selected.isBlank()) return selected;
        if ("compile-error".equals(stage) || "render-error".equals(stage)) return "Error";
        return "Off";
    }

    private static String stringValue(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String text ? text : fallback;
    }

    private void openShaderManager() {
        if (client == null) return;
        client.setScreen(new LazyBuilderShaderScreen(this));
    }

    private void buildAudioSections() {
        if (client == null) return;

        Section main = new Section("VOLUME");
        main.rows.add(soundSlider(
                "Master Volume",
                "Controls the overall game volume.",
                SoundCategory.MASTER
        ));
        sections.add(main);

        Section mix = new Section("SOUND MIX");
        mix.rows.add(soundSlider("Music", "Controls background music.", SoundCategory.MUSIC));
        mix.rows.add(soundSlider("Jukebox & Note Blocks", "Controls jukeboxes and note blocks.", SoundCategory.RECORDS));
        mix.rows.add(soundSlider("Weather", "Controls rain, thunder, and weather sounds.", SoundCategory.WEATHER));
        mix.rows.add(soundSlider("Blocks", "Controls block interaction and environment sounds.", SoundCategory.BLOCKS));
        mix.rows.add(soundSlider("Hostile Creatures", "Controls hostile creature sounds.", SoundCategory.HOSTILE));
        mix.rows.add(soundSlider("Friendly Creatures", "Controls passive and neutral creature sounds.", SoundCategory.NEUTRAL));
        mix.rows.add(soundSlider("Players", "Controls player-related sounds.", SoundCategory.PLAYERS));
        mix.rows.add(soundSlider("Ambient", "Controls ambient world sounds.", SoundCategory.AMBIENT));
        mix.rows.add(soundSlider("Voice / Speech", "Controls voice and speech sounds.", SoundCategory.VOICE));
        sections.add(mix);
    }

    private Row soundSlider(String title, String description, SoundCategory category) {
        var option = client.options.getSoundVolumeOption(category);
        return Row.slider(
                title,
                description,
                option.getValue(),
                0.0,
                1.0,
                0.01,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(option, value)
        );
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

    private void buildChatSections() {
        if (client == null) return;

        UtilityPreferences prefs = UtilityManagerClient.preferences();

        Section general = new Section("GENERAL");
        general.rows.add(Row.toggle(
                "Keep Unsent Message",
                "Keep unfinished chat text when the chat screen is closed without sending.",
                prefs.keepChatDraft(),
                enabled -> updateUtilityPreference(value -> value.withKeepChatDraft(enabled))
        ));
        general.rows.add(Row.toggle(
                "Search Chat",
                "Enable Ctrl+F search through chat history while the chat screen is open.",
                prefs.chatSearch(),
                enabled -> updateUtilityPreference(value -> value.withChatSearch(enabled))
        ));
        general.rows.add(Row.toggle(
                "Extended History",
                "Keep a longer local chat history for navigation and search during the current session.",
                prefs.extendedChatHistory(),
                enabled -> updateUtilityPreference(value -> value.withExtendedChatHistory(enabled))
        ));
        general.rows.add(Row.toggle(
                "Timestamps",
                "Show local timestamps beside chat messages.",
                prefs.chatTimestamps(),
                enabled -> updateUtilityPreference(value -> value.withChatTimestamps(enabled))
        ));
        sections.add(general);

        Section appearance = new Section("APPEARANCE");
        appearance.rows.add(Row.slider(
                "Chat Opacity",
                "Adjust chat message opacity.",
                client.options.getChatOpacity().getValue(),
                0.0,
                1.0,
                0.05,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(client.options.getChatOpacity(), value)
        ));
        appearance.rows.add(Row.slider(
                "Chat Scale",
                "Adjust the overall size of the chat panel.",
                client.options.getChatScale().getValue(),
                0.0,
                1.0,
                0.05,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(client.options.getChatScale(), value)
        ));
        appearance.rows.add(Row.slider(
                "Chat Width",
                "Adjust the width of the chat panel.",
                client.options.getChatWidth().getValue(),
                0.0,
                1.0,
                0.05,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(client.options.getChatWidth(), value)
        ));
        appearance.rows.add(Row.slider(
                "Line Spacing",
                "Adjust spacing between chat lines.",
                client.options.getChatLineSpacing().getValue(),
                0.0,
                1.0,
                0.05,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(client.options.getChatLineSpacing(), value)
        ));
        sections.add(appearance);

        Section privacy = new Section("MESSAGE CONTROLS");
        privacy.rows.add(Row.toggle(
                "Show Signing Indicators",
                "Show Minecraft's message signing indicators when they are available.",
                !prefs.hideChatSigningIndicators(),
                enabled -> updateUtilityPreference(value -> value.withHideChatSigningIndicators(!enabled))
        ));
        privacy.rows.add(Row.toggle(
                "Show Report Button",
                "Show Minecraft's built-in report action in supported social and chat surfaces.",
                !prefs.hideChatReportButton(),
                enabled -> updateUtilityPreference(value -> value.withHideChatReportButton(!enabled))
        ));
        sections.add(privacy);
    }

    private void updateUtilityPreference(java.util.function.UnaryOperator<UtilityPreferences> updater) {
        UtilityManagerClient.updatePreferences(updater.apply(UtilityManagerClient.preferences()));
        refreshCategory();
    }

    private String windowModeDisplayLabel() {
        WindowMode desired = currentWindowMode();
        if (desired == WindowMode.FULLSCREEN) return desired.label;

        boolean runtimeBorderless = BorderlessWindowController.isApplied();
        boolean wantsBorderless = desired == WindowMode.BORDERLESS;
        if (runtimeBorderless != wantsBorderless) {
            return desired.label + " (Restart)";
        }
        return desired.label;
    }

    private WindowMode currentWindowMode() {
        if (client != null && client.options.getFullscreen().getValue()) return WindowMode.FULLSCREEN;
        return UtilityManagerClient.preferences().borderlessWindow()
                ? WindowMode.BORDERLESS
                : WindowMode.WINDOWED;
    }

    private void openWindowModeChoice() {
        WindowMode current = currentWindowMode();
        List<DropdownChoice> choices = new ArrayList<>();
        for (WindowMode mode : WindowMode.values()) {
            choices.add(new DropdownChoice(
                    mode.label,
                    mode == current,
                    () -> applyWindowMode(mode)
            ));
        }
        openDropdown("Window Mode", choices);
    }

    private void applyWindowMode(WindowMode mode) {
        if (client == null) return;

        WindowMode previous = currentWindowMode();
        boolean previousFullscreen = client.options.getFullscreen().getValue();
        boolean previousBorderless = UtilityManagerClient.preferences().borderlessWindow();

        if (mode == WindowMode.BORDERLESS) {
            UtilityManagerClient.updatePreferences(
                    UtilityManagerClient.preferences().withBorderlessWindow(true)
            );
            if (previousFullscreen) {
                client.options.getFullscreen().setValue(false);
                client.options.write();
            }
            UtilityNotifications.show(
                    "Window Mode",
                    "Borderless mode will be applied on the next game launch."
            );
            refreshCategory();
            return;
        }

        UtilityManagerClient.updatePreferences(
                UtilityManagerClient.preferences().withBorderlessWindow(false)
        );

        boolean fullscreen = mode == WindowMode.FULLSCREEN;
        client.options.getFullscreen().setValue(fullscreen);
        client.options.write();

        if (previousFullscreen == fullscreen && !previousBorderless) {
            refreshCategory();
            return;
        }

        LazyBuilderSettingsScreen returnScreen = new LazyBuilderSettingsScreen(
                parent,
                Category.VIDEO,
                VideoPage.DISPLAY,
                "Window Mode"
        );
        client.setScreen(new LazyBuilderDisplayConfirmScreen(
                returnScreen,
                () -> {
                    if (client != null) {
                        client.options.getFullscreen().setValue(fullscreen);
                        client.options.write();
                    }
                },
                () -> {
                    if (client == null) return;
                    UtilityManagerClient.updatePreferences(
                            UtilityManagerClient.preferences().withBorderlessWindow(previousBorderless)
                    );
                    client.options.getFullscreen().setValue(previousFullscreen);
                    client.options.write();
                }
        ));
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
            interfaceScale.rows.add(Row.action(
                    "Language",
                    "Choose the language used by Minecraft menus and translated game text.",
                    currentLanguageLabel(),
                    () -> {
                        if (client != null) client.setScreen(new LazyBuilderLanguageScreen(this));
                    }
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

        Section multiplayer = new Section("MULTIPLAYER");
        multiplayer.rows.add(Row.toggle(
                "Reconnect Button",
                "Show a reconnect action after leaving or losing connection to a multiplayer server.",
                prefs.reconnectButton(),
                enabled -> updateInterface(prefs.withReconnectButton(enabled))
        ));
        sections.add(multiplayer);
    }

    private String currentLanguageLabel() {
        if (client == null) return "Unavailable";
        String code = client.getLanguageManager().getLanguage();
        var definition = client.getLanguageManager().getLanguage(code);
        if (definition == null) return code;
        String name = definition.getName();
        return name == null || name.isBlank() ? code : name;
    }

    private void buildAccessibilitySections() {
        if (client == null) return;

        UtilityPreferences prefs = UtilityManagerClient.preferences();

        Section hearing = new Section("HEARING");
        hearing.rows.add(Row.toggle(
                "Subtitles",
                "Show directional text captions for nearby sounds.",
                client.options.getShowSubtitles().getValue(),
                value -> setOption(client.options.getShowSubtitles(), value)
        ));
        sections.add(hearing);

        Section narrator = new Section("NARRATOR");
        narrator.rows.add(Row.toggle(
                "Keep Narrator Off",
                "Keep Minecraft's narrator disabled and disable the narrator shortcut.",
                prefs.suppressNarrator(),
                enabled -> updateNarratorSuppression(enabled)
        ));
        if (!prefs.suppressNarrator()) {
            narrator.rows.add(Row.value(
                    "Narrator Mode",
                    "Choose which interface or chat text Minecraft reads aloud.",
                    humanize(client.options.getNarrator().getValue()),
                    this::openNarratorChoice
            ));
        }
        sections.add(narrator);

        Section readability = new Section("READABILITY");
        readability.rows.add(Row.slider(
                "Text Background",
                "Adjust the background opacity behind readable text.",
                client.options.getTextBackgroundOpacity().getValue(),
                0.0,
                1.0,
                0.05,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(client.options.getTextBackgroundOpacity(), value)
        ));
        readability.rows.add(Row.toggle(
                "Chat Background Only",
                "Limit text background styling to chat instead of other text surfaces.",
                client.options.getBackgroundForChatOnly().getValue(),
                value -> setOption(client.options.getBackgroundForChatOnly(), value)
        ));
        sections.add(readability);

        Section comfort = new Section("VISUAL COMFORT");
        comfort.rows.add(Row.slider(
                "FOV Effects",
                "Reduce camera field-of-view changes caused by movement and gameplay effects.",
                client.options.getFovEffectScale().getValue(),
                0.0,
                1.0,
                0.05,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(client.options.getFovEffectScale(), value)
        ));
        comfort.rows.add(Row.slider(
                "Distortion Effects",
                "Reduce screen distortion from portals and similar effects.",
                client.options.getDistortionEffectScale().getValue(),
                0.0,
                1.0,
                0.05,
                LazyBuilderSettingsScreen::percentage,
                value -> setOptionLive(client.options.getDistortionEffectScale(), value)
        ));
        comfort.rows.add(Row.toggle(
                "Hide Lightning Flashes",
                "Reduce bright lightning flashes during storms.",
                client.options.getHideLightningFlashes().getValue(),
                value -> setOption(client.options.getHideLightningFlashes(), value)
        ));
        comfort.rows.add(Row.toggle(
                "Monochrome Logo",
                "Use Minecraft's monochrome logo presentation where supported.",
                client.options.getMonochromeLogo().getValue(),
                value -> setOption(client.options.getMonochromeLogo(), value)
        ));
        sections.add(comfort);
    }

    private void updateNarratorSuppression(boolean enabled) {
        UtilityPreferences updated = UtilityManagerClient.preferences().withSuppressNarrator(enabled);
        UtilityManagerClient.updatePreferences(updated);
        if (enabled && client != null) {
            client.options.getNarrator().setValue(NarratorMode.OFF);
            client.options.getNarratorHotkey().setValue(false);
            client.options.write();
        }
        refreshCategory();
    }

    private void openNarratorChoice() {
        if (client == null) return;
        List<DropdownChoice> choices = new ArrayList<>();
        NarratorMode current = client.options.getNarrator().getValue();
        for (NarratorMode value : NarratorMode.values()) {
            choices.add(new DropdownChoice(
                    humanize(value),
                    value == current,
                    () -> setOption(client.options.getNarrator(), value)
            ));
        }
        openDropdown("Narrator Mode", choices);
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

    private void prepareFocusTarget() {
        if (focusTarget == null || focusTarget.isBlank()) return;

        int contentY = 0;
        int targetY = -1;
        for (Section section : sections) {
            contentY += SECTION_HEIGHT;
            for (Row row : section.rows) {
                if (focusTarget.equals(row.title)) {
                    targetY = contentY;
                    break;
                }
                contentY += ROW_HEIGHT + ROW_GAP;
            }
            if (targetY >= 0) break;
            contentY += SECTION_GAP;
        }

        if (targetY < 0) return;

        int viewportHeight = Math.max(1, viewportBottom() - contentTop());
        int contentHeight = totalContentHeight();
        int allowedScroll = Math.max(0, contentHeight - viewportHeight);
        int desired = Math.max(0, targetY - Math.max(24, viewportHeight / 3));
        scrollOffset = Math.min(allowedScroll, desired);
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

        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                left + 8,
                y,
                92,
                22,
                Text.literal("Search"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::openSettingsSearch
        ));

        if (canResetCurrentSurface()) {
            this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                    left + 108,
                    y,
                    108,
                    22,
                    Text.literal(resetButtonLabel()),
                    true,
                    LazyBuilderSettingsControlWidget.Kind.FOOTER,
                    this::confirmResetCurrentCategory
            ));
        }

        if (shellWidth() >= 560) {
            int legendX = left + 228;
            contextlessFooterLegendX = legendX;
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

    private void openSettingsSearch() {
        if (client != null) client.setScreen(new LazyBuilderSettingsSearchScreen(this));
    }

    private boolean canResetCurrentSurface() {
        if (category == Category.VIDEO) {
            return videoPage != VideoPage.VISUAL;
        }
        return category == Category.AUDIO
                || category == Category.CONTROLS
                || category == Category.CHAT
                || category == Category.INTERFACE
                || category == Category.ACCESSIBILITY;
    }

    private String resetButtonLabel() {
        if (category == Category.CONTROLS) return "Reset Input";
        return "Reset";
    }

    private void confirmResetCurrentCategory() {
        if (client == null || !canResetCurrentSurface()) return;

        String title;
        String body;
        if (category == Category.VIDEO) {
            title = "Reset " + videoPage.label;
            body = "Restore " + videoPage.label + " settings to their Minecraft or LazyBuilder defaults?";
        } else if (category == Category.AUDIO) {
            title = "Reset Audio";
            body = "Restore all visible audio levels to Minecraft defaults?";
        } else if (category == Category.CONTROLS) {
            title = "Reset Input";
            body = "Restore mouse and movement settings to Minecraft defaults? Key Bindings will not change.";
        } else if (category == Category.CHAT) {
            title = "Reset Chat";
            body = "Restore Chat helpers and visible chat appearance settings to their defaults?";
        } else if (category == Category.INTERFACE) {
            title = "Reset Interface Layout";
            body = "Restore GUI Scale and LazyBuilder Interface helpers to their defaults? Language will not change.";
        } else if (category == Category.ACCESSIBILITY) {
            title = "Reset Accessibility";
            body = "Restore visible Accessibility settings to their Minecraft or LazyBuilder defaults?";
        } else {
            return;
        }

        client.setScreen(new LazyBuilderConfirmScreen(
                this,
                title,
                body,
                "Reset",
                this::resetCurrentCategory
        ));
    }

    private void resetCurrentCategory() {
        if (client == null) return;

        if (category == Category.VIDEO) {
            switch (videoPage) {
                case DISPLAY -> {
                    if (resetDisplay()) return;
                }
                case QUALITY -> resetQuality();
                case VIEW -> resetView();
                case PERFORMANCE -> resetPerformance();
                case VISUAL -> { return; }
            }
            client.options.write();
            client.options.sendClientSettings();
            refreshCategory();
            return;
        }

        if (category == Category.AUDIO) {
            for (SoundCategory sound : SoundCategory.values()) {
                resetOption(client.options.getSoundVolumeOption(sound));
            }
            client.options.write();
            refreshCategory();
            return;
        }

        if (category == Category.CONTROLS) {
            resetOption(client.options.getMouseSensitivity());
            resetOption(client.options.getInvertYMouse());
            resetOption(client.options.getRawMouseInput());
            resetOption(client.options.getDiscreteMouseScroll());
            resetOption(client.options.getMouseWheelSensitivity());
            resetOption(client.options.getAutoJump());
            resetOption(client.options.getSneakToggled());
            resetOption(client.options.getSprintToggled());
            client.options.write();
            client.options.sendClientSettings();
            refreshCategory();
            return;
        }

        if (category == Category.CHAT) {
            UtilityPreferences defaults = UtilityPreferences.defaults();
            UtilityPreferences current = UtilityManagerClient.preferences()
                    .withExtendedChatHistory(defaults.extendedChatHistory())
                    .withKeepChatDraft(defaults.keepChatDraft())
                    .withChatSearch(defaults.chatSearch())
                    .withChatTimestamps(defaults.chatTimestamps())
                    .withHideChatSigningIndicators(defaults.hideChatSigningIndicators())
                    .withHideChatReportButton(defaults.hideChatReportButton());
            UtilityManagerClient.updatePreferences(current);

            resetOption(client.options.getChatOpacity());
            resetOption(client.options.getChatScale());
            resetOption(client.options.getChatWidth());
            resetOption(client.options.getChatLineSpacing());
            client.options.write();
            client.options.sendClientSettings();
            refreshCategory();
            return;
        }

        if (category == Category.INTERFACE) {
            UtilityPreferences defaults = UtilityPreferences.defaults();
            UtilityPreferences current = UtilityManagerClient.preferences()
                    .withCompactDebugHud(defaults.compactDebugHud())
                    .withContextualScreenshotNames(defaults.contextualScreenshotNames())
                    .withInstantCreativeSearch(defaults.instantCreativeSearch())
                    .withReconnectButton(defaults.reconnectButton());
            UtilityManagerClient.updatePreferences(current);

            resetOption(client.options.getGuiScale());
            client.options.write();
            client.onResolutionChanged();
            refreshCategory();
            return;
        }

        if (category == Category.ACCESSIBILITY) {
            UtilityPreferences defaults = UtilityPreferences.defaults();
            UtilityManagerClient.updatePreferences(
                    UtilityManagerClient.preferences().withSuppressNarrator(defaults.suppressNarrator())
            );

            resetOption(client.options.getShowSubtitles());
            resetOption(client.options.getTextBackgroundOpacity());
            resetOption(client.options.getBackgroundForChatOnly());
            resetOption(client.options.getFovEffectScale());
            resetOption(client.options.getDistortionEffectScale());
            resetOption(client.options.getHideLightningFlashes());
            resetOption(client.options.getMonochromeLogo());

            if (defaults.suppressNarrator()) {
                client.options.getNarrator().setValue(NarratorMode.OFF);
                client.options.getNarratorHotkey().setValue(false);
            } else {
                resetOption(client.options.getNarrator());
                resetOption(client.options.getNarratorHotkey());
            }

            client.options.write();
            client.options.sendClientSettings();
            refreshCategory();
        }
    }

    private boolean resetDisplay() {
        boolean previousFullscreen = client.options.getFullscreen().getValue();

        resetOption(client.options.getEnableVsync());
        resetOption(client.options.getMaxFps());
        resetOption(client.options.getGamma());
        boolean defaultBorderless = UtilityPreferences.defaults().borderlessWindow();
        UtilityManagerClient.updatePreferences(
                UtilityManagerClient.preferences().withBorderlessWindow(defaultBorderless)
        );
        if (BorderlessWindowController.isApplied() != defaultBorderless) {
            UtilityNotifications.show(
                    "Window Mode",
                    "The default window mode will be applied on the next game launch."
            );
        }

        boolean defaultFullscreen = defaultValue(client.options.getFullscreen());
        if (previousFullscreen != defaultFullscreen) {
            client.options.getFullscreen().setValue(defaultFullscreen);
            client.options.write();
            client.options.sendClientSettings();

            LazyBuilderSettingsScreen returnScreen = new LazyBuilderSettingsScreen(
                    parent,
                    Category.VIDEO,
                    VideoPage.DISPLAY,
                    "Window Mode"
            );
            client.setScreen(new LazyBuilderDisplayConfirmScreen(
                    returnScreen,
                    () -> {
                        if (client != null) {
                            client.options.getFullscreen().setValue(defaultFullscreen);
                            client.options.write();
                        }
                    },
                    () -> {
                        if (client != null) {
                            client.options.getFullscreen().setValue(previousFullscreen);
                            client.options.write();
                        }
                    }
            ));
            return true;
        }

        resetOption(client.options.getFullscreen());
        return false;
    }

    private void resetQuality() {
        resetOption(client.options.getGraphicsMode());
        resetOption(client.options.getCloudRenderMode());
        resetOption(client.options.getParticles());
        resetOption(client.options.getMipmapLevels());
    }

    private void resetView() {
        resetOption(client.options.getViewDistance());
        resetOption(client.options.getSimulationDistance());
        resetOption(client.options.getEntityDistanceScaling());
        resetOption(client.options.getFov());
    }

    private void resetPerformance() {
        writePerformanceState(new PerformanceState(
                true,
                30,
                10,
                false,
                true,
                true
        ));
    }

    @SuppressWarnings("unchecked")
    private static <T> T defaultValue(SimpleOption<T> option) {
        return (T) ((SimpleOptionAccessor) (Object) option).lazybuilder$getDefaultValue();
    }

    private static <T> void resetOption(SimpleOption<T> option) {
        option.setValue(defaultValue(option));
    }

    private static boolean isOptionModified(SimpleOption<?> option) {
        return !java.util.Objects.equals(option.getValue(), defaultValue(option));
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
        boolean visualMatch = client.options.getGraphicsMode().getValue() == preset.graphicsMode
                && client.options.getCloudRenderMode().getValue() == preset.cloudMode
                && client.options.getParticles().getValue() == preset.particlesMode
                && client.options.getMipmapLevels().getValue() == preset.mipmapLevels;
        if (!visualMatch) return false;

        PerformanceState performance = performanceState();
        if (performance == null) return true;
        return performance.hiddenObjectSkipping() == presetHiddenObjectSkipping(preset)
                && performance.renderingOptimizations()
                && performance.memoryOptimizations();
    }

    private static boolean presetHiddenObjectSkipping(GraphicsPreset preset) {
        return preset == GraphicsPreset.LOW;
    }

    private void openGraphicsPresetChoice() {
        GraphicsPreset current = detectGraphicsPreset();
        List<DropdownChoice> choices = new ArrayList<>();
        for (GraphicsPreset preset : List.of(GraphicsPreset.LOW, GraphicsPreset.MEDIUM, GraphicsPreset.HIGH)) {
            choices.add(new DropdownChoice(preset.label, preset == current, () -> applyGraphicsPreset(preset)));
        }
        openDropdown("Graphics Preset", choices);
    }

    private void applyGraphicsPreset(GraphicsPreset preset) {
        if (client == null || preset == GraphicsPreset.CUSTOM) return;

        client.options.getGraphicsMode().setValue(preset.graphicsMode);
        client.options.getCloudRenderMode().setValue(preset.cloudMode);
        client.options.getParticles().setValue(preset.particlesMode);
        client.options.getMipmapLevels().setValue(preset.mipmapLevels);

        PerformanceState current = performanceState();
        if (current != null) {
            writePerformanceState(new PerformanceState(
                    current.backgroundFpsPolicy(),
                    current.unfocusedFpsLimit(),
                    current.minimizedFpsLimit(),
                    presetHiddenObjectSkipping(preset),
                    true,
                    true
            ));
        }

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

    private void updatePerformance(Function<PerformanceState, PerformanceState> update) {
        PerformanceState current = performanceState();
        if (current == null) return;
        writePerformanceState(update.apply(current));
        refreshCategory();
    }

    @SuppressWarnings("unchecked")
    private void writePerformanceState(PerformanceState updated) {
        if (updated == null) return;
        Object shared = FabricLoader.getInstance().getObjectShare()
                .get("lazybuilder-performance-manager:settings-update");
        if (!(shared instanceof Consumer<?> rawConsumer)) return;

        Consumer<Map<String, Object>> consumer = (Consumer<Map<String, Object>>) rawConsumer;
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("backgroundFpsPolicy", updated.backgroundFpsPolicy());
        values.put("unfocusedFpsLimit", updated.unfocusedFpsLimit());
        values.put("minimizedFpsLimit", updated.minimizedFpsLimit());
        values.put("hiddenObjectSkipping", updated.hiddenObjectSkipping());
        values.put("renderingOptimizations", updated.renderingOptimizations());
        values.put("memoryOptimizations", updated.memoryOptimizations());
        consumer.accept(Map.copyOf(values));
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
                ? "VIDEO SETTINGS"
                : category.label.toUpperCase(Locale.ROOT) + " SETTINGS";
        context.drawTextWithShadow(textRenderer, Text.literal(heading), shellLeft() + 8, 15, TEXT_PRIMARY);
        if (contextlessFooterLegendX >= 0) {
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("• Modified"),
                    contextlessFooterLegendX,
                    height - 24,
                    TEXT_MUTED
            );
        }
        if (hasContextPane()) {
            context.fill(panelRight + 14, viewportTop(), panelRight + 15, height - FOOTER_HEIGHT - 10, DIVIDER);
        }

        Row highlightedRow = focusedSearchRow();
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

                boolean searchFocused = focusTarget != null && focusTarget.equals(row.title);
                int rowFill = searchFocused ? 0xB8232E30 : hovered ? ROW_HOVER : ROW_FILL;
                context.fill(row.x, row.y, row.x + row.width, row.y + ROW_HEIGHT - 1, rowFill);
                if (searchFocused) {
                    context.fill(row.x, row.y, row.x + 3, row.y + ROW_HEIGHT - 1, ACCENT);
                }
                context.fill(row.x, row.y + ROW_HEIGHT - 1, row.x + row.width, row.y + ROW_HEIGHT, DIVIDER);

                int textMax = Math.max(70, row.width - CONTROL_WIDTH - 34);
                String visibleTitle = textRenderer.trimToWidth(row.title, textMax);
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(visibleTitle),
                        row.x + 8,
                        row.y + 12,
                        TEXT_PRIMARY
                );
                if (isAuthoritativelyModified(row)) {
                    int dotX = Math.min(
                            row.x + textMax - 4,
                            row.x + 12 + textRenderer.getWidth(visibleTitle)
                    );
                    context.drawTextWithShadow(
                            textRenderer,
                            Text.literal("•"),
                            dotX,
                            row.y + 12,
                            ACCENT
                    );
                }
            }
        }
        context.disableScissor();

        renderScrollBar(context, panelRight + 6);
        renderContextPane(context, panelRight + 30, highlightedRow);
        renderCompactHelp(context, highlightedRow);
        super.render(context, mouseX, mouseY, delta);
        // Flush widget/text render layers before painting the popup so values from
        // rows behind the dropdown cannot bleed through due to batched GUI layers.
        context.draw();
        renderDropdown(context, mouseX, mouseY);
    }

    private boolean isAuthoritativelyModified(Row row) {
        UtilityPreferences current = UtilityManagerClient.preferences();
        UtilityPreferences defaults = UtilityPreferences.defaults();

        if (client != null) {
            switch (row.title) {
                case "V-Sync" -> { return isOptionModified(client.options.getEnableVsync()); }
                case "Frame Rate Limit" -> { return isOptionModified(client.options.getMaxFps()); }
                case "Brightness" -> { return isOptionModified(client.options.getGamma()); }
                case "Graphics Mode" -> { return isOptionModified(client.options.getGraphicsMode()); }
                case "Cloud Quality" -> { return isOptionModified(client.options.getCloudRenderMode()); }
                case "Particles" -> { return isOptionModified(client.options.getParticles()); }
                case "Mipmap Levels" -> { return isOptionModified(client.options.getMipmapLevels()); }
                case "Render Distance" -> { return isOptionModified(client.options.getViewDistance()); }
                case "Simulation Distance" -> { return isOptionModified(client.options.getSimulationDistance()); }
                case "Entity Distance" -> { return isOptionModified(client.options.getEntityDistanceScaling()); }
                case "Field of View" -> { return isOptionModified(client.options.getFov()); }
                case "Master Volume" -> { return isOptionModified(client.options.getSoundVolumeOption(SoundCategory.MASTER)); }
                case "Music" -> { return isOptionModified(client.options.getSoundVolumeOption(SoundCategory.MUSIC)); }
                case "Jukebox & Note Blocks" -> { return isOptionModified(client.options.getSoundVolumeOption(SoundCategory.RECORDS)); }
                case "Weather" -> { return isOptionModified(client.options.getSoundVolumeOption(SoundCategory.WEATHER)); }
                case "Blocks" -> { return isOptionModified(client.options.getSoundVolumeOption(SoundCategory.BLOCKS)); }
                case "Hostile Creatures" -> { return isOptionModified(client.options.getSoundVolumeOption(SoundCategory.HOSTILE)); }
                case "Friendly Creatures" -> { return isOptionModified(client.options.getSoundVolumeOption(SoundCategory.NEUTRAL)); }
                case "Players" -> { return isOptionModified(client.options.getSoundVolumeOption(SoundCategory.PLAYERS)); }
                case "Ambient" -> { return isOptionModified(client.options.getSoundVolumeOption(SoundCategory.AMBIENT)); }
                case "Voice / Speech" -> { return isOptionModified(client.options.getSoundVolumeOption(SoundCategory.VOICE)); }
                case "Sensitivity" -> { return isOptionModified(client.options.getMouseSensitivity()); }
                case "Invert Mouse" -> { return isOptionModified(client.options.getInvertYMouse()); }
                case "Raw Input" -> { return isOptionModified(client.options.getRawMouseInput()); }
                case "Discrete Mouse Scroll" -> { return isOptionModified(client.options.getDiscreteMouseScroll()); }
                case "Mouse Wheel Sensitivity" -> { return isOptionModified(client.options.getMouseWheelSensitivity()); }
                case "Auto Jump" -> { return isOptionModified(client.options.getAutoJump()); }
                case "Toggle Sneak" -> { return isOptionModified(client.options.getSneakToggled()); }
                case "Toggle Sprint" -> { return isOptionModified(client.options.getSprintToggled()); }
                case "Chat Opacity" -> { return isOptionModified(client.options.getChatOpacity()); }
                case "Chat Scale" -> { return isOptionModified(client.options.getChatScale()); }
                case "Chat Width" -> { return isOptionModified(client.options.getChatWidth()); }
                case "Line Spacing" -> { return isOptionModified(client.options.getChatLineSpacing()); }
                case "GUI Scale" -> { return isOptionModified(client.options.getGuiScale()); }
                case "Subtitles" -> { return isOptionModified(client.options.getShowSubtitles()); }
                case "Text Background" -> { return isOptionModified(client.options.getTextBackgroundOpacity()); }
                case "Chat Background Only" -> { return isOptionModified(client.options.getBackgroundForChatOnly()); }
                case "FOV Effects" -> { return isOptionModified(client.options.getFovEffectScale()); }
                case "Distortion Effects" -> { return isOptionModified(client.options.getDistortionEffectScale()); }
                case "Hide Lightning Flashes" -> { return isOptionModified(client.options.getHideLightningFlashes()); }
                case "Monochrome Logo" -> { return isOptionModified(client.options.getMonochromeLogo()); }
                case "Window Mode" -> {
                    return current.borderlessWindow() != defaults.borderlessWindow()
                            || isOptionModified(client.options.getFullscreen());
                }
            }
        }

        return switch (row.title) {
            case "Keep Unsent Message" -> current.keepChatDraft() != defaults.keepChatDraft();
            case "Search Chat" -> current.chatSearch() != defaults.chatSearch();
            case "Extended History" -> current.extendedChatHistory() != defaults.extendedChatHistory();
            case "Timestamps" -> current.chatTimestamps() != defaults.chatTimestamps();
            case "Show Signing Indicators" ->
                    current.hideChatSigningIndicators() != defaults.hideChatSigningIndicators();
            case "Show Report Button" ->
                    current.hideChatReportButton() != defaults.hideChatReportButton();
            case "Compact Debug HUD" -> current.compactDebugHud() != defaults.compactDebugHud();
            case "Contextual Screenshot Names" ->
                    current.contextualScreenshotNames() != defaults.contextualScreenshotNames();
            case "Quick Creative Search" ->
                    current.instantCreativeSearch() != defaults.instantCreativeSearch();
            case "Reconnect Button" -> current.reconnectButton() != defaults.reconnectButton();
            case "Keep Narrator Off" -> current.suppressNarrator() != defaults.suppressNarrator();
            case "Reduce FPS in Background" -> {
                PerformanceState state = performanceState();
                yield state != null && !state.backgroundFpsPolicy();
            }
            case "Background FPS" -> {
                PerformanceState state = performanceState();
                yield state != null && state.unfocusedFpsLimit() != 30;
            }
            case "Minimized FPS" -> {
                PerformanceState state = performanceState();
                yield state != null && state.minimizedFpsLimit() != 10;
            }
            case "Skip Hidden Objects" -> {
                PerformanceState state = performanceState();
                yield state != null && state.hiddenObjectSkipping();
            }
            case "Optimized World Rendering" -> {
                PerformanceState state = performanceState();
                yield state != null && !state.renderingOptimizations();
            }
            case "Memory Optimization" -> {
                PerformanceState state = performanceState();
                yield state != null && !state.memoryOptimizations();
            }
            default -> false;
        };
    }

    private Row focusedSearchRow() {
        if (focusTarget == null || focusTarget.isBlank()) return null;
        for (Section section : sections) {
            for (Row row : section.rows) {
                if (focusTarget.equals(row.title)) return row;
            }
        }
        return null;
    }

    private void renderCompactHelp(DrawContext context, Row highlightedRow) {
        if (hasContextPane() || highlightedRow == null) return;

        int left = panelLeft();
        int available = panelWidth();
        int y = viewportBottom() + 7;
        String copy = textRenderer.trimToWidth(highlightedRow.description, available);
        context.drawTextWithShadow(
                textRenderer,
                Text.literal(copy),
                left,
                y,
                TEXT_MUTED
        );
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
            title = category == Category.VIDEO
                    ? videoPage.label.toUpperCase(Locale.ROOT)
                    : category.label.toUpperCase(Locale.ROOT);
            description = switch (category) {
                case VIDEO -> switch (videoPage) {
                    case DISPLAY -> "Window, frame pacing, and screen visibility settings.";
                    case QUALITY -> "Choose one Graphics Preset for visual detail and matching performance behavior. Fine-tune only when needed.";
                    case VIEW -> "World distance and camera settings.";
                    case PERFORMANCE -> "Advanced performance overrides. Most players can leave these managed by the Graphics Preset.";
                    case VISUAL -> "Manage Resource Packs and Shaders. Visual content stays separate from graphics quality and performance tuning.";
                };
                case AUDIO -> "Master volume and the main Minecraft sound categories.";
                case CONTROLS -> "Mouse, movement and all registered key bindings.";
                case CHAT -> "Chat behavior, appearance, search, and message controls.";
                case INTERFACE -> "Builder-facing HUD, screenshot and Creative-mode preferences.";
                case ACCESSIBILITY -> "Subtitles, narrator behavior, readability, and visual comfort settings.";
                case TOOLS -> "Tool-specific setup stays close to the workflow that owns it.";
            };
        }

        int contextY = contentTop() + 2;
        context.drawTextWithShadow(textRenderer, Text.literal(title), x, contextY, TEXT_PRIMARY);
        int y = contextY + 18;
        for (var line : textRenderer.wrapLines(Text.literal(description), available)) {
            context.drawTextWithShadow(textRenderer, line, x, y, TEXT_MUTED);
            y += 11;
        }
        if (highlightedRow != null && isAuthoritativelyModified(highlightedRow)) {
            y += 8;
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("• Modified from LazyBuilder default"),
                    x,
                    y,
                    ACCENT
            );
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

            boolean keyboardFocused = dropdown.isFocused(choiceIndex);
            int fill = choice.selected()
                    ? 0xCC1F6558
                    : (hovered || keyboardFocused) ? 0xFF252C34 : 0xFF1A1F25;
            context.fill(left, itemY, right, itemY + DropdownState.ITEM_HEIGHT - 1, fill);
            context.fill(left, itemY + DropdownState.ITEM_HEIGHT - 1, right, itemY + DropdownState.ITEM_HEIGHT, DIVIDER);

            int textColor = choice.selected() || hovered || keyboardFocused ? TEXT_PRIMARY : TEXT_SECONDARY;
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
        if (dropdown != null) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                dropdown = null;
                clearAndInit();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
                dropdown.moveSelection(-1);
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                dropdown.moveSelection(1);
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
                DropdownChoice choice = dropdown.focusedChoice();
                if (choice != null) {
                    dropdown = null;
                    choice.action().run();
                    return true;
                }
            }
        }
        if (Screen.hasControlDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_F) {
            openSettingsSearch();
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

    private int categoryTabRows() {
        int gap = 2;
        int minimum = Category.values().length * 58 + gap * (Category.values().length - 1);
        if (height < 300) return 1;
        return panelWidth() < minimum ? 2 : 1;
    }

    private int videoTabRows() {
        int gap = 2;
        int minimum = VideoPage.values().length * 58 + gap * (VideoPage.values().length - 1);
        if (height < 300) return 1;
        return panelWidth() < minimum ? 2 : 1;
    }

    private int videoTabTop() {
        return TAB_TOP + categoryTabRows() * (TAB_HEIGHT + 2) + 4;
    }

    private int contentTop() {
        int afterCategoryTabs = TAB_TOP + categoryTabRows() * (TAB_HEIGHT + 2);
        if (category != Category.VIDEO) return afterCategoryTabs + 14;
        return videoTabTop() + videoTabRows() * (TAB_HEIGHT + 2) + 14;
    }

    private int viewportTop() {
        return contentTop() - 8;
    }

    private int viewportBottom() {
        int helpReserve = hasContextPane() ? 0 : 24;
        return Math.max(viewportTop() + 1, height - FOOTER_HEIGHT - 6 - helpReserve);
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
        private int focusedIndex = -1;

        private DropdownState(String anchor, List<DropdownChoice> choices) {
            this.anchor = anchor;
            this.choices = List.copyOf(choices);
            this.focusedIndex = selectedIndex();
            if (this.focusedIndex < 0 && !this.choices.isEmpty()) this.focusedIndex = 0;
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

        private void moveSelection(int delta) {
            if (choices.isEmpty()) return;
            if (focusedIndex < 0) focusedIndex = 0;
            focusedIndex = Math.max(0, Math.min(choices.size() - 1, focusedIndex + delta));

            if (focusedIndex < firstVisible) {
                firstVisible = focusedIndex;
            } else if (focusedIndex >= firstVisible + visibleCount) {
                firstVisible = Math.max(0, focusedIndex - visibleCount + 1);
            }
        }

        private DropdownChoice focusedChoice() {
            if (focusedIndex < 0 || focusedIndex >= choices.size()) return null;
            return choices.get(focusedIndex);
        }

        private boolean isFocused(int choiceIndex) {
            return choiceIndex == focusedIndex;
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

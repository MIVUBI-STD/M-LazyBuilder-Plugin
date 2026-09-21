package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search across the stable user-facing LazyBuilder Settings vocabulary. */
public final class LazyBuilderSettingsSearchScreen extends Screen {
    private static final int BACKGROUND = 0xF20B0E12;
    private static final int TOP_BAR = 0xE813171C;
    private static final int ROW_HEIGHT = 26;
    private static final int ROW_GAP = 3;
    private static final int FOOTER_HEIGHT = 40;

    private final Screen parent;
    private final List<SearchEntry> allEntries = entries();
    private final List<SearchEntry> filtered = new ArrayList<>();
    private TextFieldWidget searchField;
    private int scrollOffset;
    private int maxScroll;

    public LazyBuilderSettingsSearchScreen(Screen parent) {
        super(Text.literal("Search Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        filtered.clear();
        filtered.addAll(filter(searchField == null ? "" : searchField.getText()));

        int width = Math.min(720, Math.max(280, this.width - 24));
        int left = (this.width - width) / 2;

        String previous = searchField == null ? "" : searchField.getText();
        searchField = new TextFieldWidget(
                textRenderer,
                left,
                48,
                width,
                22,
                Text.literal("Search settings")
        );
        searchField.setPlaceholder(Text.literal("Search settings..."));
        searchField.setMaxLength(80);
        searchField.setText(previous);
        searchField.setChangedListener(value -> {
            filtered.clear();
            filtered.addAll(filter(value));
            scrollOffset = 0;
            clearAndInit();
        });
        addDrawableChild(searchField);
        setFocused(searchField);
        searchField.setFocused(true);

        int viewportTop = 82;
        int viewportBottom = height - FOOTER_HEIGHT - 6;
        int viewportHeight = Math.max(1, viewportBottom - viewportTop);
        int contentHeight = filtered.size() * (ROW_HEIGHT + ROW_GAP);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int y = viewportTop - scrollOffset;
        for (SearchEntry entry : filtered) {
            if (y + ROW_HEIGHT > viewportTop && y < viewportBottom) {
                addDrawableChild(new LazyBuilderSettingsSearchResultWidget(
                        left,
                        y,
                        width,
                        ROW_HEIGHT,
                        entry.title(),
                        entry.path(),
                        () -> open(entry)
                ));
            }
            y += ROW_HEIGHT + ROW_GAP;
        }

        addDrawableChild(new LazyBuilderSettingsControlWidget(
                left + width - 92,
                height - 30,
                92,
                22,
                Text.literal("Back"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::close
        ));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);
        context.fill(0, 0, width, 36, TOP_BAR);
        context.fill(0, height - FOOTER_HEIGHT, width, height, TOP_BAR);

        int panelWidth = Math.min(720, Math.max(280, width - 24));
        int left = (width - panelWidth) / 2;
        context.drawTextWithShadow(
                textRenderer,
                Text.literal("SEARCH SETTINGS"),
                left,
                15,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );

        if (filtered.isEmpty()) {
            String message = searchField != null && !searchField.getText().isBlank()
                    ? "No settings found."
                    : "Type to search all settings.";
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal(message),
                    width / 2,
                    104,
                    LazyBuilderSettingsScreen.TEXT_MUTED
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0) {
            int next = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(verticalAmount * 24.0)));
            if (next != scrollOffset) {
                scrollOffset = next;
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private List<SearchEntry> filter(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return allEntries;
        return allEntries.stream()
                .filter(entry -> entry.searchText().contains(normalized))
                .toList();
    }

    private void open(SearchEntry entry) {
        if (client == null) return;
        client.setScreen(new LazyBuilderSettingsScreen(
                this,
                entry.category(),
                entry.videoPage()
        ));
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private record SearchEntry(
            String title,
            String keywords,
            LazyBuilderSettingsScreen.Category category,
            LazyBuilderSettingsScreen.VideoPage videoPage
    ) {
        String path() {
            if (category == LazyBuilderSettingsScreen.Category.VIDEO) {
                return "Video > " + videoPage.label;
            }
            return category.label;
        }

        String searchText() {
            return (title + " " + keywords + " " + path()).toLowerCase(Locale.ROOT);
        }
    }

    private static SearchEntry e(
            String title,
            String keywords,
            LazyBuilderSettingsScreen.Category category,
            LazyBuilderSettingsScreen.VideoPage page
    ) {
        return new SearchEntry(title, keywords, category, page);
    }

    private static List<SearchEntry> entries() {
        var C = LazyBuilderSettingsScreen.Category.class;
        var V = LazyBuilderSettingsScreen.VideoPage.class;
        List<SearchEntry> values = new ArrayList<>();

        values.add(e("Fullscreen", "window display", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.DISPLAY));
        values.add(e("V-Sync", "vsync display frame", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.DISPLAY));
        values.add(e("Frame Rate Limit", "fps display", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.DISPLAY));
        values.add(e("Brightness", "gamma display", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.DISPLAY));

        values.add(e("Graphics Preset", "low medium high custom quality", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.QUALITY));
        values.add(e("Graphics Mode", "fast fancy fabulous quality", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.QUALITY));
        values.add(e("Cloud Quality", "clouds quality", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.QUALITY));
        values.add(e("Particles", "effects quality", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.QUALITY));
        values.add(e("Mipmap Levels", "texture filtering quality", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.QUALITY));

        values.add(e("Render Distance", "chunks view", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.VIEW));
        values.add(e("Simulation Distance", "world active chunks view", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.VIEW));
        values.add(e("Entity Distance", "mobs players view", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.VIEW));
        values.add(e("Field of View", "fov camera view", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.VIEW));

        values.add(e("Background FPS", "performance inactive window", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.PERFORMANCE));
        values.add(e("Minimized FPS", "performance inactive window", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.PERFORMANCE));
        values.add(e("Skip Hidden Objects", "culling performance", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.PERFORMANCE));
        values.add(e("Optimized World Rendering", "renderer performance", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.PERFORMANCE));
        values.add(e("Memory Optimization", "ram memory performance", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.PERFORMANCE));

        values.add(e("Resource Packs", "textures models visual", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.VISUAL));
        values.add(e("Shaders", "lighting shadows visual iris", LazyBuilderSettingsScreen.Category.VIDEO, LazyBuilderSettingsScreen.VideoPage.VISUAL));

        for (String audio : List.of("Master Volume","Music","Jukebox & Note Blocks","Weather","Blocks","Hostile Creatures","Friendly Creatures","Players","Ambient","Voice / Speech")) {
            values.add(e(audio, "sound volume audio", LazyBuilderSettingsScreen.Category.AUDIO, LazyBuilderSettingsScreen.VideoPage.QUALITY));
        }

        for (String controls : List.of("Sensitivity","Invert Mouse","Raw Input","Discrete Mouse Scroll","Mouse Wheel Sensitivity","Auto Jump","Toggle Sneak","Toggle Sprint","Key Bindings")) {
            values.add(e(controls, "controls input keyboard mouse", LazyBuilderSettingsScreen.Category.CONTROLS, LazyBuilderSettingsScreen.VideoPage.QUALITY));
        }

        for (String ui : List.of("GUI Scale","Compact Debug HUD","Contextual Screenshot Names","Quick Creative Search")) {
            values.add(e(ui, "interface ui hud", LazyBuilderSettingsScreen.Category.INTERFACE, LazyBuilderSettingsScreen.VideoPage.QUALITY));
        }

        for (String accessibility : List.of("Subtitles","Keep Narrator Off","Narrator Mode","Text Background","Chat Background Only","FOV Effects","Distortion Effects","Hide Lightning Flashes","Monochrome Logo")) {
            values.add(e(accessibility, "accessibility comfort readability", LazyBuilderSettingsScreen.Category.ACCESSIBILITY, LazyBuilderSettingsScreen.VideoPage.QUALITY));
        }

        values.add(e("Editing Tools", "tools building axiom", LazyBuilderSettingsScreen.Category.TOOLS, LazyBuilderSettingsScreen.VideoPage.QUALITY));
        values.add(e("Map & Worlds", "tools map world", LazyBuilderSettingsScreen.Category.TOOLS, LazyBuilderSettingsScreen.VideoPage.QUALITY));

        return List.copyOf(values);
    }
}

package com.halokaryamedia.lazybuilder.utility.ui;

import com.halokaryamedia.lazybuilder.utility.notification.UtilityNotifications;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.LanguageDefinition;
import net.minecraft.client.resource.language.LanguageManager;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Searchable language selection using Minecraft's LanguageManager as authority. */
public final class LazyBuilderLanguageScreen extends Screen {
    private static final int BACKGROUND = 0xF20B0E12;
    private static final int TOP_BAR = 0xE813171C;
    private static final int FOOTER_HEIGHT = 40;
    private static final int ROW_HEIGHT = 28;
    private static final int ROW_GAP = 2;
    private static final int LIST_TOP = 82;

    private final Screen parent;
    private final List<Entry> filtered = new ArrayList<>();
    private TextFieldWidget searchField;
    private String query = "";
    private int scrollOffset;
    private int maxScroll;
    private String failure;
    private String failedLanguageCode;

    public LazyBuilderLanguageScreen(Screen parent) {
        super(Text.literal("Language"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int shell = Math.min(720, Math.max(280, width - 24));
        int left = (width - shell) / 2;

        searchField = new TextFieldWidget(
                textRenderer,
                left,
                48,
                shell,
                22,
                Text.literal("Search languages")
        );
        searchField.setPlaceholder(Text.literal("Search languages..."));
        searchField.setMaxLength(80);
        searchField.setText(query);
        searchField.setChangedListener(value -> {
            query = value;
            scrollOffset = 0;
            clearAndInit();
        });
        addDrawableChild(searchField);
        setInitialFocus(searchField);

        rebuildEntries();

        int viewportBottom = height - FOOTER_HEIGHT - 6;
        int viewportHeight = Math.max(1, viewportBottom - LIST_TOP);
        int contentHeight = filtered.size() * (ROW_HEIGHT + ROW_GAP);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int y = LIST_TOP - scrollOffset;
        String current = currentLanguageCode();
        for (Entry entry : filtered) {
            if (y + ROW_HEIGHT > LIST_TOP && y < viewportBottom) {
                String label = entry.displayName();
                if (entry.code().equals(current)) label += "  ✓";
                addDrawableChild(new LazyBuilderSettingsControlWidget(
                        left,
                        y,
                        shell,
                        ROW_HEIGHT - 2,
                        Text.literal(label),
                        true,
                        LazyBuilderSettingsControlWidget.Kind.ACTION,
                        () -> applyLanguage(entry.code())
                ));
            }
            y += ROW_HEIGHT + ROW_GAP;
        }

        if (failure != null && failedLanguageCode != null) {
            addDrawableChild(new LazyBuilderSettingsControlWidget(
                    left,
                    height - 30,
                    92,
                    22,
                    Text.literal("Retry"),
                    true,
                    LazyBuilderSettingsControlWidget.Kind.FOOTER,
                    () -> applyLanguage(failedLanguageCode)
            ));
        }

        addDrawableChild(new LazyBuilderSettingsControlWidget(
                left + shell - 92,
                height - 30,
                92,
                22,
                Text.literal("Back"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::close
        ));
    }

    private void rebuildEntries() {
        filtered.clear();
        if (client == null) return;

        LanguageManager manager = client.getLanguageManager();
        String normalized = query.trim().toLowerCase(Locale.ROOT);

        for (Map.Entry<String, LanguageDefinition> value : manager.getAllLanguages().entrySet()) {
            String code = value.getKey();
            LanguageDefinition definition = value.getValue();
            Entry entry = new Entry(
                    code,
                    definition.getName(),
                    definition.getRegion()
            );
            if (normalized.isEmpty() || entry.searchText().contains(normalized)) {
                filtered.add(entry);
            }
        }
    }

    private String currentLanguageCode() {
        if (client == null) return "";
        return client.getLanguageManager().getLanguage();
    }

    private void applyLanguage(String code) {
        if (client == null || code.equals(currentLanguageCode())) return;
        failure = null;
        failedLanguageCode = null;

        try {
            LanguageManager manager = client.getLanguageManager();
            manager.setLanguage(code);
            client.options.language = code;
            client.options.write();

            UtilityNotifications.show("Language", "Applying language...");
            client.reloadResources().whenComplete((ignored, error) -> {
                if (error != null) {
                    failure = error.getMessage() == null ? "Resource reload failed." : error.getMessage();
                    failedLanguageCode = code;
                    if (client != null) client.execute(this::clearAndInit);
                }
            });
            clearAndInit();
        } catch (RuntimeException error) {
            failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            failedLanguageCode = code;
            clearAndInit();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);
        context.fill(0, 0, width, 36, TOP_BAR);
        context.fill(0, height - FOOTER_HEIGHT, width, height, TOP_BAR);

        int shell = Math.min(720, Math.max(280, width - 24));
        int left = (width - shell) / 2;
        context.drawTextWithShadow(
                textRenderer,
                Text.literal("LANGUAGE"),
                left,
                15,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );

        if (filtered.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal("No languages found."),
                    width / 2,
                    104,
                    LazyBuilderSettingsScreen.TEXT_MUTED
            );
        }

        if (failure != null) {
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("Could not apply language: " + textRenderer.trimToWidth(failure, shell - 150)),
                    left + (failedLanguageCode != null ? 104 : 0),
                    height - FOOTER_HEIGHT - 18,
                    0xFFFFA7A7
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
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    private record Entry(String code, String name, String region) {
        String displayName() {
            if (region == null || region.isBlank()) return name;
            return name + " (" + region + ")";
        }

        String searchText() {
            return (code + " " + name + " " + region).toLowerCase(Locale.ROOT);
        }
    }
}

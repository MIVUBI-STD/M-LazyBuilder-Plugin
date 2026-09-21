package com.halokaryamedia.lazybuilder.utility.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Searchable shader-pack selector backed by Performance Manager ObjectShare state. */
public final class LazyBuilderShaderPackScreen extends Screen {
    private static final int BACKGROUND = 0xF20B0E12;
    private static final int TOP_BAR = 0xE813171C;
    private static final int FOOTER_HEIGHT = 40;
    private static final int LIST_TOP = 82;
    private static final int ROW_HEIGHT = 28;
    private static final int ROW_GAP = 2;

    private final Screen parent;
    private final List<Entry> filtered = new ArrayList<>();
    private TextFieldWidget searchField;
    private String query = "";
    private int scrollOffset;
    private int maxScroll;

    public LazyBuilderShaderPackScreen(Screen parent) {
        super(Text.literal("Shader Packs"));
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
                Text.literal("Search shader packs")
        );
        searchField.setPlaceholder(Text.literal("Search shader packs..."));
        searchField.setMaxLength(80);
        searchField.setText(query);
        searchField.setChangedListener(value -> {
            query = value;
            scrollOffset = 0;
            clearAndInit();
        });
        addDrawableChild(searchField);
        setInitialFocus(searchField);

        ShaderState state = shaderState();
        rebuildEntries(state);

        int viewportBottom = height - FOOTER_HEIGHT - 6;
        int viewportHeight = Math.max(1, viewportBottom - LIST_TOP);
        int contentHeight = filtered.size() * (ROW_HEIGHT + ROW_GAP);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int y = LIST_TOP - scrollOffset;
        for (Entry entry : filtered) {
            if (y + ROW_HEIGHT > LIST_TOP && y < viewportBottom) {
                String label = entry.name();
                if (entry.id().equals(state.selectedPackId())) label += "  ✓";
                addDrawableChild(new LazyBuilderSettingsControlWidget(
                        left,
                        y,
                        shell,
                        ROW_HEIGHT - 2,
                        Text.literal(label),
                        true,
                        LazyBuilderSettingsControlWidget.Kind.ACTION,
                        () -> select(entry.id())
                ));
            }
            y += ROW_HEIGHT + ROW_GAP;
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

    private void rebuildEntries(ShaderState state) {
        filtered.clear();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        int count = Math.min(state.packIds().size(), state.packNames().size());
        for (int i = 0; i < count; i++) {
            Entry entry = new Entry(state.packIds().get(i), state.packNames().get(i));
            if (normalized.isEmpty()
                    || entry.id().toLowerCase(Locale.ROOT).contains(normalized)
                    || entry.name().toLowerCase(Locale.ROOT).contains(normalized)) {
                filtered.add(entry);
            }
        }
        filtered.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()));
    }

    @SuppressWarnings("unchecked")
    private void select(String id) {
        Object shared = FabricLoader.getInstance().getObjectShare()
                .get("lazybuilder-performance-manager:shader-select");
        if (shared instanceof Consumer<?> consumer) {
            ((Consumer<String>) consumer).accept(id);
        }
        if (client != null) client.setScreen(parent);
    }

    @SuppressWarnings("unchecked")
    private ShaderState shaderState() {
        Object shared = FabricLoader.getInstance().getObjectShare()
                .get("lazybuilder-performance-manager:shader-snapshot");
        if (!(shared instanceof Supplier<?> supplier)) return ShaderState.EMPTY;
        Object value = supplier.get();
        if (!(value instanceof Map<?, ?> map)) return ShaderState.EMPTY;

        return new ShaderState(
                stringValue(map, "selectedPackId"),
                stringList(map.get("packIds")),
                stringList(map.get("packNames"))
        );
    }

    private static String stringValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof String text ? text : "";
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String text) result.add(text);
        }
        return List.copyOf(result);
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
                Text.literal("SHADER PACKS"),
                left,
                15,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );

        if (filtered.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal(query.isBlank() ? "No shader packs found." : "No matching shader packs."),
                    width / 2,
                    108,
                    LazyBuilderSettingsScreen.TEXT_MUTED
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private record Entry(String id, String name) {}

    private record ShaderState(
            String selectedPackId,
            List<String> packIds,
            List<String> packNames
    ) {
        private static final ShaderState EMPTY = new ShaderState("", List.of(), List.of());

        private ShaderState {
            selectedPackId = selectedPackId == null ? "" : selectedPackId;
            packIds = packIds == null ? List.of() : List.copyOf(packIds);
            packNames = packNames == null ? List.of() : List.copyOf(packNames);
        }
    }
}

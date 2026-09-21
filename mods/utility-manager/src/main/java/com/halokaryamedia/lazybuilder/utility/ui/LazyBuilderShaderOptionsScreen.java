package com.halokaryamedia.lazybuilder.utility.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Staged editor for options declared by a LazyBuilder-native shader pack. */
public final class LazyBuilderShaderOptionsScreen extends Screen {
    private static final int BACKGROUND = 0xF20B0E12;
    private static final int TOP_BAR = 0xE813171C;
    private static final int ROW_FILL = 0xA81A1F25;
    private static final int DIVIDER = 0x44545C66;
    private static final int FOOTER_HEIGHT = 40;
    private static final int ROW_HEIGHT = 34;
    private static final int ROW_GAP = 2;
    private static final int LIST_TOP = 66;

    private final Screen parent;
    private final Map<String, String> original = new LinkedHashMap<>();
    private final Map<String, String> staged = new LinkedHashMap<>();
    private String packId = "";
    private String packName = "";
    private int scrollOffset;
    private int maxScroll;
    private LazyBuilderSettingsControlWidget applyButton;

    public LazyBuilderShaderOptionsScreen(Screen parent) {
        super(Text.literal("Shader Options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        OptionState state = readState();
        if (!state.packId().equals(packId)) {
            packId = state.packId();
            packName = state.packName();
            original.clear();
            staged.clear();
            for (OptionRow option : state.options()) {
                original.put(option.id(), option.value());
                staged.put(option.id(), option.value());
            }
            scrollOffset = 0;
        }

        int shell = Math.min(720, Math.max(280, width - 24));
        int left = (width - shell) / 2;
        int right = left + shell;
        int viewportBottom = height - FOOTER_HEIGHT - 8;
        int contentHeight = state.options().size() * (ROW_HEIGHT + ROW_GAP);
        int viewportHeight = Math.max(1, viewportBottom - LIST_TOP);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int y = LIST_TOP - scrollOffset;
        for (OptionRow option : state.options()) {
            if (y + ROW_HEIGHT > LIST_TOP && y < viewportBottom) {
                addOptionControl(option, left, right, y);
            }
            y += ROW_HEIGHT + ROW_GAP;
        }

        addDrawableChild(new LazyBuilderSettingsControlWidget(
                left,
                height - 30,
                110,
                22,
                Text.literal("Reset Defaults"),
                !state.options().isEmpty(),
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                () -> resetDefaults(state.options())
        ));

        applyButton = new LazyBuilderSettingsControlWidget(
                right - 192,
                height - 30,
                92,
                22,
                Text.literal("Apply"),
                isDirty(),
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::apply
        );
        addDrawableChild(applyButton);

        addDrawableChild(new LazyBuilderSettingsControlWidget(
                right - 92,
                height - 30,
                92,
                22,
                Text.literal("Back"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::close
        ));
    }

    private void addOptionControl(OptionRow option, int left, int right, int y) {
        int controlWidth = Math.min(210, Math.max(120, (right - left) / 3));
        int controlX = right - controlWidth - 8;
        String value = staged.getOrDefault(option.id(), option.value());

        if ("boolean".equals(option.type())) {
            boolean enabled = "true".equalsIgnoreCase(value) || "1".equals(value);
            addDrawableChild(new LazyBuilderSettingsControlWidget(
                    controlX,
                    y + 6,
                    controlWidth,
                    22,
                    Text.literal(enabled ? "ON" : "OFF"),
                    true,
                    LazyBuilderSettingsControlWidget.Kind.TOGGLE,
                    () -> {
                        staged.put(option.id(), Boolean.toString(!enabled));
                        clearAndInit();
                    }
            ));
            return;
        }

        double min = number(option.min(), 0.0);
        double max = number(option.max(), min + 1.0);
        double step = number(option.step(), "int".equals(option.type()) ? 1.0 : 0.01);
        double current = number(value, number(option.defaultValue(), min));

        addDrawableChild(new LazyBuilderSettingsSliderWidget(
                controlX,
                y + 6,
                controlWidth,
                22,
                current,
                min,
                max,
                step,
                numeric -> formatValue(option.type(), numeric),
                numeric -> {
                    staged.put(option.id(), formatValue(option.type(), numeric));
                    if (applyButton != null) applyButton.setInteractive(isDirty());
                }
        ));
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
        int right = left + shell;

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("SHADER OPTIONS"),
                left,
                15,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );
        if (!packName.isBlank()) {
            String name = textRenderer.trimToWidth(packName, Math.max(80, shell - 160));
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(name),
                    right - textRenderer.getWidth(name),
                    15,
                    LazyBuilderSettingsScreen.TEXT_MUTED
            );
        }

        OptionState state = readState();
        int y = LIST_TOP - scrollOffset;
        int viewportBottom = height - FOOTER_HEIGHT - 8;
        for (OptionRow option : state.options()) {
            if (y + ROW_HEIGHT > LIST_TOP && y < viewportBottom) {
                context.fill(left, y, right, y + ROW_HEIGHT - 1, ROW_FILL);
                context.fill(left, y + ROW_HEIGHT - 1, right, y + ROW_HEIGHT, DIVIDER);

                int maxLabel = Math.max(80, shell - Math.min(210, Math.max(120, shell / 3)) - 30);
                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(textRenderer.trimToWidth(option.label(), maxLabel)),
                        left + 8,
                        y + 13,
                        LazyBuilderSettingsScreen.TEXT_PRIMARY
                );
            }
            y += ROW_HEIGHT + ROW_GAP;
        }

        if (state.options().isEmpty()) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal("This shader pack does not declare user options."),
                    width / 2,
                    90,
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

    private void resetDefaults(List<OptionRow> options) {
        for (OptionRow option : options) {
            staged.put(option.id(), option.defaultValue());
        }
        clearAndInit();
    }

    @SuppressWarnings("unchecked")
    private void apply() {
        Object action = share("lazybuilder-performance-manager:shader-option-update");
        if (!(action instanceof BiConsumer<?, ?> raw)) return;

        BiConsumer<String, String> update = (BiConsumer<String, String>) raw;
        for (Map.Entry<String, String> value : staged.entrySet()) {
            if (!java.util.Objects.equals(original.get(value.getKey()), value.getValue())) {
                update.accept(value.getKey(), value.getValue());
            }
        }

        Object apply = share("lazybuilder-performance-manager:shader-options-apply");
        if (apply instanceof Runnable runnable) runnable.run();

        original.clear();
        original.putAll(staged);
        clearAndInit();
    }

    private boolean isDirty() {
        return !staged.equals(original);
    }

    @Override
    public void close() {
        if (client == null) return;
        if (!isDirty()) {
            client.setScreen(parent);
            return;
        }

        client.setScreen(new LazyBuilderConfirmScreen(
                this,
                "Discard Shader Changes",
                "Discard shader option changes that have not been applied?",
                "Discard",
                () -> {
                    original.clear();
                    original.putAll(staged);
                    if (client != null) client.setScreen(parent);
                }
        ));
    }

    private OptionState readState() {
        Object shared = share("lazybuilder-performance-manager:shader-snapshot");
        if (!(shared instanceof Supplier<?> supplier)) return OptionState.EMPTY;
        Object raw = supplier.get();
        if (!(raw instanceof Map<?, ?> values)) return OptionState.EMPTY;

        String id = text(values.get("selectedPackId"));
        String name = text(values.get("selectedPackName"));
        List<OptionRow> options = new ArrayList<>();
        Object rows = values.get("options");
        if (rows instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> row)) continue;
                String optionId = text(row.get("id"));
                String type = text(row.get("type"));
                if (optionId.isBlank() || type.isBlank()) continue;
                options.add(new OptionRow(
                        optionId,
                        text(row.get("label")),
                        type,
                        text(row.get("value")),
                        text(row.get("default")),
                        text(row.get("min")),
                        text(row.get("max")),
                        text(row.get("step"))
                ));
            }
        }
        return new OptionState(id, name, List.copyOf(options));
    }

    private static Object share(String key) {
        return FabricLoader.getInstance().getObjectShare().get(key);
    }

    private static String text(Object value) {
        return value instanceof String string ? string : "";
    }

    private static double number(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String formatValue(String type, double value) {
        if ("int".equals(type)) return Long.toString(Math.round(value));
        String text = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        return text.contains(".") ? text : text + ".0";
    }

    private record OptionRow(
            String id,
            String label,
            String type,
            String value,
            String defaultValue,
            String min,
            String max,
            String step
    ) {}

    private record OptionState(String packId, String packName, List<OptionRow> options) {
        private static final OptionState EMPTY = new OptionState("", "", List.of());
    }
}

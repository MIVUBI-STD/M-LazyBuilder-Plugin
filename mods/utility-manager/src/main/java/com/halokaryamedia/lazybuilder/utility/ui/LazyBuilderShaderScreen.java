package com.halokaryamedia.lazybuilder.utility.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * First-party LazyBuilder shader manager.
 *
 * Performance Manager owns shader discovery, compilation, runtime state, terrain
 * integration, GBuffer, shadows, and post-process. Utility Manager only presents
 * the narrow ObjectShare contract.
 */
public final class LazyBuilderShaderScreen extends Screen {
    private static final int BACKGROUND = 0xF20B0E12;
    private static final int TOP_BAR = 0xE813171C;
    private static final int PANEL = 0xA81A1F25;
    private static final int DIVIDER = 0x44545C66;
    private static final int FOOTER_HEIGHT = 40;
    private static final int ROW_HEIGHT = 28;
    private static final int ROW_GAP = 2;

    private final Screen parent;
    private long observedRevision = Long.MIN_VALUE;
    private ShaderState currentState = ShaderState.unavailable();
    private int scrollOffset;
    private int maxScroll;

    public LazyBuilderShaderScreen(Screen parent) {
        super(Text.literal("Shaders"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ShaderState state = state();
        currentState = state;
        observedRevision = state.revision();

        int shell = Math.min(760, Math.max(280, width - 24));
        int left = (width - shell) / 2;
        int right = left + shell;
        boolean compact = shell < 540;
        int listTop = state.optionCount() > 0
                ? (compact ? 170 : 126)
                : (compact ? 142 : 104);
        int footerTop = height - FOOTER_HEIGHT;

        int footerX = left;
        addDrawableChild(new LazyBuilderSettingsControlWidget(
                footerX,
                height - 30,
                92,
                22,
                Text.literal("Refresh"),
                state.available(),
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::refreshPacks
        ));
        footerX += 100;

        addDrawableChild(new LazyBuilderSettingsControlWidget(
                footerX,
                height - 30,
                116,
                22,
                Text.literal("Open Folder"),
                state.available() && !state.directory().isBlank(),
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                () -> openFolder(state.directory())
        ));

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

        if (!state.available()) return;

        int actionWidth = compact ? shell - 28 : 170;
        int actionX = compact ? left + 14 : right - actionWidth;
        int actionY = compact ? 106 : 66;

        if (state.configuredEnabled() || !state.activePackId().isBlank()) {
            addDrawableChild(new LazyBuilderSettingsControlWidget(
                    actionX,
                    actionY,
                    actionWidth,
                    22,
                    Text.literal("Disable Shader"),
                    true,
                    LazyBuilderSettingsControlWidget.Kind.ACTION,
                    this::disableShader
            ));
        } else if (!state.selectedPackId().isBlank()) {
            addDrawableChild(new LazyBuilderSettingsControlWidget(
                    actionX,
                    actionY,
                    actionWidth,
                    22,
                    Text.literal("Compile Selected"),
                    !state.compatibilityBlocked(),
                    LazyBuilderSettingsControlWidget.Kind.ACTION,
                    this::compileSelected
            ));
        }

        if (!state.selectedPackId().isBlank() && state.optionCount() > 0) {
            int optionsY = actionY + 30;
            addDrawableChild(new LazyBuilderSettingsControlWidget(
                    actionX,
                    optionsY,
                    actionWidth,
                    22,
                    Text.literal("Shader Options"),
                    !state.compatibilityBlocked(),
                    state.compatibilityBlocked()
                            ? LazyBuilderSettingsControlWidget.Kind.STATUS
                            : LazyBuilderSettingsControlWidget.Kind.ACTION,
                    () -> {
                        if (client != null) {
                            client.setScreen(new LazyBuilderShaderOptionsScreen(this));
                        }
                    }
            ));
        }

        int viewportBottom = footerTop - 8;
        int contentHeight = state.packs().size() * (ROW_HEIGHT + ROW_GAP);
        int viewportHeight = Math.max(1, viewportBottom - listTop);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int y = listTop - scrollOffset;
        for (ShaderPack pack : state.packs()) {
            if (y + ROW_HEIGHT > listTop && y < viewportBottom) {
                String suffix = pack.id().equals(state.activePackId())
                        ? "  ACTIVE"
                        : pack.id().equals(state.selectedPackId()) ? "  SELECTED" : "";
                addDrawableChild(new LazyBuilderSettingsControlWidget(
                        left,
                        y,
                        shell,
                        ROW_HEIGHT - 2,
                        Text.literal(pack.name() + suffix),
                        !state.compatibilityBlocked(),
                        state.compatibilityBlocked()
                                ? LazyBuilderSettingsControlWidget.Kind.STATUS
                                : LazyBuilderSettingsControlWidget.Kind.ACTION,
                        () -> selectAndCompile(pack.id())
                ));
            }
            y += ROW_HEIGHT + ROW_GAP;
        }

        if (!state.lastError().isBlank()) {
            addDrawableChild(new LazyBuilderSettingsControlWidget(
                    right - 192,
                    height - 30,
                    92,
                    22,
                    Text.literal("Retry"),
                    !state.compatibilityBlocked() && !state.selectedPackId().isBlank(),
                    LazyBuilderSettingsControlWidget.Kind.FOOTER,
                    this::compileSelected
            ));
        }
    }

    @Override
    public void tick() {
        long revision = snapshotRevision();
        if (revision != observedRevision) {
            observedRevision = revision;
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

        ShaderState state = currentState;
        int shell = Math.min(760, Math.max(280, width - 24));
        int left = (width - shell) / 2;
        int right = left + shell;
        boolean compact = shell < 540;

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("SHADERS"),
                left,
                15,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );

        int panelTop = 52;
        int panelBottom = compact ? 132 : 94;
        context.fill(left, panelTop, right, panelBottom, DIVIDER);
        context.fill(left + 1, panelTop + 1, right - 1, panelBottom - 1, PANEL);

        int textX = left + 12;
        int textY = panelTop + 10;
        String active = state.activePackName().isBlank()
                ? "No active shader"
                : state.activePackName();
        context.drawTextWithShadow(
                textRenderer,
                Text.literal(active),
                textX,
                textY,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );

        textY += 15;
        context.drawTextWithShadow(
                textRenderer,
                Text.literal(statusText(state)),
                textX,
                textY,
                state.lastError().isBlank()
                        ? LazyBuilderSettingsScreen.TEXT_SECONDARY
                        : 0xFFFFA7A7
        );

        if (!compact && !state.shadowStatus().isBlank() && !"not-configured".equals(state.shadowStatus())) {
            textY += 14;
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(textRenderer.trimToWidth(shadowLabel(state), shell - 210)),
                    textX,
                    textY,
                    LazyBuilderSettingsScreen.TEXT_MUTED
            );
        }

        if (compact) {
            textY += 14;
            String details = "Terrain " + yesNo(state.terrainIntegrated())
                    + "  •  Post " + yesNo(state.postProcessReady())
                    + "  •  " + shadowLabel(state);
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(textRenderer.trimToWidth(details, shell - 24)),
                    textX,
                    textY,
                    LazyBuilderSettingsScreen.TEXT_MUTED
            );
        }

        if (state.packs().isEmpty() && state.available()) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal("No LazyBuilder shader packs found."),
                    width / 2,
                    compact ? 166 : 128,
                    LazyBuilderSettingsScreen.TEXT_MUTED
            );
        }

        if (state.invalidPackCount() > 0) {
            String warning = state.invalidPackCount() == 1
                    ? "1 invalid shader pack was skipped."
                    : state.invalidPackCount() + " invalid shader packs were skipped.";
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(warning),
                    left,
                    height - FOOTER_HEIGHT - 18,
                    0xFFFFC47A
            );
        }

        if (!state.available()) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal("First-party shader runtime is unavailable."),
                    width / 2,
                    compact ? 166 : 128,
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

    private void refreshPacks() {
        Object action = share("lazybuilder-performance-manager:shader-refresh");
        if (action instanceof Runnable runnable) runnable.run();
    }

    @SuppressWarnings("unchecked")
    private void selectAndCompile(String packId) {
        Object select = share("lazybuilder-performance-manager:shader-select");
        if (select instanceof Consumer<?> raw) {
            ((Consumer<String>) raw).accept(packId);
        }
        compileSelected();
    }

    private void compileSelected() {
        Object action = share("lazybuilder-performance-manager:shader-compile");
        if (action instanceof Runnable runnable) runnable.run();
    }

    private void disableShader() {
        Object action = share("lazybuilder-performance-manager:shader-disable");
        if (action instanceof Runnable runnable) runnable.run();
    }

    private static void openFolder(String directory) {
        if (directory == null || directory.isBlank()) return;
        try {
            Util.getOperatingSystem().open(Path.of(directory));
        } catch (RuntimeException ignored) {
        }
    }

    private static Object share(String key) {
        return FabricLoader.getInstance().getObjectShare().get(key);
    }

    private long snapshotRevision() {
        Object shared = share("lazybuilder-performance-manager:shader-snapshot");
        if (!(shared instanceof Supplier<?> supplier)) return Long.MIN_VALUE;
        Object raw = supplier.get();
        if (!(raw instanceof Map<?, ?> values)) return Long.MIN_VALUE;
        return longValue(values, "revision", 0L);
    }

    @SuppressWarnings("unchecked")
    private ShaderState state() {
        Object shared = share("lazybuilder-performance-manager:shader-snapshot");
        if (!(shared instanceof Supplier<?> supplier)) {
            return ShaderState.unavailable();
        }
        Object raw = supplier.get();
        if (!(raw instanceof Map<?, ?> values)) {
            return ShaderState.unavailable();
        }

        List<String> ids = stringList(values.get("packIds"));
        List<String> names = stringList(values.get("packNames"));
        List<ShaderPack> packs = new ArrayList<>();
        int count = Math.min(ids.size(), names.size());
        for (int i = 0; i < count; i++) {
            packs.add(new ShaderPack(ids.get(i), names.get(i)));
        }

        return new ShaderState(
                true,
                longValue(values, "revision", 0L),
                stringValue(values, "stage", "unknown"),
                booleanValue(values, "compiledReady", false),
                booleanValue(values, "postProcessReady", false),
                booleanValue(values, "shadowReady", false),
                stringValue(values, "shadowStatus", ""),
                intValue(values, "shadowResolution", 0),
                booleanValue(values, "renderingReady", false),
                booleanValue(values, "terrainIntegrated", false),
                booleanValue(values, "configuredEnabled", false),
                stringValue(values, "selectedPackId", ""),
                stringValue(values, "selectedPackName", ""),
                stringValue(values, "activePackId", ""),
                stringValue(values, "activePackName", ""),
                stringValue(values, "shaderpacksDirectory", ""),
                stringValue(values, "lastError", ""),
                listSize(values.get("invalidPacks")),
                listSize(values.get("options")),
                booleanValue(values, "compatibilityBlocked", false),
                stringValue(values, "compatibilityOwner", ""),
                List.copyOf(packs)
        );
    }

    private static String statusText(ShaderState state) {
        if (state.compatibilityBlocked()) {
            return "Compatibility owner: "
                    + (state.compatibilityOwner().isBlank() ? "external renderer" : state.compatibilityOwner());
        }
        if (!state.lastError().isBlank()) return "Error: " + state.lastError();
        if ("catalog-refresh-queued".equals(state.stage())
                || "catalog-refresh-pending".equals(state.stage())
                || "catalog-scanning".equals(state.stage())) {
            return "Refreshing shader packs...";
        }
        if ("preparing".equals(state.stage()) || "prepare-queued".equals(state.stage())) {
            return "Preparing shader sources...";
        }
        if ("compiling".equals(state.stage()) || "compile-queued".equals(state.stage())) {
            return "Compiling shader...";
        }
        if (state.renderingReady()) return "First-party shader rendering active";
        if (state.terrainIntegrated()) return "Terrain shader active";
        if (state.compiledReady()) return "Compiled; waiting for terrain reload";
        if (!state.selectedPackName().isBlank()) return "Selected: " + state.selectedPackName();
        return "Off";
    }

    private static String shadowLabel(ShaderState state) {
        String status = state.shadowStatus();
        if ("ready-solid-only".equals(status) || state.shadowReady()) {
            String resolution = state.shadowResolution() > 0
                    ? " · " + state.shadowResolution() + "px"
                    : "";
            return "Shadow: Solid terrain" + resolution;
        }
        if ("no-visible-terrain".equals(status)) return "Shadow: Waiting for visible terrain";
        if (status == null || status.isBlank() || "not-configured".equals(status)) {
            return "Shadow: Off";
        }
        return "Shadow: " + status.replace('-', ' ');
    }

    private static String yesNo(boolean value) {
        return value ? "Ready" : "Off";
    }

    private static String stringValue(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String text ? text : fallback;
    }

    private static boolean booleanValue(Map<?, ?> values, String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static int intValue(Map<?, ?> values, String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static long longValue(Map<?, ?> values, String key, long fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof String text) result.add(text);
        }
        return List.copyOf(result);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    private record ShaderPack(String id, String name) {}

    private record ShaderState(
            boolean available,
            long revision,
            String stage,
            boolean compiledReady,
            boolean postProcessReady,
            boolean shadowReady,
            String shadowStatus,
            int shadowResolution,
            boolean renderingReady,
            boolean terrainIntegrated,
            boolean configuredEnabled,
            String selectedPackId,
            String selectedPackName,
            String activePackId,
            String activePackName,
            String directory,
            String lastError,
            int invalidPackCount,
            int optionCount,
            boolean compatibilityBlocked,
            String compatibilityOwner,
            List<ShaderPack> packs
    ) {
        static ShaderState unavailable() {
            return new ShaderState(
                    false, 0L, "runtime-unavailable",
                    false, false, false, "", 0, false, false, false,
                    "", "", "", "", "", "", 0, 0, false, "", List.of()
            );
        }
    }
}

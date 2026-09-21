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
import java.util.function.Supplier;

/**
 * LazyBuilder-first shader management surface.
 *
 * Performance Manager owns the first-party shader runtime through ObjectShare.
 * Iris is exposed only as an optional migration/compatibility screen while the
 * first-party terrain shader path is still being completed.
 */
public final class LazyBuilderShaderScreen extends Screen {
    private static final int BACKGROUND = 0xF20B0E12;
    private static final int TOP_BAR = 0xE813171C;
    private static final int PANEL = 0xA81A1F25;
    private static final int DIVIDER = 0x44545C66;
    private static final int FOOTER_HEIGHT = 40;

    private final Screen parent;
    private long observedRevision = -1L;
    private String compatibilityFailure;

    public LazyBuilderShaderScreen(Screen parent) {
        super(Text.literal("Shaders"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ShaderState state = shaderState();
        observedRevision = state.revision();

        int shell = Math.min(720, Math.max(280, width - 24));
        int left = (width - shell) / 2;
        int right = left + shell;
        boolean compact = shell < 500;

        List<Action> actions = new ArrayList<>();
        if (state.available()) {
            actions.add(new Action(
                    "Choose Pack",
                    !state.packIds().isEmpty(),
                    () -> {
                        if (client != null) client.setScreen(new LazyBuilderShaderPackScreen(this));
                    }
            ));
            actions.add(new Action(
                    state.compiledReady() ? "Recompile" : "Compile",
                    !state.selectedPackId().isBlank(),
                    () -> invoke("lazybuilder-performance-manager:shader-compile")
            ));
            actions.add(new Action(
                    "Disable",
                    state.compiledReady(),
                    () -> invoke("lazybuilder-performance-manager:shader-disable")
            ));
            actions.add(new Action(
                    "Refresh Packs",
                    true,
                    () -> invoke("lazybuilder-performance-manager:shader-refresh")
            ));
            actions.add(new Action(
                    "Open Folder",
                    !state.shaderpacksDirectory().isBlank(),
                    () -> openFolder(state.shaderpacksDirectory())
            ));
        }

        if (isIrisAvailable() && !state.terrainIntegrated()) {
            actions.add(new Action(
                    "Iris Compatibility",
                    true,
                    this::openIrisCompatibility
            ));
        }

        if (compact) {
            int gap = 6;
            int columnWidth = Math.max(120, (shell - 28 - gap) / 2);
            int startX = left + 14;
            int y = Math.min(134, Math.max(112, height - FOOTER_HEIGHT - 82));

            for (int index = 0; index < actions.size(); index++) {
                int column = index % 2;
                int row = index / 2;
                int x = startX + column * (columnWidth + gap);
                int rowY = y + row * 28;
                Action action = actions.get(index);
                addDrawableChild(new LazyBuilderSettingsControlWidget(
                        x,
                        rowY,
                        columnWidth,
                        22,
                        Text.literal(action.label()),
                        action.enabled(),
                        action.enabled()
                                ? LazyBuilderSettingsControlWidget.Kind.ACTION
                                : LazyBuilderSettingsControlWidget.Kind.STATUS,
                        action.action()
                ));
            }
        } else {
            int rowWidth = Math.min(200, Math.max(150, shell / 3));
            int x = right - rowWidth - 14;
            int y = 82;
            for (Action action : actions) {
                addDrawableChild(new LazyBuilderSettingsControlWidget(
                        x,
                        y,
                        rowWidth,
                        22,
                        Text.literal(action.label()),
                        action.enabled(),
                        action.enabled()
                                ? LazyBuilderSettingsControlWidget.Kind.ACTION
                                : LazyBuilderSettingsControlWidget.Kind.STATUS,
                        action.action()
                ));
                y += 32;
            }
        }

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

    @Override
    public void tick() {
        ShaderState state = shaderState();
        if (state.revision() != observedRevision) {
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
        int right = left + shell;
        boolean compact = shell < 500;

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("SHADERS"),
                left,
                15,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );

        int panelTop = 64;
        int panelBottom = Math.max(
                panelTop + 100,
                Math.min(height - FOOTER_HEIGHT - 12, compact ? 198 : 284)
        );
        context.fill(left, panelTop, right, panelBottom, DIVIDER);
        context.fill(left + 1, panelTop + 1, right - 1, panelBottom - 1, PANEL);

        ShaderState state = shaderState();
        int textX = left + 14;
        int textY = 82;
        int textWidth = compact ? shell - 28 : Math.max(140, shell - 240);

        String title = !state.available()
                ? "Shader Runtime Unavailable"
                : !state.activePackName().isBlank()
                        ? state.activePackName()
                        : !state.selectedPackName().isBlank()
                                ? state.selectedPackName()
                                : "No Shader Pack Selected";
        context.drawTextWithShadow(
                textRenderer,
                Text.literal(textRenderer.trimToWidth(title, textWidth)),
                textX,
                textY,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );

        textY += 16;
        context.drawTextWithShadow(
                textRenderer,
                Text.literal(statusLabel(state)),
                textX,
                textY,
                state.renderingReady()
                        ? LazyBuilderSettingsScreen.ACCENT
                        : LazyBuilderSettingsScreen.TEXT_SECONDARY
        );

        textY += 22;
        String description;
        if (!state.available()) {
            description = "Performance Manager shader runtime is not available in this client package.";
        } else if (state.terrainIntegrated()) {
            description = "LazyBuilder owns shader pack loading, compilation, terrain integration, and post-processing.";
        } else {
            description = "LazyBuilder now owns shader pack discovery, preprocessing, compilation, and post-processing. Terrain shader integration is still pending, so Iris remains optional compatibility for full terrain shader packs.";
        }

        for (var line : textRenderer.wrapLines(Text.literal(description), textWidth)) {
            if (textY >= panelBottom - 26) break;
            context.drawTextWithShadow(
                    textRenderer,
                    line,
                    textX,
                    textY,
                    LazyBuilderSettingsScreen.TEXT_MUTED
            );
            textY += 11;
        }

        String error = !state.lastError().isBlank() ? state.lastError() : compatibilityFailure;
        if (error != null && !error.isBlank()) {
            int errorY = Math.min(panelBottom - 14, textY + 8);
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(textRenderer.trimToWidth("Error: " + error, textWidth)),
                    textX,
                    errorY,
                    0xFFFFA7A7
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private static String statusLabel(ShaderState state) {
        if (!state.available()) return "Unavailable";
        if (state.renderingReady()) return "First-party post-process active";
        if (state.compiledReady() && state.postProcessReady()) return "Compiled — waiting for frame";
        if (state.compiledReady()) return "Compiled — no composite/final pass";
        return switch (state.stage()) {
            case "compile-queued" -> "Compile queued";
            case "compiling" -> "Compiling";
            case "compile-error" -> "Compile failed";
            case "selected" -> "Ready to compile";
            case "disabled" -> "Disabled";
            case "render-error" -> "Render pass failed";
            default -> state.packIds().isEmpty() ? "No packs found" : "Ready";
        };
    }

    @SuppressWarnings("unchecked")
    private ShaderState shaderState() {
        Object shared = FabricLoader.getInstance().getObjectShare()
                .get("lazybuilder-performance-manager:shader-snapshot");
        if (!(shared instanceof Supplier<?> supplier)) return ShaderState.EMPTY;
        Object value = supplier.get();
        if (!(value instanceof Map<?, ?> map)) return ShaderState.EMPTY;

        return new ShaderState(
                true,
                longValue(map, "revision"),
                stringValue(map, "stage"),
                booleanValue(map, "compiledReady"),
                booleanValue(map, "postProcessReady"),
                booleanValue(map, "renderingReady"),
                booleanValue(map, "terrainIntegrated"),
                stringValue(map, "selectedPackId"),
                stringValue(map, "selectedPackName"),
                stringValue(map, "activePackName"),
                stringList(map.get("packIds")),
                stringValue(map, "shaderpacksDirectory"),
                stringValue(map, "lastError")
        );
    }

    private void invoke(String key) {
        Object shared = FabricLoader.getInstance().getObjectShare().get(key);
        if (shared instanceof Runnable action) action.run();
    }

    private void openFolder(String path) {
        try {
            Util.getOperatingSystem().open(Path.of(path));
        } catch (RuntimeException error) {
            compatibilityFailure = error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
            clearAndInit();
        }
    }

    private boolean isIrisAvailable() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    private void openIrisCompatibility() {
        compatibilityFailure = null;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object screen = apiClass.getMethod("openMainIrisScreenObj", Object.class).invoke(api, this);
            if (screen instanceof Screen irisScreen && client != null) {
                client.setScreen(irisScreen);
                return;
            }
            compatibilityFailure = "Iris did not provide a compatibility screen.";
            clearAndInit();
        } catch (ReflectiveOperationException | RuntimeException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            compatibilityFailure = cause.getMessage() == null
                    ? cause.getClass().getSimpleName()
                    : cause.getMessage();
            clearAndInit();
        }
    }

    private static boolean booleanValue(Map<?, ?> map, String key) {
        return map.get(key) instanceof Boolean value && value;
    }

    private static long longValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : -1L;
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
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    private record Action(String label, boolean enabled, Runnable action) {}

    private record ShaderState(
            boolean available,
            long revision,
            String stage,
            boolean compiledReady,
            boolean postProcessReady,
            boolean renderingReady,
            boolean terrainIntegrated,
            String selectedPackId,
            String selectedPackName,
            String activePackName,
            List<String> packIds,
            String shaderpacksDirectory,
            String lastError
    ) {
        private static final ShaderState EMPTY = new ShaderState(
                false,
                -1L,
                "runtime-unavailable",
                false,
                false,
                false,
                false,
                "",
                "",
                "",
                List.of(),
                "",
                ""
        );

        private ShaderState {
            stage = stage == null ? "" : stage;
            selectedPackId = selectedPackId == null ? "" : selectedPackId;
            selectedPackName = selectedPackName == null ? "" : selectedPackName;
            activePackName = activePackName == null ? "" : activePackName;
            packIds = packIds == null ? List.of() : List.copyOf(packIds);
            shaderpacksDirectory = shaderpacksDirectory == null ? "" : shaderpacksDirectory;
            lastError = lastError == null ? "" : lastError;
        }
    }
}

package com.halokaryamedia.lazybuilder.performance;

import com.halokaryamedia.lazybuilder.performance.compatibility.FirstPartyRendererReadiness;
import com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility;
import com.halokaryamedia.lazybuilder.performance.compatibility.RendererCompatibility;
import com.halokaryamedia.lazybuilder.performance.memory.MemoryDeduplicator;
import com.halokaryamedia.lazybuilder.performance.rendering.PerformanceShaderReloadInvalidator;
import com.halokaryamedia.lazybuilder.performance.shader.FirstPartyShaderRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourceType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


/** Fabric client entrypoint for LazyBuilder Performance Manager. */
public final class PerformanceManagerClient implements ClientModInitializer {
    private static final Matrix4f VIEW_PROJECTION_SCRATCH = new Matrix4f();
    private static final Matrix4f INVERSE_VIEW_PROJECTION_SCRATCH = new Matrix4f();

    private static PerformanceRuntime runtime;
    private static FirstPartyShaderRuntime shaderRuntime;

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        runtime = new PerformanceRuntime(loader.getConfigDir());
        shaderRuntime = new FirstPartyShaderRuntime(
                loader.getGameDir().resolve("shaderpacks"),
                loader.getConfigDir()
        );
        var share = loader.getObjectShare();
        share.put(
                "lazybuilder-performance-manager:settings-snapshot",
                (Supplier<Map<String, Object>>) PerformanceManagerClient::settingsSnapshot
        );
        share.put(
                "lazybuilder-performance-manager:settings-update",
                (Consumer<Map<String, Object>>) PerformanceManagerClient::applySharedSettings
        );
        share.put(
                "lazybuilder-performance-manager:settings-status",
                (Supplier<String>) PerformanceManagerClient::lastPreferenceUpdateStatus
        );
        share.put(
                "lazybuilder-performance-manager:renderer-readiness",
                (Supplier<Map<String, Object>>) PerformanceManagerClient::rendererReadinessSnapshot
        );
        share.put(
                "lazybuilder-performance-manager:shader-snapshot",
                (Supplier<Map<String, Object>>) PerformanceManagerClient::shaderSnapshot
        );
        share.put(
                "lazybuilder-performance-manager:shader-refresh",
                (Runnable) PerformanceManagerClient::refreshShaderPacks
        );
        share.put(
                "lazybuilder-performance-manager:shader-select",
                (Consumer<String>) PerformanceManagerClient::selectShaderPack
        );
        share.put(
                "lazybuilder-performance-manager:shader-compile",
                (Runnable) PerformanceManagerClient::compileSelectedShaderPack
        );
        share.put(
                "lazybuilder-performance-manager:shader-disable",
                (Runnable) PerformanceManagerClient::disableShaderPipeline
        );
        share.put(
                "lazybuilder-performance-manager:shader-option-update",
                (BiConsumer<String, String>) PerformanceManagerClient::updateShaderOption
        );
        share.put(
                "lazybuilder-performance-manager:shader-options-apply",
                (Runnable) PerformanceManagerClient::applyShaderOptions
        );
        share.put(
                "lazybuilder-performance-manager:shader-preprocess",
                (Function<String, Map<String, Object>>) PerformanceManagerClient::preprocessShaderSource
        );
        MemoryDeduplicator.register();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new PerformanceShaderReloadInvalidator());

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (shaderRuntime != null && firstPartyShaderOwnershipAllowed()) {
                shaderRuntime.activateConfiguredSelection();
            }
        });

        WorldRenderEvents.START.register(context -> beginFirstPartyShaderFrame());
        WorldRenderEvents.AFTER_SETUP.register(PerformanceManagerClient::renderFirstPartyShadow);
        WorldRenderEvents.END.register(context -> {
            long now = System.nanoTime();
            runtime.recordFrame(now);
            renderFirstPartyShaderFrame(context, now);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            runtime.tick(client);
            FirstPartyShaderRuntime shaders = shaderRuntime;
            if (firstPartyShaderOwnershipAllowed()
                    && shaders != null
                    && shaders.consumeTerrainReloadRequest()) {
                client.reloadResources();
            }
        });
    }

    private static void renderFirstPartyShadow(WorldRenderContext context) {
        FirstPartyShaderRuntime shaders = shaderRuntime;
        if (!firstPartyShaderOwnershipAllowed()
                || shaders == null
                || context == null
                || context.camera() == null
                || context.world() == null) {
            return;
        }

        var position = context.camera().getPos();
        shaders.renderShadow(
                position.getX(),
                position.getY(),
                position.getZ(),
                context.world().getTimeOfDay()
        );
    }

    private static void beginFirstPartyShaderFrame() {
        FirstPartyShaderRuntime shaders = shaderRuntime;
        if (!firstPartyShaderOwnershipAllowed() || shaders == null) return;

        FirstPartyShaderRuntime.Snapshot snapshot = shaders.snapshot();
        if (!snapshot.compiledReady()
                || !snapshot.postProcessReady()
                || snapshot.gbufferAttachments() <= 0) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getWindow().isMinimized()) return;

        var framebuffer = client.getFramebuffer();
        if (framebuffer == null || framebuffer.textureWidth <= 0 || framebuffer.textureHeight <= 0) return;

        shaders.beginGBufferFrame(
                framebuffer.fbo,
                framebuffer.textureWidth,
                framebuffer.textureHeight
        );
    }

    private static void renderFirstPartyShaderFrame(
            WorldRenderContext context,
            long nowNanos
    ) {
        FirstPartyShaderRuntime shaders = shaderRuntime;
        if (!firstPartyShaderOwnershipAllowed() || shaders == null) return;

        FirstPartyShaderRuntime.Snapshot snapshot = shaders.snapshot();
        if (!snapshot.compiledReady() || !snapshot.postProcessReady()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getWindow().isMinimized()) return;

        var framebuffer = client.getFramebuffer();
        if (framebuffer == null) return;

        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;
        if (width <= 0 || height <= 0) return;

        int targetFramebuffer = framebuffer.fbo;
        int sourceDepthTexture = framebuffer.useDepthAttachment
                ? framebuffer.getDepthAttachment()
                : 0;
        var gbuffer = shaders.endGBufferFrame(targetFramebuffer);

        Matrix4f inverseViewProjection = null;
        float cameraX = 0.0F;
        float cameraY = 0.0F;
        float cameraZ = 0.0F;
        if (context != null && context.camera() != null) {
            var camera = context.camera().getPos();
            cameraX = (float) camera.getX();
            cameraY = (float) camera.getY();
            cameraZ = (float) camera.getZ();
        }
        if (context != null
                && context.projectionMatrix() != null
                && context.positionMatrix() != null) {
            VIEW_PROJECTION_SCRATCH
                    .set(context.projectionMatrix())
                    .mul(context.positionMatrix());
            VIEW_PROJECTION_SCRATCH.invert(INVERSE_VIEW_PROJECTION_SCRATCH);
            inverseViewProjection = INVERSE_VIEW_PROJECTION_SCRATCH;
        }

        float timeSeconds = (float) ((nowNanos / 1_000_000L) % 3_600_000L) / 1000.0F;
        shaders.renderPostProcess(
                targetFramebuffer,
                sourceDepthTexture,
                gbuffer.texture1(),
                gbuffer.texture2(),
                inverseViewProjection,
                cameraX,
                cameraY,
                cameraZ,
                width,
                height,
                timeSeconds
        );
    }

    private static Map<String, Object> rendererReadinessSnapshot() {
        FabricLoader loader = FabricLoader.getInstance();
        RendererCompatibility.Snapshot renderer = RendererCompatibility.detect();
        OptimizationCompatibility.Policy policy = OptimizationCompatibility.evaluate(
                renderer,
                loader.isModLoaded("immediatelyfast"),
                loader.isModLoaded("entityculling")
        );
        FirstPartyRendererReadiness.Snapshot readiness =
                FirstPartyRendererReadiness.evaluate(renderer, policy);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("ready", readiness.ready());
        values.put("status", readiness.status());
        values.put("blockers", readiness.blockers());
        values.put("rendererOwner", renderer.ownerSummary());
        return Map.copyOf(values);
    }

    public static Map<String, Object> currentShaderSnapshot() {
        return shaderSnapshot();
    }

    private static Map<String, Object> shaderSnapshot() {
        if (shaderRuntime == null) {
            return Map.of(
                    "owner", "lazybuilder",
                    "stage", "runtime-unavailable",
                    "renderingReady", false
            );
        }

        Map<String, Object> base = shaderRuntime.snapshotMap();
        RendererCompatibility.Snapshot renderer = RendererCompatibility.detect();
        if (firstPartyShaderOwnershipAllowed(renderer)) return base;

        Map<String, Object> values = new LinkedHashMap<>(base);
        values.put("stage", "external-owner");
        values.put("renderingReady", false);
        values.put("terrainIntegrated", false);
        values.put("compatibilityBlocked", true);
        values.put("compatibilityOwner", renderer.ownerSummary());
        values.put(
                "lastError",
                "First-party shaders are disabled while " + renderer.ownerSummary()
                        + " owns the renderer/shader path."
        );
        return Map.copyOf(values);
    }

    private static void refreshShaderPacks() {
        if (shaderRuntime != null) shaderRuntime.refresh();
    }

    private static void selectShaderPack(String packId) {
        if (shaderRuntime != null) shaderRuntime.select(packId);
    }

    private static void compileSelectedShaderPack() {
        if (shaderRuntime != null && firstPartyShaderOwnershipAllowed()) {
            shaderRuntime.compileSelected();
        }
    }

    private static void disableShaderPipeline() {
        if (shaderRuntime != null) shaderRuntime.disable();
    }

    private static void updateShaderOption(String optionId, String value) {
        FirstPartyShaderRuntime shaders = shaderRuntime;
        if (shaders == null) return;
        shaders.updateOption(optionId, value, false);
    }

    private static void applyShaderOptions() {
        FirstPartyShaderRuntime shaders = shaderRuntime;
        if (shaders == null || !firstPartyShaderOwnershipAllowed()) return;
        shaders.recompileIfEnabled();
    }

    public static void invalidateShaderTerrainForResourceReload() {
        if (shaderRuntime != null) shaderRuntime.invalidateTerrainIntegrationForResourceReload();
    }

    public static FirstPartyShaderRuntime.TerrainSource firstPartyTerrainSource(boolean vertex) {
        FirstPartyShaderRuntime shaders = shaderRuntime;
        return shaders == null
                ? new FirstPartyShaderRuntime.TerrainSource(false, "", "", "Shader runtime unavailable.")
                : shaders.terrainSource(vertex);
    }

    public static void recordFirstPartyTerrainCompile(
            boolean vertex,
            boolean success,
            String error
    ) {
        if (shaderRuntime != null) {
            shaderRuntime.recordTerrainCompile(vertex, success, error);
        }
    }

    public static void recordFirstPartyTerrainProgramLinked() {
        if (shaderRuntime != null) shaderRuntime.recordTerrainProgramLinked();
    }

    private static Map<String, Object> preprocessShaderSource(String path) {
        if (shaderRuntime == null) {
            return Map.of(
                    "ready", false,
                    "source", "",
                    "dependencies", java.util.List.of(),
                    "error", "Shader runtime unavailable."
            );
        }

        FirstPartyShaderRuntime.SourcePreview result = shaderRuntime.preprocess(path);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("ready", result.ready());
        values.put("source", result.source());
        values.put("dependencies", result.dependencies());
        values.put("error", result.error());
        return Map.copyOf(values);
    }

    private static boolean firstPartyShaderOwnershipAllowed() {
        return firstPartyShaderOwnershipAllowed(RendererCompatibility.detect());
    }

    private static boolean firstPartyShaderOwnershipAllowed(RendererCompatibility.Snapshot renderer) {
        return renderer != null
                && !renderer.uncertain()
                && !renderer.customRendererPresent()
                && !renderer.irisPresent();
    }

    private static Map<String, Object> settingsSnapshot() {
        PerformancePreferences preferences = preferences();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("backgroundFpsPolicy", preferences.backgroundFpsPolicy());
        snapshot.put("unfocusedFpsLimit", preferences.unfocusedFpsLimit());
        snapshot.put("minimizedFpsLimit", preferences.minimizedFpsLimit());
        snapshot.put("hiddenObjectSkipping", preferences.hiddenObjectSkipping());
        snapshot.put("renderingOptimizations", preferences.renderingOptimizations());
        snapshot.put("memoryOptimizations", preferences.memoryOptimizations());
        return Map.copyOf(snapshot);
    }

    private static void applySharedSettings(Map<String, Object> values) {
        if (values == null) return;
        PerformancePreferences current = preferences();

        boolean background = booleanValue(values, "backgroundFpsPolicy", current.backgroundFpsPolicy());
        int unfocused = intValue(values, "unfocusedFpsLimit", current.unfocusedFpsLimit());
        int minimized = intValue(values, "minimizedFpsLimit", current.minimizedFpsLimit());
        boolean hidden = booleanValue(values, "hiddenObjectSkipping", current.hiddenObjectSkipping());
        boolean rendering = booleanValue(values, "renderingOptimizations", current.renderingOptimizations());
        boolean memory = booleanValue(values, "memoryOptimizations", current.memoryOptimizations());

        updatePreferences(new PerformancePreferences(
                background,
                unfocused,
                minimized,
                hidden,
                hidden,
                rendering,
                memory
        ));
    }

    private static boolean booleanValue(Map<String, Object> values, String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    private static int intValue(Map<String, Object> values, String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public static FramePressure pressure() {
        return runtime == null ? FramePressure.NORMAL : runtime.pressure();
    }

    public static PerformancePreferences preferences() {
        return runtime == null ? PerformancePreferences.defaults() : runtime.preferences();
    }

    public static void updatePreferences(PerformancePreferences updated) {
        if (runtime != null) runtime.updatePreferences(updated);
    }

    public static String lastPreferenceUpdateStatus() {
        return runtime == null ? "runtime-unavailable" : runtime.lastPreferenceUpdateStatus();
    }

    public static boolean shouldRenderEntity(Entity entity) {
        return runtime == null || runtime.shouldRender(entity);
    }

    public static <E extends BlockEntity> boolean shouldRenderBlockEntity(
            E blockEntity,
            BlockEntityRenderer<E> renderer
    ) {
        return runtime == null || runtime.shouldRender(blockEntity, renderer);
    }

    /** Captures current diagnostics on demand; no metrics history database is maintained. */
    public static PerformanceSnapshot currentSnapshot() {
        return runtime == null ? null : runtime.snapshot();
    }
}

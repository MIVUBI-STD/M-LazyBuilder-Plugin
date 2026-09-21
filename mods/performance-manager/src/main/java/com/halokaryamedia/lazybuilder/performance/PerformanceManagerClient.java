package com.halokaryamedia.lazybuilder.performance;

import com.halokaryamedia.lazybuilder.performance.compatibility.FirstPartyRendererReadiness;
import com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility;
import com.halokaryamedia.lazybuilder.performance.compatibility.RendererCompatibility;
import com.halokaryamedia.lazybuilder.performance.memory.MemoryDeduplicator;
import com.halokaryamedia.lazybuilder.performance.rendering.PerformanceShaderReloadInvalidator;
import com.halokaryamedia.lazybuilder.performance.shader.FirstPartyShaderRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourceType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.entity.Entity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


/** Fabric client entrypoint for LazyBuilder Performance Manager. */
public final class PerformanceManagerClient implements ClientModInitializer {
    private static PerformanceRuntime runtime;
    private static FirstPartyShaderRuntime shaderRuntime;

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        runtime = new PerformanceRuntime(loader.getConfigDir());
        shaderRuntime = new FirstPartyShaderRuntime(loader.getGameDir().resolve("shaderpacks"));
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
                "lazybuilder-performance-manager:shader-preprocess",
                (Function<String, Map<String, Object>>) PerformanceManagerClient::preprocessShaderSource
        );
        MemoryDeduplicator.register();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new PerformanceShaderReloadInvalidator());

        WorldRenderEvents.END.register(context ->
                runtime.recordFrame(System.nanoTime())
        );

        ClientTickEvents.END_CLIENT_TICK.register(runtime::tick);
    }

    private static Map<String, Object> rendererReadinessSnapshot() {
        FabricLoader loader = FabricLoader.getInstance();
        RendererCompatibility.Snapshot renderer = RendererCompatibility.detect();
        OptimizationCompatibility.Policy policy = OptimizationCompatibility.evaluate(
                renderer,
                loader.isModLoaded("immediatelyfast"),
                loader.isModLoaded("ferritecore"),
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

    private static Map<String, Object> shaderSnapshot() {
        return shaderRuntime == null ? Map.of(
                "owner", "lazybuilder",
                "stage", "runtime-unavailable",
                "renderingReady", false
        ) : shaderRuntime.snapshotMap();
    }

    private static void refreshShaderPacks() {
        if (shaderRuntime != null) shaderRuntime.refresh();
    }

    private static void selectShaderPack(String packId) {
        if (shaderRuntime != null) shaderRuntime.select(packId);
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

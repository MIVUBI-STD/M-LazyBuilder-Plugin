package com.halokaryamedia.lazybuilder.performance;

import com.halokaryamedia.lazybuilder.performance.memory.MemoryDeduplicator;
import com.halokaryamedia.lazybuilder.performance.rendering.PerformanceShaderReloadInvalidator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourceType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Function;

/** Fabric client entrypoint for LazyBuilder Performance Manager. */
public final class PerformanceManagerClient implements ClientModInitializer {
    private static PerformanceRuntime runtime;

    @Override
    public void onInitializeClient() {
        runtime = new PerformanceRuntime(FabricLoader.getInstance().getConfigDir());
        FabricLoader.getInstance().getObjectShare().put(
                "lazybuilder-performance-manager:settings-screen",
                (Function<Screen, Screen>) com.halokaryamedia.lazybuilder.performance.ui.PerformanceVideoSettingsScreen::new
        );
        MemoryDeduplicator.register();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new PerformanceShaderReloadInvalidator());

        WorldRenderEvents.END.register(context ->
                runtime.recordFrame(System.nanoTime())
        );

        ClientTickEvents.END_CLIENT_TICK.register(runtime::tick);
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

package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.axiom.AxiomArrayTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomClientServices;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomCompatibility;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomProceduralTextureTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomOperationCenterTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomRecoveryTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomScatterTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomSchematicCatalogTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomSchematicDistributionTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomSplinePreviewTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomSplineSchematicTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomStructureStampTool;
import com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionClientNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuilderUtilitiesClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder Builder Utilities");
    private static BuilderRuntime runtime;
    private static AxiomRecoveryTool recoveryTool;
    private static ClientWorld observedWorld;
    private static String observedScope;
    private static int transitionRetryTicks;

    @Override
    public void onInitializeClient() {
        AxiomCompatibility compatibility = AxiomCompatibility.current();
        AxiomClientServices services = AxiomClientServices.load();
        BuilderExtensionClientNetworking.register();
        runtime = BuilderRuntime.createDefault();
        services.toolRegistry().register(new AxiomSplinePreviewTool(services, runtime));
        services.toolRegistry().register(new AxiomSplineSchematicTool(services, runtime));
        services.toolRegistry().register(new AxiomArrayTool(services, runtime));
        services.toolRegistry().register(new AxiomScatterTool(services, runtime));
        services.toolRegistry().register(new AxiomProceduralTextureTool(services, runtime));
        services.toolRegistry().register(new AxiomStructureStampTool(services, runtime));
        services.toolRegistry().register(new AxiomSchematicCatalogTool(services, runtime));
        services.toolRegistry().register(new AxiomSchematicDistributionTool(services, runtime));
        recoveryTool = new AxiomRecoveryTool(services, runtime);
        services.toolRegistry().register(recoveryTool);
        services.toolRegistry().register(new AxiomOperationCenterTool(services, runtime));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> closeRuntime());
        ClientTickEvents.END_CLIENT_TICK.register(BuilderUtilitiesClient::clientTick);
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> resetWorldTimeline());
        LOGGER.info(
                "Builder Utilities attached to {} with durable block spline, schematic spline, array, scatter, procedural texturing, structure stamping, schematic catalog/distribution, and restart recovery.",
                compatibility.summary());
    }
    /**
     * The single Builder execution path: advance active operations once per client tick,
     * independent of which Axiom tool is rendered. World changes first preserve durable
     * work and isolate the old timeline, then admit the new ClientWorld.
     */
    private static void clientTick(MinecraftClient client) {
        BuilderRuntime current = runtime;
        if (current == null) return;
        String scope = null;
        if (client.world != null) {
            try {
                scope = com.halokaryamedia.lazybuilder.builder.axiom.AxiomWorldScope
                        .currentScopeId(client.world);
            } catch (IllegalStateException unavailable) {
                // A scope lost on an otherwise live connection must not strand
                // an in-flight operation. Detach its durable journal even if
                // the new Paper identity never arrives.
                current.suspendWorldAdmission();
                if (observedWorld != null) {
                    if (transitionRetryTicks > 0) {
                        transitionRetryTicks--;
                    } else if (resetWorldTimeline()) {
                        observedWorld = null;
                        observedScope = null;
                        transitionRetryTicks = 0;
                    } else {
                        transitionRetryTicks = 100;
                    }
                }
                return;
            }
        }
        if (client.world != observedWorld || !java.util.Objects.equals(scope, observedScope)) {
            current.suspendWorldAdmission();
            if (transitionRetryTicks > 0) {
                transitionRetryTicks--;
                return;
            }
            if (observedWorld != null && !resetWorldTimeline()) {
                transitionRetryTicks = 100;
                return;
            }
            observedWorld = client.world;
            observedScope = scope;
            transitionRetryTicks = 0;
            if (observedWorld != null) {
                current.admitWorld(observedWorld);
                scanRecoveryNotice();
            }
        }
        if (client.world != null) current.pumpActiveOperations(client.world);
    }

    private static void scanRecoveryNotice() {
        BuilderRuntime current = runtime;
        if (current == null) return;
        try {
            String scope = com.halokaryamedia.lazybuilder.builder.axiom.AxiomWorldScope
                    .currentScopeId();
            var recovery = new com.halokaryamedia.lazybuilder.builder.history.HistoryRecoveryManager(
                    current.diskHistory());
            var filter = (java.util.function.Predicate<String>) operationId ->
                    com.halokaryamedia.lazybuilder.builder.history.ScopedOperationIds
                            .belongsTo(operationId, scope);
            int committed = recovery.committedSummaries(filter).size();
            int incomplete = recovery.incompleteFiles(filter).size();
            int legacyCommitted = recovery.unscopedCommittedSummaries().size();
            int legacyIncomplete = recovery.unscopedIncompleteFiles().size();
            int unreadableIncomplete = recovery.unreadableIncompleteFiles().size();
            current.recoveryNotice().update(
                    scope,
                    committed,
                    Math.addExact(incomplete, unreadableIncomplete));
            if (unreadableIncomplete > 0) {
                LOGGER.warn(
                        "Unreadable Builder recovery journals detected: incomplete={}. "
                                + "They are quarantined because world ownership cannot be proven.",
                        unreadableIncomplete);
            }
            if (legacyCommitted > 0 || legacyIncomplete > 0) {
                LOGGER.warn(
                        "Legacy unscoped Builder recovery journals detected: committed={}, incomplete={}. "
                                + "They are intentionally not attached to the current world automatically.",
                        legacyCommitted,
                        legacyIncomplete);
            }
            if (committed > 0 || incomplete > 0) {
                LOGGER.warn(
                        "LazyBuilder recovery work detected for current world: committed={}, incomplete={}. "
                                + "Open LazyBuilder Recovery or Operation Center to inspect it.",
                        committed,
                        incomplete);
            }
        } catch (Exception e) {
            current.recoveryNotice().failure(
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            LOGGER.error("Failed to inspect LazyBuilder recovery journals on world join", e);
        }
    }

    private static boolean resetWorldTimeline() {
        BuilderRuntime current = runtime;
        if (current == null) return true;
        current.suspendWorldAdmission();

        try {
            current.preserveActiveOperations();
        } catch (java.io.IOException e) {
            LOGGER.error(
                    "Failed to preserve one or more active Builder operations on world exit. "
                            + "World timeline and proof were intentionally left unchanged.",
                    e);
            return false;
        }

        try {
            AxiomRecoveryTool recovery = recoveryTool;
            if (recovery != null) recovery.releaseForWorldExit();
        } catch (java.io.IOException e) {
            LOGGER.error(
                    "Failed to release Builder recovery wrappers on world exit. "
                            + "World timeline was intentionally left unchanged.",
                    e);
            return false;
        }

        try {
            current.saveRuntimeProof("world-exit");
        } catch (java.io.IOException e) {
            LOGGER.error(
                    "Failed to persist Builder runtime proof on world exit. "
                            + "World isolation will continue without publishing this proof.",
                    e);
        }

        try {
            current.resetWorldTimeline();
            return true;
        } catch (java.io.IOException e) {
            LOGGER.error("Failed to reset Builder history after world exit", e);
            return false;
        }
    }

    private static void closeRuntime() {
        BuilderRuntime current = runtime;
        runtime = null;
        recoveryTool = null;
        observedWorld = null;
        observedScope = null;
        transitionRetryTicks = 0;
        if (current == null) return;
        current.suspendWorldAdmission();
        try {
            current.close();
        } catch (java.io.IOException e) {
            LOGGER.error("Failed to close Builder Utilities runtime cleanly", e);
        }
    }
}

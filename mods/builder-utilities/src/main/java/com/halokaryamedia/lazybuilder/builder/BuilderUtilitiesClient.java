package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.axiom.AxiomArrayTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomClientServices;
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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuilderUtilitiesClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder Builder Utilities");
    private static BuilderRuntime runtime;

    @Override
    public void onInitializeClient() {
        BuilderExtensionClientNetworking.register();
        AxiomClientServices services = AxiomClientServices.load();
        runtime = BuilderRuntime.createDefault();
        services.toolRegistry().register(new AxiomSplinePreviewTool(services, runtime));
        services.toolRegistry().register(new AxiomSplineSchematicTool(services, runtime));
        services.toolRegistry().register(new AxiomArrayTool(services, runtime));
        services.toolRegistry().register(new AxiomScatterTool(services, runtime));
        services.toolRegistry().register(new AxiomProceduralTextureTool(services, runtime));
        services.toolRegistry().register(new AxiomStructureStampTool(services, runtime));
        services.toolRegistry().register(new AxiomSchematicCatalogTool(services, runtime));
        services.toolRegistry().register(new AxiomSchematicDistributionTool(services, runtime));
        services.toolRegistry().register(new AxiomRecoveryTool(services, runtime));
        services.toolRegistry().register(new AxiomOperationCenterTool(services, runtime));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> closeRuntime());
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> client.execute(BuilderUtilitiesClient::scanRecoveryNotice));
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> resetWorldTimeline());
        LOGGER.info("Builder Utilities attached to Axiom public API with durable block spline, schematic spline, array, scatter, procedural texturing, structure stamping, schematic catalog/distribution, and restart recovery.");
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
            current.recoveryNotice().update(scope, committed, incomplete);
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

    private static void resetWorldTimeline() {
        BuilderRuntime current = runtime;
        if (current == null) return;
        java.io.IOException failure = null;
        try {
            current.preserveActiveOperations();
        } catch (java.io.IOException e) {
            failure = e;
            LOGGER.error(
                    "Failed to preserve one or more active Builder operations on disconnect", e);
        }
        try {
            current.resetWorldTimeline();
        } catch (java.io.IOException e) {
            if (failure != null) e.addSuppressed(failure);
            LOGGER.error("Failed to reset Builder history after world disconnect", e);
        }
    }

    private static void closeRuntime() {
        BuilderRuntime current = runtime;
        runtime = null;
        if (current == null) return;
        try {
            current.close();
        } catch (java.io.IOException e) {
            LOGGER.error("Failed to close Builder Utilities runtime cleanly", e);
        }
    }
}

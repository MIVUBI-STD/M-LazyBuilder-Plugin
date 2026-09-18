package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.axiom.AxiomArrayTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomClientServices;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomProceduralTextureTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomRecoveryTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomScatterTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomSchematicCatalogTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomSchematicDistributionTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomSplinePreviewTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomSplineSchematicTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomStructureStampTool;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuilderUtilitiesClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder Builder Utilities");
    private static BuilderRuntime runtime;

    @Override
    public void onInitializeClient() {
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
        LOGGER.info("Builder Utilities attached to Axiom public API with durable block spline, schematic spline, array, scatter, procedural texturing, structure stamping, schematic catalog/distribution, and restart recovery.");
    }
}

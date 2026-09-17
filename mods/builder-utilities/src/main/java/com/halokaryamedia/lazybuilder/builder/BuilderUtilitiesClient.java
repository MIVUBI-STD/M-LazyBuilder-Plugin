package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.axiom.AxiomClientServices;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomScatterTool;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomSplinePreviewTool;
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
        services.toolRegistry().register(new AxiomScatterTool(services, runtime));
        LOGGER.info("Builder Utilities attached to Axiom public API with durable budgeted spline and scatter mutation enabled.");
    }
}

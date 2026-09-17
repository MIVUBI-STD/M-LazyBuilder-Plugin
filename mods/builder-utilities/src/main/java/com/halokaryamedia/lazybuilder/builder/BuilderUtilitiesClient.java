package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.axiom.AxiomClientServices;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuilderUtilitiesClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder Builder Utilities");

    @Override
    public void onInitializeClient() {
        AxiomClientServices services = AxiomClientServices.load();
        LOGGER.info(
                "Builder Utilities attached to Axiom public client API (tool registry={}, tool service={}, region provider={}, pather provider={}).",
                services.toolRegistry().getClass().getSimpleName(),
                services.toolService().getClass().getSimpleName(),
                services.regionProvider().getClass().getSimpleName(),
                services.toolPatherProvider().getClass().getSimpleName()
        );
    }
}

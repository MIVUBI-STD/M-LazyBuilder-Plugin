package com.halokaryamedia.lazybuilder.builder.axiom;

import com.moulberry.axiomclientapi.service.RegionProvider;
import com.moulberry.axiomclientapi.service.ToolPatherProvider;
import com.moulberry.axiomclientapi.service.ToolRegistryService;
import com.moulberry.axiomclientapi.service.ToolService;

import java.util.ServiceLoader;

/**
 * Typed boundary to Axiom's public client API.
 *
 * <p>Builder Utilities deliberately resolves only public axiomclientapi services here.
 * A missing service is a hard compatibility failure for the pinned Axiom line rather
 * than a reason to fall back to Axiom implementation internals.</p>
 */
public record AxiomClientServices(
        ToolRegistryService toolRegistry,
        ToolService toolService,
        RegionProvider regionProvider,
        ToolPatherProvider toolPatherProvider
) {
    public static AxiomClientServices load() {
        return new AxiomClientServices(
                require(ToolRegistryService.class),
                require(ToolService.class),
                require(RegionProvider.class),
                require(ToolPatherProvider.class)
        );
    }

    public AxiomSplinePreviewRegion createSplinePreviewRegion() {
        return new AxiomSplinePreviewRegion(regionProvider.createBoolean());
    }

    private static <T> T require(Class<T> serviceType) {
        return ServiceLoader.load(serviceType, serviceType.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Axiom client API service is unavailable: " + serviceType.getName()
                ));
    }
}

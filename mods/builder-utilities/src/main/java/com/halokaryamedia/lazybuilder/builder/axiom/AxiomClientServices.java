package com.halokaryamedia.lazybuilder.builder.axiom;

import com.moulberry.axiomclientapi.service.RegionProvider;
import com.moulberry.axiomclientapi.service.ToolRegistryService;
import com.moulberry.axiomclientapi.service.ToolService;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Typed boundary to Axiom's public client API.
 *
 * <p>Builder Utilities deliberately resolves only public axiomclientapi services here.
 * Only services used by the production path are boot requirements; optional Axiom
 * services must not make the entire Builder extension fail to initialize.</p>
 */
public record AxiomClientServices(
        ToolRegistryService toolRegistry,
        ToolService toolService,
        RegionProvider regionProvider
) {
    public static AxiomClientServices load() {
        return new AxiomClientServices(
                require(ToolRegistryService.class),
                require(ToolService.class),
                require(RegionProvider.class)
        );
    }

    public AxiomSplinePreviewRegion createSplinePreviewRegion() {
        return new AxiomSplinePreviewRegion(regionProvider.createBoolean());
    }

    private static <T> T require(Class<T> serviceType) {
        try {
            return ServiceLoader.load(serviceType, serviceType.getClassLoader())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Axiom client API service is unavailable: "
                                    + serviceType.getName()
                                    + " | " + AxiomCompatibility.current().summary()
                    ));
        } catch (ServiceConfigurationError | LinkageError failure) {
            throw new IllegalStateException(
                    "Axiom public API linkage failed for "
                            + serviceType.getName()
                            + " | " + AxiomCompatibility.current().summary(),
                    failure);
        }
    }
}

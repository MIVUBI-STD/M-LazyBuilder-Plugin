package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.ScopedOperationIds;
import net.minecraft.client.world.ClientWorld;

import java.util.Objects;
import java.util.UUID;

/** Creates durable Builder operation ids bound to the active world/dimension scope. */
public final class AxiomDurableOperationIds {
    private AxiomDurableOperationIds() {}

    public static String scope(ClientWorld world, String operationId) {
        Objects.requireNonNull(world, "world");
        return ScopedOperationIds.scope(
                AxiomWorldScope.currentScopeId(world),
                operationId
        );
    }

    public static String random(ClientWorld world) {
        return scope(world, UUID.randomUUID().toString());
    }
}

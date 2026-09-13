package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;

import java.io.IOException;
import java.util.Objects;

/** Canonical Create World use case. Runtime load truth comes directly from Paper. */
public final class WorldCreationService {
    private final WorldRegistry registry;
    private final WorldRegistryPersistence persistence;
    private final WorldRuntimeGateway runtime;
    private final BuildReadyPolicy buildReadyPolicy;

    public WorldCreationService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeGateway runtime,
            BuildReadyPolicy buildReadyPolicy
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.buildReadyPolicy = Objects.requireNonNull(buildReadyPolicy, "buildReadyPolicy");
    }

    /** Migration bridge only; legacy runtime-state registry is intentionally ignored. */
    public WorldCreationService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeGateway runtime,
            WorldRuntimeStateRegistry ignoredLegacyStates,
            BuildReadyPolicy buildReadyPolicy
    ) {
        this(registry, persistence, runtime, buildReadyPolicy);
    }

    public synchronized WorldRecord create(String folderName, String displayName, WorldKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (kind == WorldKind.IMPORTED) {
            throw new IllegalArgumentException("Create World only supports FLAT or VOID worlds");
        }

        WorldRecord record = new WorldRecord(
                WorldId.create(),
                folderName,
                displayName,
                kind,
                WorldLifecycle.ACTIVE,
                true,
                buildReadyPolicy.defaultGameMode().name()
        );

        try (WorldRegistry.FolderReservation ignored = registry.reserveFolder(record.folderName())) {
            runtime.createNewWorld(record, buildReadyPolicy);
            boolean registered = false;
            try {
                registry.register(record);
                registered = true;
                persistence.save(registry.all());
                return record;
            } catch (IOException | RuntimeException exception) {
                if (registered) registry.remove(record.id());
                try {
                    runtime.rollbackCreatedWorld(record);
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw new IllegalStateException("Failed to publish newly created world: " + folderName, exception);
            }
        }
    }
}

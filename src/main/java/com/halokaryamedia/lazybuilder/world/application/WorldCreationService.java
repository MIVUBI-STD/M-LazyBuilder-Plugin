package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;

import java.io.IOException;
import java.util.Objects;

/**
 * Canonical Create World use case.
 *
 * <p>Both Flat and Void worlds use this path so registry publication,
 * BUILD_READY application, runtime state, rollback, and persistence cannot drift apart.</p>
 */
public final class WorldCreationService {
    private final WorldRegistry registry;
    private final WorldRegistryPersistence persistence;
    private final WorldRuntimeGateway runtime;
    private final WorldRuntimeStateRegistry runtimeStates;
    private final BuildReadyPolicy buildReadyPolicy;

    public WorldCreationService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeGateway runtime,
            WorldRuntimeStateRegistry runtimeStates,
            BuildReadyPolicy buildReadyPolicy
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.runtimeStates = Objects.requireNonNull(runtimeStates, "runtimeStates");
        this.buildReadyPolicy = Objects.requireNonNull(buildReadyPolicy, "buildReadyPolicy");
    }

    public synchronized WorldRecord create(String folderName, String displayName, WorldKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (kind == WorldKind.IMPORTED) {
            throw new IllegalArgumentException("Create World only supports FLAT or VOID worlds");
        }
        if (registry.findByFolderName(folderName).isPresent()) {
            throw new IllegalArgumentException("World folder is already managed: " + folderName);
        }

        WorldRecord record = new WorldRecord(
                WorldId.create(),
                folderName,
                displayName,
                kind,
                WorldLifecycle.ACTIVE,
                true
        );

        runtime.createNewWorld(record, buildReadyPolicy);
        boolean registered = false;
        boolean stateInitialized = false;
        try {
            registry.register(record);
            registered = true;
            runtimeStates.initialize(record.id(), WorldRuntimeState.LOADED);
            stateInitialized = true;
            persistence.save(registry.all());
            return record;
        } catch (IOException | RuntimeException exception) {
            if (stateInitialized) {
                runtimeStates.remove(record.id());
            }
            if (registered) {
                registry.remove(record.id());
            }
            try {
                runtime.rollbackCreatedWorld(record);
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw new IllegalStateException("Failed to publish newly created world: " + folderName, exception);
        }
    }
}

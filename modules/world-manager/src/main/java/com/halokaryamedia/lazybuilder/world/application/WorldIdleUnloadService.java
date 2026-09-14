package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Automatically unloads active managed worlds after they stay empty and idle. */
public final class WorldIdleUnloadService {
    private final WorldRegistry registry;
    private final WorldRuntimeService runtimeService;
    private final WorldOperationCoordinator operations;
    private final Predicate<WorldRecord> isLoaded;
    private final Predicate<WorldRecord> hasPlayers;
    private final Predicate<WorldRecord> protectedWorld;
    private final long idleMillis;
    private final Map<WorldId, Long> emptySince = new HashMap<>();

    public WorldIdleUnloadService(
            WorldRegistry registry,
            WorldRuntimeService runtimeService,
            WorldOperationCoordinator operations,
            Predicate<WorldRecord> isLoaded,
            Predicate<WorldRecord> hasPlayers,
            Duration idleTimeout
    ) {
        this(registry, runtimeService, operations, isLoaded, hasPlayers, ignored -> false, idleTimeout);
    }

    public WorldIdleUnloadService(
            WorldRegistry registry,
            WorldRuntimeService runtimeService,
            WorldOperationCoordinator operations,
            Predicate<WorldRecord> isLoaded,
            Predicate<WorldRecord> hasPlayers,
            Predicate<WorldRecord> protectedWorld,
            Duration idleTimeout
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.isLoaded = Objects.requireNonNull(isLoaded, "isLoaded");
        this.hasPlayers = Objects.requireNonNull(hasPlayers, "hasPlayers");
        this.protectedWorld = Objects.requireNonNull(protectedWorld, "protectedWorld");
        this.idleMillis = Math.max(30_000L, Objects.requireNonNull(idleTimeout, "idleTimeout").toMillis());
    }

    /** Runs on the Paper main thread. Loading is automatic on teleport; unloading is automatic here. */
    public void tick(long nowMillis) {
        for (WorldRecord world : registry.all()) {
            WorldId id = world.id();
            if (world.lifecycle() != WorldLifecycle.ACTIVE
                    || protectedWorld.test(world)
                    || !isLoaded.test(world)
                    || hasPlayers.test(world)
                    || operations.activeOperation(id) != null) {
                emptySince.remove(id);
                continue;
            }

            long since = emptySince.computeIfAbsent(id, ignored -> nowMillis);
            if (nowMillis - since < idleMillis) continue;

            try {
                runtimeService.unload(id);
                emptySince.remove(id);
            } catch (RuntimeException ignored) {
                // Runtime may reject an unload for a transient safety reason. Retry only after another idle window.
                emptySince.put(id, nowMillis);
            }
        }
    }
}

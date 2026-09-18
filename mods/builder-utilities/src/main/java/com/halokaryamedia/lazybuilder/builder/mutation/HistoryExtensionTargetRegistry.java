package com.halokaryamedia.lazybuilder.builder.mutation;

import java.util.Map;
import java.util.Objects;

/** Immutable adapter registry keyed by namespaced History extension type id. */
public final class HistoryExtensionTargetRegistry {
    private final Map<String, HistoryExtensionMutationTarget> targets;

    public HistoryExtensionTargetRegistry(Map<String, HistoryExtensionMutationTarget> targets) {
        Objects.requireNonNull(targets, "targets");
        this.targets = Map.copyOf(targets);
        this.targets.forEach((type, target) -> {
            if (type == null || type.isBlank()) throw new IllegalArgumentException("extension type id must be non-blank");
            Objects.requireNonNull(target, "extension target");
        });
    }

    public static HistoryExtensionTargetRegistry empty() {
        return new HistoryExtensionTargetRegistry(Map.of());
    }

    public boolean supports(String typeId) {
        return targets.containsKey(typeId);
    }

    public HistoryExtensionMutationTarget require(String typeId) {
        HistoryExtensionMutationTarget target = targets.get(typeId);
        if (target == null) {
            throw new IllegalArgumentException("No mutation target registered for History extension type " + typeId);
        }
        return target;
    }
}

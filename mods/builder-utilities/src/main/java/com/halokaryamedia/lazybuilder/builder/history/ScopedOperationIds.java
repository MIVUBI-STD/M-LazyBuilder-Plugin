package com.halokaryamedia.lazybuilder.builder.history;

import java.util.Objects;
import java.util.Optional;

/** Stable operation-id envelope used to bind durable History to one world scope. */
public final class ScopedOperationIds {
    private static final String PREFIX = "lbw1:";

    private ScopedOperationIds() {}

    public static String scope(String scopeId, String operationId) {
        requireScope(scopeId);
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must be non-blank");
        }
        Optional<String> existing = scopeOf(operationId);
        if (existing.isPresent()) {
            if (!existing.get().equals(scopeId)) {
                throw new IllegalArgumentException(
                        "operation id belongs to a different world scope");
            }
            return operationId;
        }
        return PREFIX + scopeId + ":" + operationId;
    }

    public static Optional<String> scopeOf(String operationId) {
        if (operationId == null || !operationId.startsWith(PREFIX)) return Optional.empty();
        int separator = operationId.indexOf(':', PREFIX.length());
        if (separator <= PREFIX.length()) return Optional.empty();
        return Optional.of(operationId.substring(PREFIX.length(), separator));
    }

    public static boolean belongsTo(String operationId, String scopeId) {
        requireScope(scopeId);
        return scopeOf(operationId).map(scopeId::equals).orElse(false);
    }

    private static void requireScope(String scopeId) {
        Objects.requireNonNull(scopeId, "scopeId");
        if (scopeId.isBlank() || scopeId.indexOf(':') >= 0) {
            throw new IllegalArgumentException("scopeId must be non-blank and contain no ':'");
        }
    }
}

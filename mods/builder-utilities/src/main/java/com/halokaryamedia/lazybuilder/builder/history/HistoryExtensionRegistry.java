package com.halokaryamedia.lazybuilder.builder.history;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable registry for adapter-owned extension codecs. Unknown extension IDs remain
 * valid History v2 frames but cannot be decoded through this registry.
 */
public final class HistoryExtensionRegistry {
    private final Map<String, HistoryExtensionType<?>> types;

    private HistoryExtensionRegistry(Map<String, HistoryExtensionType<?>> types) {
        this.types = Map.copyOf(types);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<HistoryExtensionType<?>> find(String id) {
        return Optional.ofNullable(types.get(id));
    }

    @SuppressWarnings("unchecked")
    public <T> HistoryExtensionType<T> require(String id, Class<T> valueType) {
        Objects.requireNonNull(valueType, "valueType");
        HistoryExtensionType<?> type = types.get(id);
        if (type == null) throw new IllegalArgumentException("Unknown History extension type: " + id);
        // Java erasure prevents proving codec T here; the explicit valueType is a caller
        // assertion and makes the unchecked boundary visible in adapter code.
        return (HistoryExtensionType<T>) type;
    }

    public static final class Builder {
        private final LinkedHashMap<String, HistoryExtensionType<?>> types = new LinkedHashMap<>();

        public Builder register(HistoryExtensionType<?> type) {
            Objects.requireNonNull(type, "type");
            if (types.putIfAbsent(type.id(), type) != null) {
                throw new IllegalArgumentException("Duplicate History extension type: " + type.id());
            }
            return this;
        }

        public HistoryExtensionRegistry build() {
            return new HistoryExtensionRegistry(types);
        }
    }
}

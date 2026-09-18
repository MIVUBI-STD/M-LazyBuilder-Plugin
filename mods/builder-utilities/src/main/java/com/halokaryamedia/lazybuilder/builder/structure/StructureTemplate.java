package com.halokaryamedia.lazybuilder.builder.structure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Neutral immutable block template. Duplicate local coordinates are rejected so
 * compiled mutations have one deterministic source state per local position.
 */
public record StructureTemplate(String id, List<StructureBlock> blocks) {
    public StructureTemplate {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must be non-blank");
        Objects.requireNonNull(blocks, "blocks");
        LinkedHashMap<LocalKey, StructureBlock> unique = new LinkedHashMap<>();
        for (StructureBlock block : blocks) {
            Objects.requireNonNull(block, "block");
            LocalKey key = new LocalKey(block.x(), block.y(), block.z());
            if (unique.putIfAbsent(key, block) != null) {
                throw new IllegalArgumentException("duplicate structure block coordinate: " + key);
            }
        }
        blocks = List.copyOf(unique.values());
    }

    public static StructureTemplate of(String id, StructureBlock... blocks) {
        return new StructureTemplate(id, List.of(blocks));
    }

    public Map<String, Long> blockStateHistogram() {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        for (StructureBlock block : blocks) {
            counts.merge(block.blockState(), 1L, Long::sum);
        }
        return Map.copyOf(counts);
    }

    private record LocalKey(int x, int y, int z) {}
}

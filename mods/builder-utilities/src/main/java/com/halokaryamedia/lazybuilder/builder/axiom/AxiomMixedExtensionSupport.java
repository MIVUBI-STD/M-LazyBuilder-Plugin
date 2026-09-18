package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.Objects;

/** Supported authoritative non-block extension mix for Axiom + server bridge. */
final class AxiomMixedExtensionSupport {
    private AxiomMixedExtensionSupport() {}

    static Plan inspect(StoredChangeSet set) throws IOException {
        Objects.requireNonNull(set, "set");
        long[] counts = new long[3];
        set.visitExtensions(frame -> {
            if (HistoryExtensionTypes.BLOCK_ENTITY.equals(frame.typeId())) {
                counts[0]++;
            } else if (HistoryExtensionTypes.BIOME.equals(frame.typeId())) {
                counts[1]++;
            } else if (HistoryExtensionTypes.ENTITY.equals(frame.typeId())) {
                counts[2]++;
            } else {
                throw new IOException(
                        "No authoritative Axiom mixed mutation path for extension type "
                                + frame.typeId());
            }
            return true;
        });
        return new Plan(counts[0], counts[1], counts[2]);
    }

    record Plan(long blockEntities, long biomes, long entities) {
        Plan {
            if (blockEntities < 0 || biomes < 0 || entities < 0) {
                throw new IllegalArgumentException("extension counts must be >= 0");
            }
        }

        long total() {
            return Math.addExact(blockEntities, Math.addExact(biomes, entities));
        }

        boolean hasBlockEntities() { return blockEntities > 0; }
        boolean hasBiomes() { return biomes > 0; }
        boolean hasEntities() { return entities > 0; }
    }
}

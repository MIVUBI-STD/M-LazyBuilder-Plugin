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
        long[] counts = new long[2];
        set.visitExtensions(frame -> {
            if (HistoryExtensionTypes.BIOME.equals(frame.typeId())) {
                counts[0]++;
            } else if (HistoryExtensionTypes.ENTITY.equals(frame.typeId())) {
                counts[1]++;
            } else {
                throw new IOException(
                        "No authoritative Axiom mixed mutation path for extension type "
                                + frame.typeId());
            }
            return true;
        });
        return new Plan(counts[0], counts[1]);
    }

    record Plan(long biomes, long entities) {
        Plan {
            if (biomes < 0 || entities < 0) {
                throw new IllegalArgumentException("extension counts must be >= 0");
            }
        }

        long total() {
            return Math.addExact(biomes, entities);
        }

        boolean hasBiomes() { return biomes > 0; }
        boolean hasEntities() { return entities > 0; }
    }
}

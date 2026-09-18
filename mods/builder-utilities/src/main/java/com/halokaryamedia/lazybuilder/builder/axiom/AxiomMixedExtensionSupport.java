package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.structure.EntityExtensionPayload;
import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Supported authoritative non-block extension mix for Axiom + server bridge. */
final class AxiomMixedExtensionSupport {
    private AxiomMixedExtensionSupport() {}

    static Plan inspect(StoredChangeSet set) throws IOException {
        Objects.requireNonNull(set, "set");
        long[] counts = new long[3];
        set.visitExtensions(frame -> {
            if (HistoryExtensionTypes.BLOCK_ENTITY.equals(frame.typeId())) {
                requireBlockEntityPayload(frame.beforePayload(), "before");
                requireBlockEntityPayload(frame.afterPayload(), "after");
                counts[0]++;
            } else if (HistoryExtensionTypes.BIOME.equals(frame.typeId())) {
                String before = new String(frame.beforePayload(), StandardCharsets.UTF_8);
                String after = new String(frame.afterPayload(), StandardCharsets.UTF_8);
                try {
                    new BuilderExtensionWireProtocol.BiomeMutation(
                            0, 0, 0, before, after);
                } catch (IllegalArgumentException failure) {
                    throw new IOException(
                            "BIOME extension is not transportable: "
                                    + failure.getMessage(),
                            failure
                    );
                }
                counts[1]++;
            } else if (HistoryExtensionTypes.ENTITY.equals(frame.typeId())) {
                requireEntityPayloads(
                        frame.beforePayload(),
                        frame.afterPayload());
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

    private static void requireBlockEntityPayload(
            byte[] payload,
            String side
    ) throws IOException {
        if (payload.length > BuilderExtensionWireProtocol.MAX_BLOCK_ENTITY_NBT_BYTES) {
            throw new IOException(
                    "BLOCK_ENTITY " + side + " payload exceeds server bridge limit "
                            + BuilderExtensionWireProtocol.MAX_BLOCK_ENTITY_NBT_BYTES
                            + " bytes");
        }
    }

    private static void requireEntityPayloads(
            byte[] beforeBytes,
            byte[] afterBytes
    ) throws IOException {
        EntityExtensionPayload before =
                EntityExtensionPayload.decode(beforeBytes);
        EntityExtensionPayload after =
                EntityExtensionPayload.decode(afterBytes);
        if (!before.sameSlot(after)
                || before.present() == after.present()) {
            throw new IOException(
                    "ENTITY extension must be an absence/presence toggle at one slot");
        }
        EntityExtensionPayload present = before.present() ? before : after;
        AxiomEntityTemplate template =
                AxiomEntityTemplate.decode(present.templateNbt());
        int bytes = template.snbt().getBytes(StandardCharsets.UTF_8).length;
        if (bytes > BuilderExtensionWireProtocol.MAX_ENTITY_TEMPLATE_BYTES) {
            throw new IOException(
                    "ENTITY template exceeds server bridge limit "
                            + BuilderExtensionWireProtocol.MAX_ENTITY_TEMPLATE_BYTES
                            + " UTF-8 bytes");
        }
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

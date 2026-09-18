package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionClientNetworking;
import com.halokaryamedia.lazybuilder.builder.structure.StructureSnapshot;

import java.util.Objects;

/** One authoritative capability matrix for structure/schematic payload types. */
public final class AxiomStructureCapabilityMatrix {
    private AxiomStructureCapabilityMatrix() {}

    public static Report current(StructureSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var server = BuilderExtensionClientNetworking.capabilities();
        return new Report(
                PayloadSupport.NATIVE_AXIOM,
                snapshot.biomeCount() == 0
                        ? PayloadSupport.NOT_PRESENT
                        : server.supportsBiome()
                                ? PayloadSupport.AUTHORITATIVE_SERVER
                                : PayloadSupport.UNAVAILABLE,
                snapshot.entityCount() == 0
                        ? PayloadSupport.NOT_PRESENT
                        : server.supportsEntity()
                                ? PayloadSupport.AUTHORITATIVE_SERVER
                                : PayloadSupport.UNAVAILABLE,
                snapshot.blockEntityCount() == 0
                        ? PayloadSupport.NOT_PRESENT
                        : server.supportsBlockEntity()
                                ? PayloadSupport.AUTHORITATIVE_SERVER
                                : PayloadSupport.PRESERVE_ONLY
        );
    }

    public static GlobalReport global() {
        var server = BuilderExtensionClientNetworking.capabilities();
        return new GlobalReport(
                PayloadSupport.NATIVE_AXIOM,
                server.supportsBiome()
                        ? PayloadSupport.AUTHORITATIVE_SERVER
                        : PayloadSupport.UNAVAILABLE,
                server.supportsEntity()
                        ? PayloadSupport.AUTHORITATIVE_SERVER
                        : PayloadSupport.UNAVAILABLE,
                server.supportsBlockEntity()
                        ? PayloadSupport.AUTHORITATIVE_SERVER
                        : PayloadSupport.PRESERVE_ONLY
        );
    }

    public enum PayloadSupport {
        NOT_PRESENT,
        NATIVE_AXIOM,
        AUTHORITATIVE_SERVER,
        PRESERVE_ONLY,
        UNAVAILABLE;

        public boolean canApply() {
            return this == NOT_PRESENT
                    || this == NATIVE_AXIOM
                    || this == AUTHORITATIVE_SERVER;
        }
    }

    public record Report(
            PayloadSupport blocks,
            PayloadSupport biomes,
            PayloadSupport entities,
            PayloadSupport blockEntities
    ) {
        public Report {
            Objects.requireNonNull(blocks, "blocks");
            Objects.requireNonNull(biomes, "biomes");
            Objects.requireNonNull(entities, "entities");
            Objects.requireNonNull(blockEntities, "blockEntities");
        }

        public boolean canApplyLosslessly() {
            return blocks.canApply()
                    && biomes.canApply()
                    && entities.canApply()
                    && blockEntities.canApply();
        }

        public String blockerSummary() {
            if (canApplyLosslessly()) return "none";
            StringBuilder result = new StringBuilder();
            appendBlocker(result, "BIOME", biomes);
            appendBlocker(result, "ENTITY", entities);
            appendBlocker(result, "BLOCK_ENTITY", blockEntities);
            return result.toString();
        }

        private static void appendBlocker(
                StringBuilder result,
                String type,
                PayloadSupport support
        ) {
            if (support.canApply()) return;
            if (!result.isEmpty()) result.append(" | ");
            result.append(type).append('=').append(support);
        }
    }

    public record GlobalReport(
            PayloadSupport blocks,
            PayloadSupport biomes,
            PayloadSupport entities,
            PayloadSupport blockEntities
    ) {}
}

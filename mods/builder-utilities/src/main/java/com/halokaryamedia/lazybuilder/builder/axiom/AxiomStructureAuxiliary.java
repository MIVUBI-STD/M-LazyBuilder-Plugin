package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionClientNetworking;
import com.halokaryamedia.lazybuilder.builder.structure.BiomePayloadTransform;
import com.halokaryamedia.lazybuilder.builder.structure.EntityPayloadTransform;
import com.halokaryamedia.lazybuilder.builder.structure.BlockEntityPayloadTransform;
import com.halokaryamedia.lazybuilder.builder.structure.StructureAuxiliaryContext;
import com.halokaryamedia.lazybuilder.builder.structure.StructureAuxiliaryPayloadPlanner;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePastePlan;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePastePlanner;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import com.halokaryamedia.lazybuilder.builder.structure.StructureSnapshot;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Shared capability policy and BIOME adapters for structure tools. */
public final class AxiomStructureAuxiliary {
    private AxiomStructureAuxiliary() {}

    public static void requireApplySupported(StructureSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.blockEntityCount() != 0) {
            throw new IllegalStateException(
                    "Block-entity mutation authority is not available yet; "
                            + "the payload remains preserved for .schem export");
        }
        if (snapshot.entityCount() != 0
                && !BuilderExtensionClientNetworking.capabilities().supportsEntity()) {
            throw new IllegalStateException(
                    "This structure contains entities but the connected server does not "
                            + "advertise LazyBuilder ENTITY authority");
        }
        if (snapshot.biomeCount() != 0
                && !BuilderExtensionClientNetworking.capabilities().supportsBiome()) {
            throw new IllegalStateException(
                    "This structure contains biomes but the connected server does not "
                            + "advertise LazyBuilder BIOME authority");
        }
    }

    public static StructureAuxiliaryContext contextFor(
            StructureSnapshot snapshot,
            ClientWorld world
    ) {
        requireApplySupported(snapshot);
        Objects.requireNonNull(world, "world");
        return new StructureAuxiliaryContext(
                null,
                BlockEntityPayloadTransform.identity(),
                snapshot.biomeCount() == 0
                        ? null
                        : (x, y, z) -> new AxiomWorldBiomeSource(world)
                                .biomeAt(x, y, z)
                                .getBytes(StandardCharsets.UTF_8),
                BiomePayloadTransform.identity(),
                snapshot.entityCount() == 0
                        ? null
                        : new AxiomEntityPlacementStateSource(world),
                snapshot.entityCount() == 0
                        ? EntityPayloadTransform.identity()
                        : new AxiomEntityPayloadTransform()
        );
    }

    public static StructurePastePlan planSingle(
            StructureSnapshot snapshot,
            StructurePlacement placement,
            ClientWorld world
    ) throws IOException {
        requireApplySupported(snapshot);
        var blocks = StructurePastePlanner.plan(
                snapshot,
                placement,
                new MinecraftStructureBlockStateTransform(world),
                new AxiomClientWorldStateSource(world)
        );
        var extensions = StructureAuxiliaryPayloadPlanner.plan(
                snapshot,
                placement,
                snapshot.biomeCount() == 0
                        ? null
                        : (x, y, z) -> new AxiomWorldBiomeSource(world)
                                .biomeAt(x, y, z)
                                .getBytes(StandardCharsets.UTF_8),
                BiomePayloadTransform.identity(),
                snapshot.entityCount() == 0
                        ? null
                        : new AxiomEntityPlacementStateSource(world),
                snapshot.entityCount() == 0
                        ? EntityPayloadTransform.identity()
                        : new AxiomEntityPayloadTransform()
        );
        return new StructurePastePlan(blocks, extensions);
    }

    public static long estimateHistoryBytes(StructurePastePlan plan) {
        Objects.requireNonNull(plan, "plan");
        try {
            long blocks = Math.multiplyExact(plan.blockChanges(), 96L);
            long extensions = Math.multiplyExact((long) plan.extensionChanges(), 192L);
            return Math.max(1L, Math.addExact(blocks, extensions));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("structure History estimate overflow", e);
        }
    }
}

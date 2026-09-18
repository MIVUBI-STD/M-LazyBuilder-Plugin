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
import com.halokaryamedia.lazybuilder.builder.structure.SpongeSchematicImport;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.List;

/** Shared capability policy and BIOME adapters for structure tools. */
public final class AxiomStructureAuxiliary {
    private AxiomStructureAuxiliary() {}

    public static void requireApplySupported(StructureSnapshot snapshot) {
        AxiomStructureCapabilityMatrix.Report report =
                AxiomStructureCapabilityMatrix.current(snapshot);
        if (!report.canApplyLosslessly()) {
            throw new IllegalStateException(
                    "Structure cannot be applied losslessly with current authorities: "
                            + report.blockerSummary()
                            + ". Unsupported payloads remain preserved for .schem export.");
        }
    }

    /**
     * Returns an apply-only snapshot. When stripping is explicitly requested,
     * block-entity payloads are omitted while blocks/biomes/entities stay intact.
     * The source snapshot is never modified.
     */
    public static StructureSnapshot snapshotForApply(
            StructureSnapshot source,
            boolean stripBlockEntities
    ) {
        Objects.requireNonNull(source, "source");
        StructureSnapshot result = source;
        if (stripBlockEntities && source.blockEntityCount() != 0) {
            result = new StructureSnapshot(
                    source.blocks(),
                    List.of(),
                    source.biomes(),
                    source.entities()
            );
        }
        requireApplySupported(result);
        return result;
    }

    public static SpongeSchematicImport importForApply(
            SpongeSchematicImport source,
            boolean stripBlockEntities
    ) {
        Objects.requireNonNull(source, "source");
        StructureSnapshot snapshot = snapshotForApply(
                source.snapshot(), stripBlockEntities);
        if (snapshot == source.snapshot()) return source;
        return new SpongeSchematicImport(
                snapshot,
                source.offsetX(),
                source.offsetY(),
                source.offsetZ(),
                source.dataVersion(),
                source.metadataPayload()
        );
    }

    public static StructureAuxiliaryContext contextForAuthorities(ClientWorld world) {
        Objects.requireNonNull(world, "world");
        var capabilities = BuilderExtensionClientNetworking.capabilities();
        return new StructureAuxiliaryContext(
                null,
                BlockEntityPayloadTransform.identity(),
                capabilities.supportsBiome()
                        ? (x, y, z) -> new AxiomWorldBiomeSource(world)
                                .biomeAt(x, y, z)
                                .getBytes(StandardCharsets.UTF_8)
                        : null,
                BiomePayloadTransform.identity(),
                capabilities.supportsEntity()
                        ? new AxiomEntityPlacementStateSource(world)
                        : null,
                capabilities.supportsEntity()
                        ? new AxiomEntityPayloadTransform()
                        : EntityPayloadTransform.identity()
        );
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

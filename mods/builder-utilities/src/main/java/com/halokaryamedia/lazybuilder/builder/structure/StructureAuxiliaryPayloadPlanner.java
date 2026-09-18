package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Builds durable biome/entity extension frames for a single structure placement. */
public final class StructureAuxiliaryPayloadPlanner {
    private StructureAuxiliaryPayloadPlanner() {}

    public static List<HistoryExtensionFrame> plan(
            StructureSnapshot snapshot,
            StructurePlacement placement,
            StructureBiomeStateSource biomeSource,
            BiomePayloadTransform biomeTransform,
            StructureEntityStateSource entitySource,
            EntityPayloadTransform entityTransform
    ) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(biomeTransform, "biomeTransform");
        Objects.requireNonNull(entityTransform, "entityTransform");

        List<HistoryExtensionFrame> frames = new ArrayList<>();

        if (!snapshot.biomes().isEmpty()) {
            if (biomeSource == null) {
                throw new IllegalArgumentException(
                        "biome source is required when snapshot contains biome samples");
            }
            for (StructureBiomeSample biome : snapshot.biomes()) {
                StructurePlacement.WorldPosition world =
                        placement.transform(biome.x(), biome.y(), biome.z());
                byte[] before = Objects.requireNonNull(
                        biomeSource.read(world.x(), world.y(), world.z()),
                        "biome before payload").clone();
                byte[] after = Objects.requireNonNull(
                        biomeTransform.transform(biome.payload(), placement),
                        "biome transformed payload").clone();
                if (Arrays.equals(before, after)) continue;
                frames.add(new HistoryExtensionFrame(
                        HistoryExtensionTypes.BIOME,
                        Math.floorDiv(world.x(), 16),
                        Math.floorDiv(world.z(), 16),
                        LocalBlockPosition.pack(
                                Math.floorMod(world.x(), 16),
                                world.y(),
                                Math.floorMod(world.z(), 16)),
                        before,
                        after
                ));
            }
        }

        if (!snapshot.entities().isEmpty()) {
            if (entitySource == null) {
                throw new IllegalArgumentException(
                        "entity source is required when snapshot contains entities");
            }
            long ordinal = 0;
            for (StructureEntity entity : snapshot.entities()) {
                StructurePlacement.WorldPositionD world =
                        placement.transform(entity.x(), entity.y(), entity.z());
                long key = ordinal++;
                byte[] before = Objects.requireNonNull(
                        entitySource.read(key, world),
                        "entity before payload").clone();
                byte[] transformed = Objects.requireNonNull(
                        entityTransform.transform(entity.payload(), placement),
                        "entity transformed payload").clone();
                byte[] after = EntityExtensionPayload.present(
                        world.x(), world.y(), world.z(), transformed).encode();
                if (Arrays.equals(before, after)) continue;
                int blockX = floorToInt(world.x());
                int blockZ = floorToInt(world.z());
                frames.add(new HistoryExtensionFrame(
                        HistoryExtensionTypes.ENTITY,
                        Math.floorDiv(blockX, 16),
                        Math.floorDiv(blockZ, 16),
                        key,
                        before,
                        after
                ));
            }
        }

        return List.copyOf(frames);
    }

    private static int floorToInt(double value) {
        double floored = Math.floor(value);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("entity position exceeds integer world range");
        }
        return (int) floored;
    }
}

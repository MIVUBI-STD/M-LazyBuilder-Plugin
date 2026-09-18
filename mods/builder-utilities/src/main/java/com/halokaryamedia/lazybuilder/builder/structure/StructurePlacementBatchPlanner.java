package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSetBuilder;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.material.BlockStateSource;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Expands many placement entries into one collision-checked structure paste plan.
 * All block changes are merged into one frame per chunk to satisfy History v2 invariants.
 */
public final class StructurePlacementBatchPlanner {
    private StructurePlacementBatchPlanner() {}

    public static StructurePastePlan planBlocks(
            List<PlacementPlanEntry> placements,
            StructureSourceResolver resolver,
            BlockStateTransform stateTransform,
            BlockStateSource existing
    ) {
        try {
            return planAll(
                    placements,
                    resolver,
                    stateTransform,
                    existing,
                    StructureAuxiliaryContext.none()
            );
        } catch (IOException impossible) {
            throw new IllegalStateException("block-only structure planning unexpectedly failed", impossible);
        }
    }

    public static StructurePastePlan plan(
            List<PlacementPlanEntry> placements,
            StructureSourceResolver resolver,
            BlockStateTransform stateTransform,
            BlockStateSource existing,
            StructureBlockEntityStateSource blockEntitySource,
            BlockEntityPayloadTransform payloadTransform
    ) throws IOException {
        return planAll(
                placements,
                resolver,
                stateTransform,
                existing,
                new StructureAuxiliaryContext(
                        blockEntitySource,
                        payloadTransform,
                        null,
                        BiomePayloadTransform.identity(),
                        null,
                        EntityPayloadTransform.identity()
                )
        );
    }

    public static StructurePastePlan planAll(
            List<PlacementPlanEntry> placements,
            StructureSourceResolver resolver,
            BlockStateTransform stateTransform,
            BlockStateSource existing,
            StructureAuxiliaryContext auxiliary
    ) throws IOException {
        Objects.requireNonNull(placements, "placements");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(stateTransform, "stateTransform");
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(auxiliary, "auxiliary");

        LinkedHashMap<WorldKey, DesiredBlock> desired = new LinkedHashMap<>();
        List<HistoryExtensionFrame> extensions = new ArrayList<>();
        Set<ExtensionWorldKey> blockEntityKeys = new HashSet<>();
        Set<ExtensionWorldKey> biomeKeys = new HashSet<>();
        long entityKey = 0L;

        for (PlacementPlanEntry entry : placements) {
            Objects.requireNonNull(entry, "entry");
            StructureSnapshot snapshot = Objects.requireNonNull(
                    resolver.resolve(entry.sourceId()),
                    "resolved structure"
            );
            StructurePlacement placement = StructurePlacementAdapter.from(entry);

            for (StructureBlock block : snapshot.blocks()) {
                StructurePlacement.WorldPosition world =
                        placement.transform(block.x(), block.y(), block.z());
                WorldKey key = new WorldKey(world.x(), world.y(), world.z());
                if (desired.containsKey(key)) {
                    throw collision(world.x(), world.y(), world.z());
                }
                String after = requireState(
                        stateTransform.transform(block.blockState(), placement),
                        "transformed structure state"
                );
                desired.put(key, new DesiredBlock(world.x(), world.y(), world.z(), after));
            }

            if (!snapshot.blockEntities().isEmpty()) {
                if (auxiliary.blockEntities() == null) {
                    throw new IllegalArgumentException(
                            "block entity source is required for structure snapshots containing block entities");
                }
                for (StructureBlockEntity blockEntity : snapshot.blockEntities()) {
                    StructurePlacement.WorldPosition world =
                            placement.transform(blockEntity.x(), blockEntity.y(), blockEntity.z());
                    ExtensionWorldKey key = new ExtensionWorldKey(world.x(), world.y(), world.z());
                    if (!blockEntityKeys.add(key)) {
                        throw new IllegalArgumentException(
                                "structure block entity collision at "
                                        + world.x() + "," + world.y() + "," + world.z());
                    }
                    byte[] before = Objects.requireNonNull(
                            auxiliary.blockEntities().read(world.x(), world.y(), world.z()),
                            "block entity before payload").clone();
                    byte[] after = Objects.requireNonNull(
                            auxiliary.blockEntityTransform().transform(
                                    blockEntity.payload(), placement),
                            "transformed block entity payload").clone();
                    if (!Arrays.equals(before, after)) {
                        extensions.add(frameAtBlock(
                                HistoryExtensionTypes.BLOCK_ENTITY,
                                world.x(), world.y(), world.z(),
                                before, after));
                    }
                }
            }

            if (!snapshot.biomes().isEmpty()) {
                if (auxiliary.biomes() == null) {
                    throw new IllegalArgumentException(
                            "biome source is required for structure snapshots containing biomes");
                }
                for (StructureBiomeSample biome : snapshot.biomes()) {
                    StructurePlacement.WorldPosition world =
                            placement.transform(biome.x(), biome.y(), biome.z());
                    ExtensionWorldKey key = new ExtensionWorldKey(world.x(), world.y(), world.z());
                    if (!biomeKeys.add(key)) {
                        throw new IllegalArgumentException(
                                "structure biome collision at "
                                        + world.x() + "," + world.y() + "," + world.z());
                    }
                    byte[] before = Objects.requireNonNull(
                            auxiliary.biomes().read(world.x(), world.y(), world.z()),
                            "biome before payload").clone();
                    byte[] after = Objects.requireNonNull(
                            auxiliary.biomeTransform().transform(biome.payload(), placement),
                            "transformed biome payload").clone();
                    if (!Arrays.equals(before, after)) {
                        extensions.add(frameAtBlock(
                                HistoryExtensionTypes.BIOME,
                                world.x(), world.y(), world.z(),
                                before, after));
                    }
                }
            }

            if (!snapshot.entities().isEmpty()) {
                if (auxiliary.entities() == null) {
                    throw new IllegalArgumentException(
                            "entity source is required for structure snapshots containing entities");
                }
                for (StructureEntity entity : snapshot.entities()) {
                    StructurePlacement.WorldPositionD world =
                            placement.transform(entity.x(), entity.y(), entity.z());
                    long key = entityKey++;
                    byte[] before = Objects.requireNonNull(
                            auxiliary.entities().read(key, world),
                            "entity before payload").clone();
                    byte[] after = Objects.requireNonNull(
                            auxiliary.entityTransform().transform(entity.payload(), placement),
                            "transformed entity payload").clone();
                    if (!Arrays.equals(before, after)) {
                        int blockX = floorToInt(world.x());
                        int blockZ = floorToInt(world.z());
                        extensions.add(new HistoryExtensionFrame(
                                HistoryExtensionTypes.ENTITY,
                                Math.floorDiv(blockX, 16),
                                Math.floorDiv(blockZ, 16),
                                key,
                                before,
                                after
                        ));
                    }
                }
            }
        }

        LinkedHashMap<ChunkKey, ChunkChangeSetBuilder> chunks = new LinkedHashMap<>();
        for (DesiredBlock block : desired.values()) {
            String before = requireState(
                    existing.stateAt(block.x, block.y, block.z),
                    "existing block state"
            );
            if (before.equals(block.afterState)) continue;
            int chunkX = Math.floorDiv(block.x, 16);
            int chunkZ = Math.floorDiv(block.z, 16);
            chunks.computeIfAbsent(
                    new ChunkKey(chunkX, chunkZ),
                    key -> new ChunkChangeSetBuilder(key.chunkX, key.chunkZ)
            ).addWorld(block.x, block.y, block.z, before, block.afterState);
        }

        return new StructurePastePlan(
                chunks.values().stream().map(ChunkChangeSetBuilder::build).toList(),
                extensions
        );
    }

    private static HistoryExtensionFrame frameAtBlock(
            String typeId,
            int worldX,
            int y,
            int worldZ,
            byte[] before,
            byte[] after
    ) {
        return new HistoryExtensionFrame(
                typeId,
                Math.floorDiv(worldX, 16),
                Math.floorDiv(worldZ, 16),
                LocalBlockPosition.pack(
                        Math.floorMod(worldX, 16),
                        y,
                        Math.floorMod(worldZ, 16)
                ),
                before,
                after
        );
    }

    private static int floorToInt(double value) {
        double floored = Math.floor(value);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("entity position exceeds integer world range");
        }
        return (int) floored;
    }

    private static IllegalArgumentException collision(int x, int y, int z) {
        return new IllegalArgumentException(
                "structure placement collision at " + x + "," + y + "," + z);
    }

    private static String requireState(String state, String label) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return state;
    }

    private record WorldKey(int x, int y, int z) {}
    private record ExtensionWorldKey(int x, int y, int z) {}
    private record ChunkKey(int chunkX, int chunkZ) {}
    private record DesiredBlock(int x, int y, int z, String afterState) {}

}

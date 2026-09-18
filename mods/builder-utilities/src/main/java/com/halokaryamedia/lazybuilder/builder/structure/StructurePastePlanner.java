package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSetBuilder;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.material.BlockStateSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure block-only structure paste planner producing chunk-local History v2 deltas. */
public final class StructurePastePlanner {
    private StructurePastePlanner() {}

    public static List<ChunkChangeSet> plan(
            StructureSnapshot snapshot,
            StructurePlacement placement,
            BlockStateTransform stateTransform,
            BlockStateSource existing
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(stateTransform, "stateTransform");
        Objects.requireNonNull(existing, "existing");

        LinkedHashMap<ChunkKey, ChunkChangeSetBuilder> chunks = new LinkedHashMap<>();
        for (StructureBlock block : snapshot.blocks()) {
            StructurePlacement.WorldPosition world =
                    placement.transform(block.x(), block.y(), block.z());
            String before = requireState(existing.stateAt(world.x(), world.y(), world.z()), "existing state");
            String after = requireState(stateTransform.transform(block.blockState(), placement), "transformed state");
            if (before.equals(after)) continue;

            int chunkX = Math.floorDiv(world.x(), 16);
            int chunkZ = Math.floorDiv(world.z(), 16);
            ChunkChangeSetBuilder builder = chunks.computeIfAbsent(
                    new ChunkKey(chunkX, chunkZ),
                    key -> new ChunkChangeSetBuilder(key.chunkX, key.chunkZ)
            );
            builder.addWorld(world.x(), world.y(), world.z(), before, after);
        }

        return chunks.values().stream()
                .map(ChunkChangeSetBuilder::build)
                .toList();
    }

    public static StructurePastePlan planWithBlockEntities(
            StructureSnapshot snapshot,
            StructurePlacement placement,
            BlockStateTransform stateTransform,
            BlockStateSource existing,
            StructureBlockEntityStateSource blockEntities,
            BlockEntityPayloadTransform payloadTransform
    ) throws IOException {
        Objects.requireNonNull(blockEntities, "blockEntities");
        Objects.requireNonNull(payloadTransform, "payloadTransform");

        LinkedHashMap<ChunkKey, ChunkChangeSetBuilder> chunkBuilders = new LinkedHashMap<>();
        for (ChunkChangeSet chunk : plan(snapshot, placement, stateTransform, existing)) {
            ChunkChangeSetBuilder builder =
                    new ChunkChangeSetBuilder(chunk.chunkX(), chunk.chunkZ());
            long[] positions = chunk.positions();
            for (int i = 0; i < positions.length; i++) {
                long packed = positions[i];
                int worldX = Math.addExact(
                        Math.multiplyExact(chunk.chunkX(), 16),
                        LocalBlockPosition.localX(packed));
                int worldZ = Math.addExact(
                        Math.multiplyExact(chunk.chunkZ(), 16),
                        LocalBlockPosition.localZ(packed));
                builder.addWorld(
                        worldX,
                        LocalBlockPosition.y(packed),
                        worldZ,
                        chunk.beforeState(i),
                        chunk.afterState(i));
            }
            chunkBuilders.put(new ChunkKey(chunk.chunkX(), chunk.chunkZ()), builder);
        }

        Map<LocalPosition, StructureBlock> blocksByPosition = new LinkedHashMap<>();
        for (StructureBlock block : snapshot.blocks()) {
            blocksByPosition.put(
                    new LocalPosition(block.x(), block.y(), block.z()),
                    block);
        }

        List<HistoryExtensionFrame> extensions = new ArrayList<>();
        for (StructureBlockEntity blockEntity : snapshot.blockEntities()) {
            StructurePlacement.WorldPosition world =
                    placement.transform(blockEntity.x(), blockEntity.y(), blockEntity.z());
            byte[] before = Objects.requireNonNull(
                    blockEntities.read(world.x(), world.y(), world.z()),
                    "block entity before payload").clone();
            byte[] after = Objects.requireNonNull(
                    payloadTransform.transform(blockEntity.payload(), placement),
                    "block entity transformed payload").clone();
            if (Arrays.equals(before, after)) continue;

            StructureBlock sourceBlock = Objects.requireNonNull(
                    blocksByPosition.get(new LocalPosition(
                            blockEntity.x(), blockEntity.y(), blockEntity.z())),
                    "block entity source block");
            String beforeState = requireState(
                    existing.stateAt(world.x(), world.y(), world.z()),
                    "existing block state");
            String afterState = requireState(
                    stateTransform.transform(sourceBlock.blockState(), placement),
                    "transformed block state");

            int chunkX = Math.floorDiv(world.x(), 16);
            int chunkZ = Math.floorDiv(world.z(), 16);
            ChunkKey chunkKey = new ChunkKey(chunkX, chunkZ);
            ChunkChangeSetBuilder builder = chunkBuilders.computeIfAbsent(
                    chunkKey,
                    key -> new ChunkChangeSetBuilder(key.chunkX, key.chunkZ));
            if (beforeState.equals(afterState)) {
                builder.addWorldGuard(world.x(), world.y(), world.z(), beforeState);
            }

            long localKey = LocalBlockPosition.pack(
                    Math.floorMod(world.x(), 16),
                    world.y(),
                    Math.floorMod(world.z(), 16)
            );
            extensions.add(new HistoryExtensionFrame(
                    "lazybuilder:block_entity",
                    chunkX,
                    chunkZ,
                    localKey,
                    before,
                    after
            ));
        }

        List<ChunkChangeSet> chunks = chunkBuilders.values().stream()
                .map(ChunkChangeSetBuilder::build)
                .filter(chunk -> chunk.size() != 0)
                .toList();
        return new StructurePastePlan(chunks, extensions);
    }

    private static String requireState(String state, String label) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return state;
    }

    private record ChunkKey(int chunkX, int chunkZ) {}
    private record LocalPosition(int x, int y, int z) {}

}

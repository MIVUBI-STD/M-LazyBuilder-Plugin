package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
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

        LinkedHashMap<ChunkKey, ChunkBuilder> chunks = new LinkedHashMap<>();
        for (StructureBlock block : snapshot.blocks()) {
            StructurePlacement.WorldPosition world =
                    placement.transform(block.x(), block.y(), block.z());
            String before = requireState(existing.stateAt(world.x(), world.y(), world.z()), "existing state");
            String after = requireState(stateTransform.transform(block.blockState(), placement), "transformed state");
            if (before.equals(after)) continue;

            int chunkX = Math.floorDiv(world.x(), 16);
            int chunkZ = Math.floorDiv(world.z(), 16);
            ChunkBuilder builder = chunks.computeIfAbsent(
                    new ChunkKey(chunkX, chunkZ),
                    key -> new ChunkBuilder(key.chunkX, key.chunkZ)
            );
            builder.add(world.x(), world.y(), world.z(), before, after);
        }

        return chunks.values().stream()
                .map(ChunkBuilder::build)
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

        List<ChunkChangeSet> chunks = plan(snapshot, placement, stateTransform, existing);
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

            int chunkX = Math.floorDiv(world.x(), 16);
            int chunkZ = Math.floorDiv(world.z(), 16);
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

        return new StructurePastePlan(chunks, extensions);
    }

    private static String requireState(String state, String label) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return state;
    }

    private record ChunkKey(int chunkX, int chunkZ) {}

    private static final class ChunkBuilder {
        private final int chunkX;
        private final int chunkZ;
        private final LinkedHashMap<String, Integer> palette = new LinkedHashMap<>();
        private final List<Long> positions = new ArrayList<>();
        private final List<Integer> before = new ArrayList<>();
        private final List<Integer> after = new ArrayList<>();

        private ChunkBuilder(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private void add(int worldX, int y, int worldZ, String beforeState, String afterState) {
            positions.add(LocalBlockPosition.pack(
                    Math.floorMod(worldX, 16),
                    y,
                    Math.floorMod(worldZ, 16)
            ));
            before.add(paletteIndex(beforeState));
            after.add(paletteIndex(afterState));
        }

        private int paletteIndex(String state) {
            Integer existing = palette.get(state);
            if (existing != null) return existing;
            int next = palette.size();
            palette.put(state, next);
            return next;
        }

        private ChunkChangeSet build() {
            long[] p = new long[positions.size()];
            int[] b = new int[before.size()];
            int[] a = new int[after.size()];
            for (int i = 0; i < p.length; i++) {
                p[i] = positions.get(i);
                b[i] = before.get(i);
                a[i] = after.get(i);
            }
            return new ChunkChangeSet(
                    chunkX,
                    chunkZ,
                    List.copyOf(palette.keySet()),
                    p,
                    b,
                    a
            );
        }
    }
}

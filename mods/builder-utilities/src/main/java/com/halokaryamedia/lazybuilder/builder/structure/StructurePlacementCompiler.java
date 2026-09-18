package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.material.BlockStateSource;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiles structure placement entries into exact chunk-local block deltas.
 * This is the neutral bridge between placement source IDs and the mutation/history engine.
 */
public final class StructurePlacementCompiler {
    private StructurePlacementCompiler() {}

    public static List<ChunkChangeSet> compile(
            List<PlacementPlanEntry> placements,
            StructureTemplateSource templates,
            BlockStateSource existing,
            StructureOverlapPolicy overlapPolicy
    ) {
        Objects.requireNonNull(placements, "placements");
        Objects.requireNonNull(templates, "templates");
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(overlapPolicy, "overlapPolicy");

        LinkedHashMap<WorldKey, String> desired = new LinkedHashMap<>();
        for (PlacementPlanEntry placement : placements) {
            Objects.requireNonNull(placement, "placement");
            StructureTemplate template = Objects.requireNonNull(
                    templates.resolve(placement.sourceId()), "structure template");
            BlockAlignedTransform transform =
                    BlockAlignedTransform.fromPlacement(placement.transform(), 1e-6);

            for (StructureBlock block : template.blocks()) {
                BlockAlignedTransform.LocalPoint local = transform.apply(block.x(), block.y(), block.z());
                WorldKey key = new WorldKey(
                        Math.addExact(placement.point().x(), local.x()),
                        Math.addExact(placement.point().y(), local.y()),
                        Math.addExact(placement.point().z(), local.z())
                );
                String previous = desired.get(key);
                if (previous == null) {
                    desired.put(key, block.blockState());
                    continue;
                }
                switch (overlapPolicy) {
                    case FIRST_WINS -> { }
                    case LAST_WINS -> desired.put(key, block.blockState());
                    case ERROR -> throw new IllegalArgumentException(
                            "structure placements overlap at " + key);
                }
            }
        }

        LinkedHashMap<ChunkKey, ChunkBuilder> chunks = new LinkedHashMap<>();
        for (Map.Entry<WorldKey, String> entry : desired.entrySet()) {
            WorldKey pos = entry.getKey();
            String before = requireState(existing.stateAt(pos.x(), pos.y(), pos.z()));
            String after = entry.getValue();
            if (before.equals(after)) continue;
            int chunkX = Math.floorDiv(pos.x(), 16);
            int chunkZ = Math.floorDiv(pos.z(), 16);
            chunks.computeIfAbsent(new ChunkKey(chunkX, chunkZ), ignored -> new ChunkBuilder(chunkX, chunkZ))
                    .add(pos.x(), pos.y(), pos.z(), before, after);
        }

        List<ChunkChangeSet> result = new ArrayList<>(chunks.size());
        for (ChunkBuilder builder : chunks.values()) result.add(builder.build());
        return List.copyOf(result);
    }

    private static String requireState(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("existing block state must be non-blank");
        }
        return value;
    }

    private record WorldKey(int x, int y, int z) {}
    private record ChunkKey(int x, int z) {}

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

        private void add(int x, int y, int z, String beforeState, String afterState) {
            positions.add(LocalBlockPosition.pack(Math.floorMod(x, 16), y, Math.floorMod(z, 16)));
            before.add(index(beforeState));
            after.add(index(afterState));
        }

        private int index(String state) {
            Integer existing = palette.get(state);
            if (existing != null) return existing;
            int index = palette.size();
            palette.put(state, index);
            return index;
        }

        private ChunkChangeSet build() {
            long[] packed = new long[positions.size()];
            int[] beforeArray = new int[positions.size()];
            int[] afterArray = new int[positions.size()];
            for (int i = 0; i < positions.size(); i++) {
                packed[i] = positions.get(i);
                beforeArray[i] = before.get(i);
                afterArray[i] = after.get(i);
            }
            return new ChunkChangeSet(
                    chunkX,
                    chunkZ,
                    List.copyOf(palette.keySet()),
                    packed,
                    beforeArray,
                    afterArray
            );
        }
    }
}

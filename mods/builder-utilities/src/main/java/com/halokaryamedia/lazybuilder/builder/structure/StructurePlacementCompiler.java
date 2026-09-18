package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSetBuilder;
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

        LinkedHashMap<ChunkKey, ChunkChangeSetBuilder> chunks = new LinkedHashMap<>();
        for (Map.Entry<WorldKey, String> entry : desired.entrySet()) {
            WorldKey pos = entry.getKey();
            String before = requireState(existing.stateAt(pos.x(), pos.y(), pos.z()));
            String after = entry.getValue();
            if (before.equals(after)) continue;
            int chunkX = Math.floorDiv(pos.x(), 16);
            int chunkZ = Math.floorDiv(pos.z(), 16);
            chunks.computeIfAbsent(
                            new ChunkKey(chunkX, chunkZ),
                            ignored -> new ChunkChangeSetBuilder(chunkX, chunkZ))
                    .addWorld(pos.x(), pos.y(), pos.z(), before, after);
        }

        List<ChunkChangeSet> result = new ArrayList<>(chunks.size());
        for (ChunkChangeSetBuilder builder : chunks.values()) result.add(builder.build());
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

}

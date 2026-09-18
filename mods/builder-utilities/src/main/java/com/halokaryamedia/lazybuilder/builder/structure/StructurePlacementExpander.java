package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.material.BlockStateSource;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Expands abstract placement source IDs into concrete block-only structure deltas.
 *
 * <p>Block structures currently support unit scale and quarter-turn Y rotation only.
 * Unsupported transforms fail explicitly instead of silently distorting structures.</p>
 */
public final class StructurePlacementExpander {
    private static final double EPSILON = 1.0e-6;

    private StructurePlacementExpander() {}

    public static List<ChunkChangeSet> expand(
            List<PlacementPlanEntry> placements,
            StructureSourceResolver resolver,
            BlockStateTransform stateTransform,
            BlockStateSource existing
    ) {
        Objects.requireNonNull(placements, "placements");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(stateTransform, "stateTransform");
        Objects.requireNonNull(existing, "existing");

        List<DesiredBlock> desired = new ArrayList<>();
        Set<WorldKey> occupied = new HashSet<>();

        for (PlacementPlanEntry entry : placements) {
            Objects.requireNonNull(entry, "entry");
            StructureSnapshot snapshot = Objects.requireNonNull(
                    resolver.resolve(entry.sourceId()),
                    "resolved structure"
            );
            StructurePlacement placement = toStructurePlacement(entry);

            for (StructureBlock block : snapshot.blocks()) {
                StructurePlacement.WorldPosition world =
                        placement.transform(block.x(), block.y(), block.z());
                WorldKey key = new WorldKey(world.x(), world.y(), world.z());
                if (!occupied.add(key)) {
                    throw new IllegalArgumentException(
                            "structure placement collision at "
                                    + world.x() + "," + world.y() + "," + world.z());
                }

                String state = stateTransform.transform(block.blockState(), placement);
                if (state == null || state.isBlank()) {
                    throw new IllegalArgumentException("transformed structure block state must be non-blank");
                }
                desired.add(new DesiredBlock(world.x(), world.y(), world.z(), state));
            }
        }

        LinkedHashMap<ChunkKey, ChunkBuilder> chunks = new LinkedHashMap<>();
        for (DesiredBlock block : desired) {
            String before = existing.stateAt(block.x, block.y, block.z);
            if (before == null || before.isBlank()) {
                throw new IllegalArgumentException("existing block state must be non-blank");
            }
            if (before.equals(block.afterState)) continue;

            int chunkX = Math.floorDiv(block.x, 16);
            int chunkZ = Math.floorDiv(block.z, 16);
            ChunkBuilder builder = chunks.computeIfAbsent(
                    new ChunkKey(chunkX, chunkZ),
                    key -> new ChunkBuilder(key.chunkX, key.chunkZ)
            );
            builder.add(block.x, block.y, block.z, before, block.afterState);
        }

        return chunks.values().stream().map(ChunkBuilder::build).toList();
    }

    private static StructurePlacement toStructurePlacement(PlacementPlanEntry entry) {
        PlacementTransform transform = entry.transform();
        if (Math.abs(transform.scale() - 1.0) > EPSILON) {
            throw new IllegalArgumentException(
                    "block structures require unit scale; requested " + transform.scale());
        }

        double quarter = transform.yawDegrees() / 90.0;
        long rounded = Math.round(quarter);
        if (Math.abs(quarter - rounded) > EPSILON) {
            throw new IllegalArgumentException(
                    "block structures require yaw in 90-degree increments; requested "
                            + transform.yawDegrees());
        }

        return new StructurePlacement(
                entry.point().x(),
                entry.point().y(),
                entry.point().z(),
                Math.toIntExact(rounded),
                transform.mirrorX(),
                false
        );
    }

    private record DesiredBlock(int x, int y, int z, String afterState) {}
    private record WorldKey(int x, int y, int z) {}
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
            Integer index = palette.get(state);
            if (index != null) return index;
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

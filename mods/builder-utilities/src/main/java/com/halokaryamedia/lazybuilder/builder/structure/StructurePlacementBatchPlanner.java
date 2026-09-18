package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
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
            return plan(
                    placements,
                    resolver,
                    stateTransform,
                    existing,
                    null,
                    BlockEntityPayloadTransform.identity()
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
        Objects.requireNonNull(placements, "placements");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(stateTransform, "stateTransform");
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(payloadTransform, "payloadTransform");

        LinkedHashMap<WorldKey, DesiredBlock> desired = new LinkedHashMap<>();
        List<DesiredExtension> desiredExtensions = new ArrayList<>();
        Set<ExtensionWorldKey> extensionKeys = new HashSet<>();

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
                if (blockEntitySource == null) {
                    throw new IllegalArgumentException(
                            "block entity source is required for structure snapshots containing block entities");
                }
                for (StructureBlockEntity blockEntity : snapshot.blockEntities()) {
                    StructurePlacement.WorldPosition world =
                            placement.transform(blockEntity.x(), blockEntity.y(), blockEntity.z());
                    ExtensionWorldKey key = new ExtensionWorldKey(world.x(), world.y(), world.z());
                    if (!extensionKeys.add(key)) {
                        throw new IllegalArgumentException(
                                "structure block entity collision at "
                                        + world.x() + "," + world.y() + "," + world.z());
                    }
                    byte[] before = Objects.requireNonNull(
                            blockEntitySource.read(world.x(), world.y(), world.z()),
                            "block entity before payload"
                    ).clone();
                    byte[] after = Objects.requireNonNull(
                            payloadTransform.transform(blockEntity.payload(), placement),
                            "transformed block entity payload"
                    ).clone();
                    if (!Arrays.equals(before, after)) {
                        desiredExtensions.add(new DesiredExtension(
                                world.x(), world.y(), world.z(), before, after));
                    }
                }
            }
        }

        LinkedHashMap<ChunkKey, ChunkBuilder> chunks = new LinkedHashMap<>();
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
                    key -> new ChunkBuilder(key.chunkX, key.chunkZ)
            ).add(block.x, block.y, block.z, before, block.afterState);
        }

        List<HistoryExtensionFrame> extensions = new ArrayList<>(desiredExtensions.size());
        for (DesiredExtension extension : desiredExtensions) {
            int chunkX = Math.floorDiv(extension.x, 16);
            int chunkZ = Math.floorDiv(extension.z, 16);
            extensions.add(new HistoryExtensionFrame(
                    HistoryExtensionTypes.BLOCK_ENTITY,
                    chunkX,
                    chunkZ,
                    LocalBlockPosition.pack(
                            Math.floorMod(extension.x, 16),
                            extension.y,
                            Math.floorMod(extension.z, 16)
                    ),
                    extension.beforePayload,
                    extension.afterPayload
            ));
        }

        return new StructurePastePlan(
                chunks.values().stream().map(ChunkBuilder::build).toList(),
                extensions
        );
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
    private record DesiredExtension(int x, int y, int z, byte[] beforePayload, byte[] afterPayload) {}

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

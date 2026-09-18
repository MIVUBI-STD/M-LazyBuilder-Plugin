package com.halokaryamedia.lazybuilder.builder.history;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared deterministic chunk delta builder.
 *
 * <p>Repeated writes to one position are coalesced only when their state chain is
 * continuous: previous AFTER must equal next BEFORE. The first BEFORE and final
 * AFTER are retained; a chain that returns to its original state is removed.</p>
 */
public final class ChunkChangeSetBuilder {
    private final int chunkX;
    private final int chunkZ;
    private final LinkedHashMap<Long, Change> changes = new LinkedHashMap<>();

    public ChunkChangeSetBuilder(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int size() {
        return changes.size();
    }

    public void addWorld(
            int worldX,
            int y,
            int worldZ,
            String beforeState,
            String afterState
    ) {
        requireState(beforeState, "beforeState");
        requireState(afterState, "afterState");
        if (Math.floorDiv(worldX, 16) != chunkX || Math.floorDiv(worldZ, 16) != chunkZ) {
            throw new IllegalArgumentException("world position does not belong to builder chunk");
        }
        if (beforeState.equals(afterState)) return;

        long packed = LocalBlockPosition.pack(
                Math.floorMod(worldX, 16),
                y,
                Math.floorMod(worldZ, 16)
        );
        Change previous = changes.get(packed);
        if (previous == null) {
            changes.put(packed, new Change(beforeState, afterState));
            return;
        }
        if (!previous.after.equals(beforeState)) {
            throw new IllegalArgumentException(
                    "non-contiguous duplicate write at local position " + packed
                            + ": previous after=" + previous.after
                            + ", next before=" + beforeState);
        }
        if (previous.before.equals(afterState)) {
            changes.remove(packed);
        } else {
            changes.put(packed, new Change(previous.before, afterState));
        }
    }

    /**
     * Adds a no-op state guard used by extension CAS paths that must preserve
     * the exact block-state precondition even when the block itself does not change.
     */
    public void addWorldGuard(
            int worldX,
            int y,
            int worldZ,
            String state
    ) {
        requireState(state, "state");
        if (Math.floorDiv(worldX, 16) != chunkX || Math.floorDiv(worldZ, 16) != chunkZ) {
            throw new IllegalArgumentException("world position does not belong to builder chunk");
        }
        long packed = LocalBlockPosition.pack(
                Math.floorMod(worldX, 16),
                y,
                Math.floorMod(worldZ, 16)
        );
        changes.putIfAbsent(packed, new Change(state, state));
    }

    public ChunkChangeSet build() {
        LinkedHashMap<String, Integer> palette = new LinkedHashMap<>();
        long[] positions = new long[changes.size()];
        int[] before = new int[changes.size()];
        int[] after = new int[changes.size()];

        int i = 0;
        for (Map.Entry<Long, Change> entry : changes.entrySet()) {
            positions[i] = entry.getKey();
            before[i] = paletteIndex(palette, entry.getValue().before);
            after[i] = paletteIndex(palette, entry.getValue().after);
            i++;
        }

        List<String> orderedPalette = new ArrayList<>(palette.size());
        palette.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> orderedPalette.add(entry.getKey()));
        return new ChunkChangeSet(
                chunkX,
                chunkZ,
                orderedPalette,
                positions,
                before,
                after
        );
    }

    private static int paletteIndex(LinkedHashMap<String, Integer> palette, String state) {
        Integer existing = palette.get(state);
        if (existing != null) return existing;
        int index = palette.size();
        palette.put(state, index);
        return index;
    }

    private static void requireState(String state, String label) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private record Change(String before, String after) {}
}

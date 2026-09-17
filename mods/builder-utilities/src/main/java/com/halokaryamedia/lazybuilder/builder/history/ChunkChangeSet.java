package com.halokaryamedia.lazybuilder.builder.history;

import java.util.List;
import java.util.Objects;

/**
 * Immutable chunk-local history delta using palette indexes and primitive arrays.
 */
public record ChunkChangeSet(
        int chunkX,
        int chunkZ,
        List<String> palette,
        long[] positions,
        int[] beforeStates,
        int[] afterStates
) {
    public ChunkChangeSet {
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(beforeStates, "beforeStates");
        Objects.requireNonNull(afterStates, "afterStates");
        palette = List.copyOf(palette);
        positions = positions.clone();
        beforeStates = beforeStates.clone();
        afterStates = afterStates.clone();
        if (positions.length != beforeStates.length || positions.length != afterStates.length) {
            throw new IllegalArgumentException("History arrays must have identical lengths");
        }
        for (String state : palette) {
            if (state == null || state.isBlank()) {
                throw new IllegalArgumentException("Palette states must be non-blank");
            }
        }
        for (int index : beforeStates) {
            validatePaletteIndex(index, palette.size());
        }
        for (int index : afterStates) {
            validatePaletteIndex(index, palette.size());
        }
    }

    @Override
    public long[] positions() {
        return positions.clone();
    }

    @Override
    public int[] beforeStates() {
        return beforeStates.clone();
    }

    @Override
    public int[] afterStates() {
        return afterStates.clone();
    }

    public int size() {
        return positions.length;
    }

    public String beforeState(int changeIndex) {
        return palette.get(beforeStates[changeIndex]);
    }

    public String afterState(int changeIndex) {
        return palette.get(afterStates[changeIndex]);
    }

    private static void validatePaletteIndex(int index, int paletteSize) {
        if (index < 0 || index >= paletteSize) {
            throw new IllegalArgumentException("Palette index out of range: " + index);
        }
    }
}

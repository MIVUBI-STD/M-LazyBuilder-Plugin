package com.halokaryamedia.lazybuilder.builder.mutation;

public record ChunkDispatchEntry(int worldX, int y, int worldZ, String afterState) {
    public ChunkDispatchEntry {
        if (afterState == null || afterState.isBlank()) {
            throw new IllegalArgumentException("afterState must be non-blank");
        }
    }
}

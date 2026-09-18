package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;

import java.util.List;
import java.util.Objects;

public record StructurePastePlan(
        List<ChunkChangeSet> chunks,
        List<HistoryExtensionFrame> extensions
) {
    public StructurePastePlan {
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(extensions, "extensions");
        chunks = List.copyOf(chunks);
        extensions = List.copyOf(extensions);
    }

    public long blockChanges() {
        long total = 0L;
        for (ChunkChangeSet chunk : chunks) {
            for (int i = 0; i < chunk.size(); i++) {
                if (!chunk.beforeState(i).equals(chunk.afterState(i))) {
                    total = Math.addExact(total, 1L);
                }
            }
        }
        return total;
    }

    public long historyBlockEntries() {
        return chunks.stream().mapToLong(ChunkChangeSet::size).sum();
    }

    public int extensionChanges() {
        return extensions.size();
    }
}

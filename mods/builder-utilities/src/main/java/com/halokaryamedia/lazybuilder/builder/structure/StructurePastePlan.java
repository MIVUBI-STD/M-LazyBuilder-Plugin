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
        return chunks.stream().mapToLong(ChunkChangeSet::size).sum();
    }

    public int extensionChanges() {
        return extensions.size();
    }
}

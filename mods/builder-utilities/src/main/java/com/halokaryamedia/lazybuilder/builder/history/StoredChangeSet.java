package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;

public interface StoredChangeSet extends AutoCloseable {
    String operationId();

    HistoryStorageTier storageTier();

    long changeCount();

    long extensionCount();

    void replayAll(ReplayDirection direction, HistoryReplayConsumer consumer) throws IOException;

    /**
     * Streams raw committed chunk deltas without expanding them into per-block callbacks.
     * Implementations must still validate the complete committed stream even when the
     * visitor asks to stop receiving callbacks early.
     */
    void visitChunks(ChunkChangeSetVisitor visitor) throws IOException;

    /** Streams opaque extension frames while still validating the complete committed stream. */
    void visitExtensions(HistoryExtensionVisitor visitor) throws IOException;

    default void replay(ReplayDirection direction, BlockChangeConsumer consumer) throws IOException {
        replayAll(direction, HistoryReplayConsumer.blocksOnly(consumer));
    }

    @Override
    default void close() throws IOException {
    }
}

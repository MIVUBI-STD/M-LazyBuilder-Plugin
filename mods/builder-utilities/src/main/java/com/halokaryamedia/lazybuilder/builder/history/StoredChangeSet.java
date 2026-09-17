package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;

public interface StoredChangeSet extends AutoCloseable {
    String operationId();

    HistoryStorageTier storageTier();

    long changeCount();

    long extensionCount();

    void replayAll(ReplayDirection direction, HistoryReplayConsumer consumer) throws IOException;

    default void replay(ReplayDirection direction, BlockChangeConsumer consumer) throws IOException {
        replayAll(direction, HistoryReplayConsumer.blocksOnly(consumer));
    }

    @Override
    default void close() throws IOException {
    }
}

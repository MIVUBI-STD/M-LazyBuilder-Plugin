package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;

public interface StoredChangeSet extends AutoCloseable {
    String operationId();

    HistoryStorageTier storageTier();

    long changeCount();

    void replay(ReplayDirection direction, BlockChangeConsumer consumer) throws IOException;

    @Override
    default void close() throws IOException {
    }
}

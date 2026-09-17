package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;

public interface ChangeSetWriter extends AutoCloseable {
    void append(ChunkChangeSet chunk) throws IOException;

    StoredChangeSet commit() throws IOException;

    void abort() throws IOException;

    @Override
    default void close() throws IOException {
        abort();
    }
}

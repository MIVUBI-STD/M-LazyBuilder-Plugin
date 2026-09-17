package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;

/**
 * Stateful forward-only cursor over committed History v2 chunk frames.
 * The cursor keeps its underlying stream open so large operations can yield and
 * resume without rescanning earlier chunks.
 */
public interface ChunkChangeSetCursor extends AutoCloseable {
    String operationId();

    long observedChanges();

    long observedExtensions();

    boolean exhausted();

    /** Returns the next chunk, or {@code null} after the validated commit footer. */
    ChunkChangeSet nextChunk() throws IOException;

    @Override
    void close() throws IOException;
}

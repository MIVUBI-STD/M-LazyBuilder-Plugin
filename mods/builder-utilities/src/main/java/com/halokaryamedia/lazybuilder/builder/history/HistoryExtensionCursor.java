package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;

public interface HistoryExtensionCursor extends AutoCloseable {
    String operationId();
    long observedExtensions();
    boolean exhausted();
    HistoryExtensionFrame nextExtension() throws IOException;
    @Override void close();
}

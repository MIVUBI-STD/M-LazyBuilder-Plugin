package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;

@FunctionalInterface
public interface HistoryExtensionVisitor {
    /**
     * @return true to keep receiving callbacks, false to suppress later callbacks
     * while the codec continues validating the committed stream.
     */
    boolean visit(HistoryExtensionFrame frame) throws IOException;
}

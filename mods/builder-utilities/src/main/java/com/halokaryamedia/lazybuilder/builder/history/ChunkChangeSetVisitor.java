package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;

@FunctionalInterface
public interface ChunkChangeSetVisitor {
    /**
     * @return true to keep receiving chunk callbacks; false to stop callbacks
     * while the codec continues scanning to verify the committed stream footer.
     */
    boolean visit(ChunkChangeSet chunk) throws IOException;
}

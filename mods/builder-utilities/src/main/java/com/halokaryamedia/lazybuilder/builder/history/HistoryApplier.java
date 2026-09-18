package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;

@FunctionalInterface
public interface HistoryApplier {
    /**
     * Applies one stored changeset through the world-mutation authority.
     * Implementations own any transactional rollback required when application fails.
     */
    void apply(StoredChangeSet changeSet, ReplayDirection direction) throws IOException;
}

package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;

public interface PreparedBlockMutation extends AutoCloseable {
    StoredChangeSet changeSet();

    long plannedChanges();

    @Override
    default void close() throws IOException {
        changeSet().close();
    }
}

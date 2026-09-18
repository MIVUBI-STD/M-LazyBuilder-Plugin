package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedBlockMutation;

import java.io.IOException;
import java.util.Objects;

public record PreparedMaterialMutation(StoredChangeSet changeSet, long plannedChanges) implements PreparedBlockMutation {
    public PreparedMaterialMutation {
        Objects.requireNonNull(changeSet, "changeSet");
        if (plannedChanges < 0) throw new IllegalArgumentException("plannedChanges must be >= 0");
        if (changeSet.changeCount() != plannedChanges) {
            throw new IllegalArgumentException("Stored changeset count does not match plannedChanges");
        }
    }

    @Override
    public void close() throws IOException {
        changeSet.close();
    }
}

package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedBlockMutation;

import java.io.IOException;
import java.util.Objects;

public record PreparedStructureMutation(
        StoredChangeSet changeSet,
        long plannedChanges,
        long plannedExtensions
) implements PreparedBlockMutation {
    public PreparedStructureMutation {
        Objects.requireNonNull(changeSet, "changeSet");
        if (plannedChanges < 0 || plannedExtensions < 0) {
            throw new IllegalArgumentException("planned counts must be >= 0");
        }
        if (changeSet.changeCount() != plannedChanges
                || changeSet.extensionCount() != plannedExtensions) {
            throw new IllegalArgumentException("Stored changeset counts do not match structure plan");
        }
    }

    @Override
    public void close() throws IOException {
        changeSet.close();
    }
}

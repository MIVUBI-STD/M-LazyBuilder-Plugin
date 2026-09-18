package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedBlockMutation;

import java.util.Objects;

public record PreparedStructureMutation(
        StoredChangeSet changeSet,
        long plannedChanges
) implements PreparedBlockMutation {
    public PreparedStructureMutation {
        Objects.requireNonNull(changeSet, "changeSet");
        if (plannedChanges < 0) throw new IllegalArgumentException("plannedChanges must be >= 0");
        if (changeSet.changeCount() != plannedChanges) {
            throw new IllegalArgumentException("Stored changeset count does not match plannedChanges");
        }
        if (changeSet.extensionCount() != 0) {
            throw new IllegalArgumentException("PreparedStructureMutation is block-only");
        }
    }
}

package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** Executes opaque History extension frames through adapter-owned mutation targets. */
public final class PreparedExtensionMutationExecutor {
    private PreparedExtensionMutationExecutor() {}

    public static ExtensionMutationExecution execute(
            StoredChangeSet prepared,
            HistoryExtensionTargetRegistry registry,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(cancellationToken, "cancellationToken");

        Mutable state = new Mutable();
        prepared.visitExtensions(frame -> {
            if (state.terminal || cancellationToken.isCancellationRequested()) {
                state.cancelled = true;
                return false;
            }

            HistoryExtensionMutationTarget target = registry.require(frame.typeId());
            byte[] actual = Objects.requireNonNull(target.read(frame), "extension target read payload");
            byte[] before = frame.beforePayload();
            byte[] after = frame.afterPayload();

            if (Arrays.equals(actual, after)) {
                state.applied++;
                return true;
            }
            if (!Arrays.equals(actual, before)) {
                state.conflict = frame;
                state.terminal = true;
                return false;
            }

            target.write(frame, after);
            byte[] verified = Objects.requireNonNull(target.read(frame), "extension target verify payload");
            if (!Arrays.equals(verified, after)) {
                state.conflict = frame;
                state.terminal = true;
                return false;
            }
            state.applied++;
            return true;
        });

        if (state.conflict != null) {
            HistoryExtensionFrame conflict = state.conflict;
            return new ExtensionMutationExecution(
                    prepared.extensionCount(), state.applied, MutationExecutionState.CONFLICT,
                    conflict.typeId(), conflict.chunkX(), conflict.chunkZ(), conflict.localKey());
        }
        if (state.cancelled) {
            return new ExtensionMutationExecution(
                    prepared.extensionCount(), state.applied, MutationExecutionState.CANCELLED,
                    null, null, null, null);
        }
        return new ExtensionMutationExecution(
                prepared.extensionCount(), state.applied, MutationExecutionState.COMPLETED,
                null, null, null, null);
    }

    private static final class Mutable {
        long applied;
        boolean cancelled;
        boolean terminal;
        HistoryExtensionFrame conflict;
    }
}

package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.ReplayDirection;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Preconditions and verifies opaque extension writes. Undo uses global reverse
 * extension order; redo preserves committed order.
 */
public final class HistoryExtensionMutationExecutor {
    private HistoryExtensionMutationExecutor() {}

    public static ExtensionMutationExecution apply(
            StoredChangeSet stored,
            ReplayDirection direction,
            HistoryExtensionMutationTarget target,
            CancellationToken cancellation
    ) throws IOException {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(cancellation, "cancellation");

        List<HistoryExtensionFrame> frames = new ArrayList<>();
        stored.visitExtensions(frame -> {
            frames.add(frame);
            return true;
        });
        if (direction == ReplayDirection.UNDO) Collections.reverse(frames);

        long processed = 0;
        for (HistoryExtensionFrame frame : frames) {
            if (cancellation.isCancellationRequested()) {
                return ExtensionMutationExecution.cancelled(stored.extensionCount(), processed);
            }
            byte[] expected = direction == ReplayDirection.REDO
                    ? frame.beforePayload()
                    : frame.afterPayload();
            byte[] desired = direction == ReplayDirection.REDO
                    ? frame.afterPayload()
                    : frame.beforePayload();
            byte[] actual = Objects.requireNonNull(
                    target.read(frame.typeId(), frame.chunkX(), frame.chunkZ(), frame.localKey()),
                    "extension actual payload");

            if (Arrays.equals(actual, desired)) {
                processed++;
                continue;
            }
            if (!Arrays.equals(actual, expected)) {
                return ExtensionMutationExecution.conflict(stored.extensionCount(), processed, frame);
            }

            target.write(
                    frame.typeId(),
                    frame.chunkX(),
                    frame.chunkZ(),
                    frame.localKey(),
                    desired.clone()
            );
            byte[] verified = Objects.requireNonNull(
                    target.read(frame.typeId(), frame.chunkX(), frame.chunkZ(), frame.localKey()),
                    "extension verified payload");
            if (!Arrays.equals(verified, desired)) {
                return ExtensionMutationExecution.conflict(stored.extensionCount(), processed, frame);
            }
            processed++;
        }
        return ExtensionMutationExecution.completed(stored.extensionCount());
    }
}

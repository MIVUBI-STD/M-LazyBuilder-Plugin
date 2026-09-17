package com.halokaryamedia.lazybuilder.builder.history;

import java.util.Objects;

/** Replay target for both block deltas and opaque extension frames. */
public interface HistoryReplayConsumer {
    void acceptBlock(int chunkX, int chunkZ, int localX, int y, int localZ, String state);

    default void acceptExtension(HistoryExtensionFrame frame, byte[] payload) {
    }

    static HistoryReplayConsumer blocksOnly(BlockChangeConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        return (chunkX, chunkZ, localX, y, localZ, state) ->
                consumer.accept(chunkX, chunkZ, localX, y, localZ, state);
    }
}

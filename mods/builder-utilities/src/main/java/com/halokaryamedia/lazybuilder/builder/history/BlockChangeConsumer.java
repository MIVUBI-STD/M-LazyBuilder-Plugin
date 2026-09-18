package com.halokaryamedia.lazybuilder.builder.history;

@FunctionalInterface
public interface BlockChangeConsumer {
    void accept(int chunkX, int chunkZ, int localX, int y, int localZ, String state);
}

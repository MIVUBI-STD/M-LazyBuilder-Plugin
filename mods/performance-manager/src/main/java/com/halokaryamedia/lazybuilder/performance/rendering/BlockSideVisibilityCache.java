package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Bounded concurrent cache for vanilla block-side visibility decisions. */
public final class BlockSideVisibilityCache {
    private static final int MAX_ENTRIES = 16_384;
    private final ConcurrentMap<Key, Boolean> decisions = new ConcurrentHashMap<>();

    public Boolean get(BlockState state, BlockState otherState, Direction side) {
        if (state == null || otherState == null || side == null) return null;
        return decisions.get(new Key(state, otherState, side));
    }

    public void put(BlockState state, BlockState otherState, Direction side, boolean result) {
        if (state == null || otherState == null || side == null) return;
        if (decisions.size() >= MAX_ENTRIES) decisions.clear();
        decisions.putIfAbsent(new Key(state, otherState, side), result);
    }

    int size() {
        return decisions.size();
    }

    private static final class Key {
        private final BlockState state;
        private final BlockState otherState;
        private final Direction side;
        private final int hash;

        private Key(BlockState state, BlockState otherState, Direction side) {
            this.state = state;
            this.otherState = otherState;
            this.side = side;
            int result = System.identityHashCode(state);
            result = 31 * result + System.identityHashCode(otherState);
            result = 31 * result + side.ordinal();
            this.hash = result;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || (other instanceof Key key
                    && this.state == key.state
                    && this.otherState == key.otherState
                    && this.side == key.side);
        }
    }
}

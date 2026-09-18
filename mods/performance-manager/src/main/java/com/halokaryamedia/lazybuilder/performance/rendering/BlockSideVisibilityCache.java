package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Bounded concurrent cache for vanilla block-side visibility decisions.
 *
 * Chunk meshing queries this path extremely frequently, so hits use one reusable thread-local probe
 * rather than allocating a temporary key for every lookup. Immutable keys are allocated only for
 * genuinely new cache entries.
 */
public final class BlockSideVisibilityCache {
    private static final int MAX_ENTRIES = 16_384;

    private final ConcurrentMap<Key, Boolean> decisions = new ConcurrentHashMap<>();
    private final ThreadLocal<Probe> probes = ThreadLocal.withInitial(Probe::new);

    public Boolean get(BlockState state, BlockState otherState, Direction side) {
        if (state == null || otherState == null || side == null) return null;
        Probe probe = probes.get();
        probe.set(state, otherState, side);
        return decisions.get(probe);
    }

    public void put(BlockState state, BlockState otherState, Direction side, boolean result) {
        if (state == null || otherState == null || side == null) return;

        Probe probe = probes.get();
        probe.set(state, otherState, side);
        if (decisions.containsKey(probe)) return;

        if (decisions.size() >= MAX_ENTRIES) decisions.clear();
        decisions.putIfAbsent(new Key(state, otherState, side), result);
    }

    int size() {
        return decisions.size();
    }

    private interface IdentityKey {
        BlockState state();
        BlockState otherState();
        Direction side();
        int hash();
    }

    private static final class Probe implements IdentityKey {
        private BlockState state;
        private BlockState otherState;
        private Direction side;
        private int hash;

        void set(BlockState state, BlockState otherState, Direction side) {
            this.state = state;
            this.otherState = otherState;
            this.side = side;
            this.hash = hash(state, otherState, side);
        }

        @Override public BlockState state() { return state; }
        @Override public BlockState otherState() { return otherState; }
        @Override public Direction side() { return side; }
        @Override public int hash() { return hash; }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            return sameIdentity(this, other);
        }
    }

    private static final class Key implements IdentityKey {
        private final BlockState state;
        private final BlockState otherState;
        private final Direction side;
        private final int hash;

        private Key(BlockState state, BlockState otherState, Direction side) {
            this.state = state;
            this.otherState = otherState;
            this.side = side;
            this.hash = hash(state, otherState, side);
        }

        @Override public BlockState state() { return state; }
        @Override public BlockState otherState() { return otherState; }
        @Override public Direction side() { return side; }
        @Override public int hash() { return hash; }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            return sameIdentity(this, other);
        }
    }

    private static boolean sameIdentity(IdentityKey left, Object other) {
        return left == other || (other instanceof IdentityKey right
                && left.state() == right.state()
                && left.otherState() == right.otherState()
                && left.side() == right.side());
    }

    private static int hash(BlockState state, BlockState otherState, Direction side) {
        int result = System.identityHashCode(state);
        result = 31 * result + System.identityHashCode(otherState);
        result = 31 * result + side.ordinal();
        return result;
    }
}

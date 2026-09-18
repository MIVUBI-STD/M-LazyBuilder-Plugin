package com.halokaryamedia.lazybuilder.performance.rendering;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Runtime proof gate for future exclusive physical-arena ownership.
 *
 * A resident must survive a long sequence of successful physical draws without re-upload,
 * relocation, invalidation, or custom/sorted index ownership before it becomes a candidate.
 * This class only proves eligibility; it never removes the vanilla fallback itself.
 */
public final class TerrainOwnershipProofTracker<K> {
    static final long REQUIRED_STABLE_DRAWS = 600L;

    private final Map<K, State> states = new IdentityHashMap<>();
    private long totalProofDraws;
    private long proofResets;

    public void begin(K key, boolean customIndices) {
        if (key == null) return;
        State previous = states.put(key, new State(!customIndices));
        if (previous != null) proofResets++;
    }

    public void recordDraw(K key) {
        State state = states.get(key);
        if (state == null || !state.eligible) return;
        if (state.successfulDraws < Long.MAX_VALUE) state.successfulDraws++;
        if (totalProofDraws < Long.MAX_VALUE) totalProofDraws++;
    }

    public void reset(K key) {
        State state = states.get(key);
        if (state == null) return;
        state.successfulDraws = 0L;
        proofResets++;
    }

    public void invalidate(K key) {
        if (key != null && states.remove(key) != null) proofResets++;
    }

    public void release(K key) {
        if (key != null) states.remove(key);
    }

    public boolean isCandidate(K key) {
        State state = states.get(key);
        return state != null && state.eligible && state.successfulDraws >= REQUIRED_STABLE_DRAWS;
    }

    public Snapshot snapshot() {
        int observing = 0;
        int candidates = 0;
        int excluded = 0;
        for (State state : states.values()) {
            if (!state.eligible) {
                excluded++;
            } else if (state.successfulDraws >= REQUIRED_STABLE_DRAWS) {
                candidates++;
            } else {
                observing++;
            }
        }

        String status = candidates > 0
                ? "candidate"
                : observing > 0 ? "observing" : excluded > 0 ? "custom-index-excluded" : "no-residents";
        return new Snapshot(
                observing,
                candidates,
                excluded,
                totalProofDraws,
                proofResets,
                REQUIRED_STABLE_DRAWS,
                status
        );
    }

    public void clear() {
        states.clear();
        totalProofDraws = 0L;
        proofResets = 0L;
    }

    private static final class State {
        private final boolean eligible;
        private long successfulDraws;

        private State(boolean eligible) {
            this.eligible = eligible;
        }
    }

    public record Snapshot(
            int observingBuffers,
            int candidateBuffers,
            int excludedCustomIndexBuffers,
            long successfulProofDraws,
            long proofResets,
            long requiredStableDraws,
            String status
    ) {
    }
}

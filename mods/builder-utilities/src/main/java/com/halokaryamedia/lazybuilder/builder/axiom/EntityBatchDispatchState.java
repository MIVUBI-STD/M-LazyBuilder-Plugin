package com.halokaryamedia.lazybuilder.builder.axiom;

public enum EntityBatchDispatchState {
    YIELDED,
    WAITING,
    EXHAUSTED,
    CANCELLED,
    CONFLICT,
    FAILED
}

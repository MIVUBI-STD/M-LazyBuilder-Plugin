package com.halokaryamedia.lazybuilder.builder.axiom;

public enum BlockEntityBatchDispatchState {
    YIELDED,
    WAITING,
    EXHAUSTED,
    CANCELLED,
    CONFLICT,
    FAILED
}

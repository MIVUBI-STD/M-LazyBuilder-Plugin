package com.halokaryamedia.lazybuilder.builder.axiom;

public record AxiomMixedPumpResult(
        State state,
        String detail
) {
    public AxiomMixedPumpResult {
        if (state == null) throw new NullPointerException("state");
    }

    public enum State {
        RUNNING,
        WAITING,
        COMPLETED,
        CANCELLED,
        FAILED
    }
}

package com.halokaryamedia.lazybuilder.performance;

/** Converts current frame pressure into a permission for LazyBuilder-owned work. */
public final class WorkloadBudget {
    private FramePressure pressure = FramePressure.NORMAL;

    public void update(FramePressure pressure) {
        this.pressure = pressure == null ? FramePressure.NORMAL : pressure;
    }

    public FramePressure pressure() {
        return pressure;
    }

    public boolean allows(WorkClass workClass) {
        if (workClass == null) return false;
        return switch (pressure) {
            case NORMAL -> true;
            case ELEVATED -> workClass != WorkClass.DEFERRED;
            case HEAVY -> workClass == WorkClass.CRITICAL;
        };
    }
}

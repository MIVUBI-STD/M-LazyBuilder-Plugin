package com.halokaryamedia.lazybuilder.world.task;

/** Lifecycle states for long-running World-Manager tasks. */
public enum WorldTaskState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED
}

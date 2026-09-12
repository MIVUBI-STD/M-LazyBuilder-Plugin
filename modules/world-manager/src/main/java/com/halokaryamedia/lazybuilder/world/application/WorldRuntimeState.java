package com.halokaryamedia.lazybuilder.world.application;

/** Ephemeral Paper runtime state. This is never persisted as world lifecycle metadata. */
public enum WorldRuntimeState {
    LOADED,
    UNLOADED,
    LOADING,
    UNLOADING
}

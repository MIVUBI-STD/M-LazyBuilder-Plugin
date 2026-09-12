package com.halokaryamedia.lazybuilder.world.task;

/** One asynchronous World-Manager unit of work executed by {@link WorldTaskRunner}. */
@FunctionalInterface
public interface WorldTaskWork {
    String run(Progress progress) throws Exception;

    @FunctionalInterface
    interface Progress {
        void update(int progressPercent, String message);
    }
}

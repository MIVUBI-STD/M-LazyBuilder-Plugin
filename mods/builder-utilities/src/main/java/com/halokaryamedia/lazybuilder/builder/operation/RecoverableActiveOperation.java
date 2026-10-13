package com.halokaryamedia.lazybuilder.builder.operation;

import java.io.IOException;

/** Runtime-owned active operation that can detach its durable plan for later recovery. */
@FunctionalInterface
public interface RecoverableActiveOperation {
    void preserveForWorldExit() throws IOException;

    /** Advances one bounded client-tick slice while the owning world is active. */
    default void pump() { }

}

package com.halokaryamedia.lazybuilder.performance.shader;

import java.nio.file.Path;
import java.util.Objects;

/** Stable metadata for one first-party shader pack source. */
public record ShaderPackDescriptor(
        String id,
        String displayName,
        Path path,
        Kind kind
) {
    public ShaderPackDescriptor {
        id = Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName");
        path = Objects.requireNonNull(path, "path");
        kind = Objects.requireNonNull(kind, "kind");
    }

    public enum Kind {
        DIRECTORY,
        ZIP
    }
}

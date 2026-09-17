package com.halokaryamedia.lazybuilder.builder.spline;

import java.util.Objects;

public record SplineFrame(BuilderVec3 tangent, BuilderVec3 normal, BuilderVec3 binormal) {
    public SplineFrame {
        Objects.requireNonNull(tangent, "tangent");
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(binormal, "binormal");
    }
}

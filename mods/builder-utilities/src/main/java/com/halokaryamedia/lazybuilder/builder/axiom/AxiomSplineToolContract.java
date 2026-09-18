package com.halokaryamedia.lazybuilder.builder.axiom;

/** Safety contract for the end-to-end LazyBuilder Axiom spline tool. */
public final class AxiomSplineToolContract {
    public static final String TOOL_NAME = "LazyBuilder Spline";
    public static final boolean WORLD_MUTATION_ENABLED = true;
    public static final int MAX_MUTATION_VOXELS = SplinePreviewVoxelizer.MAX_MUTATION_VOXELS;
    private AxiomSplineToolContract() { }
}

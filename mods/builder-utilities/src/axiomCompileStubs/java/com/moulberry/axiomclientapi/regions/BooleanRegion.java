package com.moulberry.axiomclientapi.regions;

import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Compile-only subset of the MIT-licensed AxiomClientAPI BooleanRegion contract. */
public interface BooleanRegion {
    void render(Camera camera, Vec3d translation, MatrixStack matrices, Matrix4f projection, long time, int effects);
    boolean add(int x, int y, int z);
    void close();
    void clear();
}

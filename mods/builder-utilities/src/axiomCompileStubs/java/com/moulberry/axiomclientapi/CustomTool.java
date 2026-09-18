package com.moulberry.axiomclientapi;

import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/** Compile-only subset of the MIT-licensed AxiomClientAPI CustomTool contract. */
public interface CustomTool {
    default void reset() {}
    default void render(Camera camera, float tickDelta, long time, MatrixStack poseStack, Matrix4f projection) {}
    default boolean callUseTool() { return false; }
    default boolean callConfirm() { return false; }
    default boolean callDelete() { return false; }
    default void displayImguiOptions() {}
    String name();
}

package com.halokaryamedia.lazybuilder.utility.terraform;

import com.halokaryamedia.lazybuilder.terraform.CliffShape;
import com.halokaryamedia.lazybuilder.terraform.CliffSpec;
import com.halokaryamedia.lazybuilder.terraform.ShapeBounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only diagnostic preview for the first LazyBuilder terrain shape.
 *
 * <p>The preview deliberately has no placement/networking path. Hold a debug
 * stick and aim at a block to inspect the exact shared {@link CliffShape}
 * geometry that a future Paper executor will consume.</p>
 */
public final class CliffPreviewController {
    private static final double LENGTH = 32.0;
    private static final double HEIGHT = 24.0;
    private static final double WIDTH = 18.0;
    private static final long SEED = 0x4C415A59434C4946L;
    private static final int DIRECTION_STEPS = 16;

    private static PreviewKey lastKey;
    private static List<BlockPos> surfaceBlocks = List.of();

    private CliffPreviewController() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CliffPreviewController::update);
        WorldRenderEvents.AFTER_ENTITIES.register(CliffPreviewController::render);
    }

    private static void update(MinecraftClient client) {
        if (!canPreview(client)) {
            clear();
            return;
        }

        HitResult target = client.crosshairTarget;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            clear();
            return;
        }

        BlockPos anchor = blockHit.getBlockPos().offset(blockHit.getSide());
        double yawRadians = Math.toRadians(client.player.getYaw());
        int directionStep = Math.floorMod(
                (int) Math.round((yawRadians / (Math.PI * 2.0)) * DIRECTION_STEPS),
                DIRECTION_STEPS
        );
        PreviewKey key = new PreviewKey(anchor, directionStep);
        if (key.equals(lastKey)) {
            return;
        }

        lastKey = key;
        double angle = directionStep * (Math.PI * 2.0 / DIRECTION_STEPS);
        double dirX = -Math.sin(angle);
        double dirZ = Math.cos(angle);

        CliffSpec spec = new CliffSpec(
                anchor.getX() + 0.5,
                anchor.getY(),
                anchor.getZ() + 0.5,
                dirX,
                dirZ,
                LENGTH,
                HEIGHT,
                WIDTH,
                SEED
        );
        surfaceBlocks = sampleSurface(spec.createField());
    }

    private static boolean canPreview(MinecraftClient client) {
        return client.player != null
                && client.world != null
                && client.currentScreen == null
                && client.player.getMainHandStack().isOf(Items.DEBUG_STICK);
    }

    private static List<BlockPos> sampleSurface(CliffShape shape) {
        ShapeBounds bounds = shape.bounds();
        int minX = (int) Math.floor(bounds.minX());
        int minY = (int) Math.floor(bounds.minY());
        int minZ = (int) Math.floor(bounds.minZ());
        int maxX = (int) Math.ceil(bounds.maxX());
        int maxY = (int) Math.ceil(bounds.maxY());
        int maxZ = (int) Math.ceil(bounds.maxZ());

        Set<Long> solid = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (shape.sample(x + 0.5, y + 0.5, z + 0.5) <= 0.0) {
                        solid.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }

        List<BlockPos> surface = new ArrayList<>();
        for (long packed : solid) {
            BlockPos pos = BlockPos.fromLong(packed);
            if (isSurface(pos, solid)) {
                surface.add(pos);
            }
        }
        return List.copyOf(surface);
    }

    private static boolean isSurface(BlockPos pos, Set<Long> solid) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            if (!solid.contains(neighbor.asLong())) {
                return true;
            }
        }
        return false;
    }

    private static void render(WorldRenderContext context) {
        if (surfaceBlocks.isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }

        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        for (BlockPos pos : surfaceBlocks) {
            WorldRenderer.drawBox(
                    matrices,
                    lines,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                    0.25f, 0.85f, 1.0f, 0.55f
            );
        }
        matrices.pop();
    }

    private static void clear() {
        lastKey = null;
        surfaceBlocks = List.of();
    }

    private record PreviewKey(BlockPos anchor, int directionStep) {
    }
}

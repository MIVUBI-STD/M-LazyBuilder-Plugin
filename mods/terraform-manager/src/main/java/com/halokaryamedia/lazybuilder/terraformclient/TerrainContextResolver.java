package com.halokaryamedia.lazybuilder.terraformclient;

import com.halokaryamedia.lazybuilder.terraform.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Bounded client-side terrain context sampling for automatic tool orientation.
 * Uses only a small neighborhood around the cursor; it is never world-global state.
 */
final class TerrainContextResolver {
    private static final int SAMPLE_RADIUS = 3;
    private static final int VERTICAL_SEARCH = 8;
    private static final double SLOPE_THRESHOLD = 0.20;

    private TerrainContextResolver() {}

    static Vec3d resolveFront(MinecraftClient client, boolean flipped) {
        if (client == null || client.player == null) return new Vec3d(0, 0, 1);

        Vec3d resolved = null;
        if (client.crosshairTarget instanceof BlockHitResult hit) {
            Direction side = hit.getSide();
            if (side.getAxis().isHorizontal()) {
                resolved = new Vec3d(side.getOffsetX(), 0, side.getOffsetZ());
            } else if (client.world != null) {
                resolved = smoothedDownhill(client, hit.getBlockPos());
            }
        }

        if (resolved == null) {
            double yaw = Math.toRadians(client.player.getYaw());
            resolved = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)).horizontalNormalized();
        }
        return flipped ? resolved.multiply(-1.0) : resolved;
    }

    private static Vec3d smoothedDownhill(MinecraftClient client, BlockPos anchor) {
        double east = averageHeight(client, anchor, 1, 0);
        double west = averageHeight(client, anchor, -1, 0);
        double south = averageHeight(client, anchor, 0, 1);
        double north = averageHeight(client, anchor, 0, -1);
        if (!Double.isFinite(east + west + south + north)) return null;

        double dx = (west - east) * 0.5;
        double dz = (north - south) * 0.5;
        double magnitude = Math.hypot(dx, dz);
        if (magnitude < SLOPE_THRESHOLD) return null;
        return new Vec3d(dx, 0.0, dz).horizontalNormalized();
    }

    private static double averageHeight(MinecraftClient client, BlockPos anchor, int axisX, int axisZ) {
        double sum = 0.0;
        int count = 0;
        for (int step = 1; step <= SAMPLE_RADIUS; step++) {
            int x = anchor.getX() + axisX * step;
            int z = anchor.getZ() + axisZ * step;
            Integer y = surfaceY(client, x, z, anchor.getY());
            if (y != null) {
                sum += y;
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static Integer surfaceY(MinecraftClient client, int x, int z, int centerY) {
        int top = centerY + VERTICAL_SEARCH;
        int bottom = centerY - VERTICAL_SEARCH;
        for (int y = top; y >= bottom; y--) {
            if (!client.world.getBlockState(new BlockPos(x, y, z)).isAir()) return y + 1;
        }
        return null;
    }
}

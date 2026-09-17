package com.halokaryamedia.lazybuilder.performance.culling;

import com.halokaryamedia.lazybuilder.performance.PerformancePreferences;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Conservative main-thread occlusion culling.
 *
 * Render hooks never raycast. Unknown or stale state renders immediately and is queued for a
 * bounded end-of-tick evaluation. This makes overload fail open instead of hiding builder content.
 */
public final class CullingRuntime {
    private static final int ENTITY_BUDGET_PER_TICK = 12;
    private static final int BLOCK_ENTITY_BUDGET_PER_TICK = 12;
    private static final long MAX_AGE_NANOS = 250_000_000L;
    private static final double MAX_CAMERA_MOVE_SQ = 0.75D * 0.75D;
    private static final double MAX_TARGET_MOVE_SQ = 0.5D * 0.5D;
    private static final double ALWAYS_VISIBLE_DISTANCE_SQ = 4.0D * 4.0D;

    private final Map<Entity, CacheEntry> entities = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<BlockEntity, CacheEntry> blockEntities = Collections.synchronizedMap(new WeakHashMap<>());
    private final Queue<Entity> entityQueue = new ArrayDeque<>();
    private final Queue<BlockEntity> blockEntityQueue = new ArrayDeque<>();
    private final Set<Entity> queuedEntities = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<BlockEntity> queuedBlockEntities = Collections.newSetFromMap(new IdentityHashMap<>());
    private World lastWorld;

    public boolean shouldRender(Entity entity, PerformancePreferences preferences) {
        if (!preferences.entityCulling()) return true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!eligible(client, entity)) return true;

        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Vec3d target = entity.getBoundingBox().getCenter();
        CacheEntry entry = entities.get(entity);
        if (!fresh(entry, camera, target, System.nanoTime())) {
            enqueue(entity);
            return true;
        }
        return entry.decision != VisibilityDecision.OCCLUDED;
    }

    public <E extends BlockEntity> boolean shouldRender(
            E blockEntity,
            BlockEntityRenderer<E> renderer,
            PerformancePreferences preferences
    ) {
        if (!preferences.blockEntityCulling() || renderer == null || renderer.rendersOutsideBoundingBox(blockEntity)) {
            return true;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || blockEntity.getWorld() != client.world) return true;

        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Vec3d target = Vec3d.ofCenter(blockEntity.getPos());
        if (camera.squaredDistanceTo(target) <= ALWAYS_VISIBLE_DISTANCE_SQ) return true;

        CacheEntry entry = blockEntities.get(blockEntity);
        if (!fresh(entry, camera, target, System.nanoTime())) {
            enqueue(blockEntity);
            return true;
        }
        return entry.decision != VisibilityDecision.OCCLUDED;
    }

    public void tick(MinecraftClient client, PerformancePreferences preferences) {
        if (client == null || client.world == null) {
            clear();
            return;
        }
        if (lastWorld != client.world) {
            clear();
            lastWorld = client.world;
        }
        if (!preferences.entityCulling() && !preferences.blockEntityCulling()) return;

        for (int i = 0; i < ENTITY_BUDGET_PER_TICK; i++) {
            Entity entity = entityQueue.poll();
            if (entity == null) break;
            queuedEntities.remove(entity);
            if (!preferences.entityCulling() || !eligible(client, entity)) continue;
            evaluateEntity(client, entity);
        }
        for (int i = 0; i < BLOCK_ENTITY_BUDGET_PER_TICK; i++) {
            BlockEntity blockEntity = blockEntityQueue.poll();
            if (blockEntity == null) break;
            queuedBlockEntities.remove(blockEntity);
            if (!preferences.blockEntityCulling() || blockEntity.getWorld() != client.world) continue;
            evaluateBlockEntity(client, blockEntity);
        }
    }

    public void clear() {
        entities.clear();
        blockEntities.clear();
        entityQueue.clear();
        blockEntityQueue.clear();
        queuedEntities.clear();
        queuedBlockEntities.clear();
        lastWorld = null;
    }

    private static boolean eligible(MinecraftClient client, Entity entity) {
        if (client == null || client.world == null || entity == null || entity.getWorld() != client.world) return false;
        if (entity == client.player || entity == client.gameRenderer.getCamera().getFocusedEntity()) return false;
        if (entity.isGlowing() || entity.hasCustomName()) return false;
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        return camera.squaredDistanceTo(entity.getBoundingBox().getCenter()) > ALWAYS_VISIBLE_DISTANCE_SQ;
    }

    private void evaluateEntity(MinecraftClient client, Entity entity) {
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Box box = entity.getBoundingBox();
        Vec3d center = box.getCenter();
        double inset = Math.min(0.2D, Math.max(0.05D, (box.maxY - box.minY) * 0.2D));
        Vec3d upper = new Vec3d(center.x, box.maxY - inset, center.z);
        Vec3d lower = new Vec3d(center.x, box.minY + inset, center.z);
        boolean visible = rayVisible(client, camera, center, null)
                || rayVisible(client, camera, upper, null)
                || rayVisible(client, camera, lower, null);
        entities.put(entity, new CacheEntry(
                visible ? VisibilityDecision.VISIBLE : VisibilityDecision.OCCLUDED,
                System.nanoTime(),
                camera,
                center
        ));
    }

    private void evaluateBlockEntity(MinecraftClient client, BlockEntity blockEntity) {
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Vec3d center = Vec3d.ofCenter(blockEntity.getPos());
        boolean visible = rayVisible(client, camera, center, blockEntity);
        blockEntities.put(blockEntity, new CacheEntry(
                visible ? VisibilityDecision.VISIBLE : VisibilityDecision.OCCLUDED,
                System.nanoTime(),
                camera,
                center
        ));
    }

    private static boolean rayVisible(MinecraftClient client, Vec3d camera, Vec3d target, BlockEntity targetBlockEntity) {
        Entity context = client.gameRenderer.getCamera().getFocusedEntity();
        if (context == null) context = client.player;
        if (context == null) return true;

        BlockHitResult hit = client.world.raycast(new RaycastContext(
                camera,
                target,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                context
        ));
        if (hit.getType() == HitResult.Type.MISS) return true;
        if (targetBlockEntity != null && hit.getBlockPos().equals(targetBlockEntity.getPos())) return true;
        return camera.squaredDistanceTo(hit.getPos()) + 0.01D >= camera.squaredDistanceTo(target);
    }

    private static boolean fresh(CacheEntry entry, Vec3d camera, Vec3d target, long now) {
        return entry != null
                && now - entry.createdNanos <= MAX_AGE_NANOS
                && entry.camera.squaredDistanceTo(camera) <= MAX_CAMERA_MOVE_SQ
                && entry.target.squaredDistanceTo(target) <= MAX_TARGET_MOVE_SQ;
    }

    private void enqueue(Entity entity) {
        if (queuedEntities.add(entity)) entityQueue.add(entity);
    }

    private void enqueue(BlockEntity blockEntity) {
        if (queuedBlockEntities.add(blockEntity)) blockEntityQueue.add(blockEntity);
    }

    private record CacheEntry(
            VisibilityDecision decision,
            long createdNanos,
            Vec3d camera,
            Vec3d target
    ) {}
}

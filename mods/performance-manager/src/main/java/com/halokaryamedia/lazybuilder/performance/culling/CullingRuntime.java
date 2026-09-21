package com.halokaryamedia.lazybuilder.performance.culling;

import com.halokaryamedia.lazybuilder.performance.FramePressure;
import com.halokaryamedia.lazybuilder.performance.PerformancePreferences;
import com.halokaryamedia.lazybuilder.performance.StageTimingMetrics;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
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
 * Modded entity and block-entity types are left to their owning renderer and are never culled here.
 */
public final class CullingRuntime {
    private static final int NORMAL_ENTITY_BUDGET = 8;
    private static final int ELEVATED_ENTITY_BUDGET = 4;
    private static final int HEAVY_ENTITY_BUDGET = 2;
    private static final int NORMAL_BLOCK_ENTITY_BUDGET = 4;
    private static final int ELEVATED_BLOCK_ENTITY_BUDGET = 2;
    private static final int HEAVY_BLOCK_ENTITY_BUDGET = 1;
    private static final int MAX_ENTITY_QUEUE = 64;
    private static final int MAX_BLOCK_ENTITY_QUEUE = 32;
    private static final int MAX_TRANSPARENT_PASSES = 8;
    private static final long MAX_AGE_NANOS = 250_000_000L;
    private static final double MAX_CAMERA_MOVE_SQ = 0.75D * 0.75D;
    private static final double MAX_TARGET_MOVE_SQ = 0.5D * 0.5D;
    private static final double ALWAYS_VISIBLE_DISTANCE_SQ = 4.0D * 4.0D;
    private static final double RAY_ADVANCE = 0.05D;
    private static final Map<EntityType<?>, Boolean> VANILLA_ENTITY_TYPES = new IdentityHashMap<>();
    private static final Map<BlockEntityType<?>, Boolean> VANILLA_BLOCK_ENTITY_TYPES = new IdentityHashMap<>();

    // All access is from Minecraft's client/render thread; weak keys avoid retaining removed world objects.
    private final Map<Entity, CacheEntry> entities = new WeakHashMap<>();
    private final Map<BlockEntity, CacheEntry> blockEntities = new WeakHashMap<>();
    private final Queue<Entity> entityQueue = new ArrayDeque<>();
    private final Queue<BlockEntity> blockEntityQueue = new ArrayDeque<>();
    private final Set<Entity> queuedEntities = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<BlockEntity> queuedBlockEntities = Collections.newSetFromMap(new IdentityHashMap<>());
    private World lastWorld;
    private long entityCacheHits;
    private long entityCacheStales;
    private long blockEntityCacheHits;
    private long blockEntityCacheStales;
    private long entityEvaluations;
    private long blockEntityEvaluations;
    private long entityQueueDrops;
    private long blockEntityQueueDrops;

    public boolean shouldRender(
            Entity entity,
            PerformancePreferences preferences,
            long frameNowNanos
    ) {
        if (!preferences.entityCulling()) return true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!eligibleWithoutDistance(client, entity)) return true;

        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Box box = entity.getBoundingBox();
        double targetX = (box.minX + box.maxX) * 0.5D;
        double targetY = (box.minY + box.maxY) * 0.5D;
        double targetZ = (box.minZ + box.maxZ) * 0.5D;
        if (squaredDistance(camera, targetX, targetY, targetZ) <= ALWAYS_VISIBLE_DISTANCE_SQ) return true;

        CacheEntry entry = entities.get(entity);
        long now = frameNowNanos > 0L ? frameNowNanos : System.nanoTime();
        if (!fresh(entry, camera, targetX, targetY, targetZ, now)) {
            entityCacheStales++;
            enqueue(entity);
            return true;
        }
        entityCacheHits++;
        return entry.decision != VisibilityDecision.OCCLUDED;
    }

    public <E extends BlockEntity> boolean shouldRender(
            E blockEntity,
            BlockEntityRenderer<E> renderer,
            PerformancePreferences preferences,
            long frameNowNanos
    ) {
        if (!preferences.blockEntityCulling()
                || renderer == null
                || !isVanillaBlockEntity(blockEntity)
                || renderer.rendersOutsideBoundingBox(blockEntity)) {
            return true;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || blockEntity.getWorld() != client.world) return true;

        Vec3d camera = client.gameRenderer.getCamera().getPos();
        double targetX = blockEntity.getPos().getX() + 0.5D;
        double targetY = blockEntity.getPos().getY() + 0.5D;
        double targetZ = blockEntity.getPos().getZ() + 0.5D;
        if (squaredDistance(camera, targetX, targetY, targetZ) <= ALWAYS_VISIBLE_DISTANCE_SQ) return true;

        CacheEntry entry = blockEntities.get(blockEntity);
        long now = frameNowNanos > 0L ? frameNowNanos : System.nanoTime();
        if (!fresh(entry, camera, targetX, targetY, targetZ, now)) {
            blockEntityCacheStales++;
            enqueue(blockEntity);
            return true;
        }
        blockEntityCacheHits++;
        return entry.decision != VisibilityDecision.OCCLUDED;
    }

    public void tick(
            MinecraftClient client,
            PerformancePreferences preferences,
            FramePressure pressure
    ) {
        if (client == null || client.world == null) {
            clear();
            return;
        }
        if (lastWorld != client.world) {
            clear();
            lastWorld = client.world;
        }
        if (!preferences.entityCulling() && !preferences.blockEntityCulling()) return;

        int entityBudget = entityBudget(pressure);
        int blockEntityBudget = blockEntityBudget(pressure);

        for (int i = 0; i < entityBudget; i++) {
            Entity entity = entityQueue.poll();
            if (entity == null) break;
            queuedEntities.remove(entity);
            if (!preferences.entityCulling() || !eligible(client, entity)) continue;
            evaluateEntityTimed(client, entity);
        }
        for (int i = 0; i < blockEntityBudget; i++) {
            BlockEntity blockEntity = blockEntityQueue.poll();
            if (blockEntity == null) break;
            queuedBlockEntities.remove(blockEntity);
            if (!preferences.blockEntityCulling()
                    || blockEntity.getWorld() != client.world
                    || !isVanillaBlockEntity(blockEntity)) {
                continue;
            }
            evaluateBlockEntityTimed(client, blockEntity);
        }
    }

    private static int entityBudget(FramePressure pressure) {
        if (pressure == FramePressure.HEAVY) return HEAVY_ENTITY_BUDGET;
        if (pressure == FramePressure.ELEVATED) return ELEVATED_ENTITY_BUDGET;
        return NORMAL_ENTITY_BUDGET;
    }

    private static int blockEntityBudget(FramePressure pressure) {
        if (pressure == FramePressure.HEAVY) return HEAVY_BLOCK_ENTITY_BUDGET;
        if (pressure == FramePressure.ELEVATED) return ELEVATED_BLOCK_ENTITY_BUDGET;
        return NORMAL_BLOCK_ENTITY_BUDGET;
    }

    public Snapshot snapshot() {
        return new Snapshot(
                entities.size(),
                blockEntities.size(),
                entityQueue.size(),
                blockEntityQueue.size(),
                entityCacheHits,
                entityCacheStales,
                blockEntityCacheHits,
                blockEntityCacheStales,
                entityEvaluations,
                blockEntityEvaluations,
                entityQueueDrops,
                blockEntityQueueDrops
        );
    }

    public void clear() {
        entities.clear();
        blockEntities.clear();
        entityQueue.clear();
        blockEntityQueue.clear();
        queuedEntities.clear();
        queuedBlockEntities.clear();
        lastWorld = null;
        entityCacheHits = 0L;
        entityCacheStales = 0L;
        blockEntityCacheHits = 0L;
        blockEntityCacheStales = 0L;
        entityEvaluations = 0L;
        blockEntityEvaluations = 0L;
        entityQueueDrops = 0L;
        blockEntityQueueDrops = 0L;
    }

    private static boolean eligible(MinecraftClient client, Entity entity) {
        if (!eligibleWithoutDistance(client, entity)) return false;
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Box box = entity.getBoundingBox();
        double targetX = (box.minX + box.maxX) * 0.5D;
        double targetY = (box.minY + box.maxY) * 0.5D;
        double targetZ = (box.minZ + box.maxZ) * 0.5D;
        return squaredDistance(camera, targetX, targetY, targetZ) > ALWAYS_VISIBLE_DISTANCE_SQ;
    }

    private static boolean eligibleWithoutDistance(MinecraftClient client, Entity entity) {
        if (client == null || client.world == null || entity == null || entity.getWorld() != client.world) return false;
        if (!isVanillaEntity(entity)) return false;
        if (entity == client.player || entity == client.gameRenderer.getCamera().getFocusedEntity()) return false;
        return !entity.isGlowing() && !entity.hasCustomName();
    }

    private static boolean isVanillaEntity(Entity entity) {
        EntityType<?> type = entity.getType();
        Boolean cached = VANILLA_ENTITY_TYPES.get(type);
        if (cached != null) return cached;

        boolean vanilla = Registries.ENTITY_TYPE.getId(type).getNamespace().equals("minecraft");
        VANILLA_ENTITY_TYPES.put(type, vanilla);
        return vanilla;
    }

    private static boolean isVanillaBlockEntity(BlockEntity blockEntity) {
        BlockEntityType<?> type = blockEntity.getType();
        Boolean cached = VANILLA_BLOCK_ENTITY_TYPES.get(type);
        if (cached != null) return cached;

        boolean vanilla = Registries.BLOCK_ENTITY_TYPE.getId(type).getNamespace().equals("minecraft");
        VANILLA_BLOCK_ENTITY_TYPES.put(type, vanilla);
        return vanilla;
    }

    private void evaluateEntityTimed(MinecraftClient client, Entity entity) {
        if (!StageTimingMetrics.enabled()) {
            evaluateEntity(client, entity);
            return;
        }
        long started = System.nanoTime();
        try {
            evaluateEntity(client, entity);
        } finally {
            StageTimingMetrics.record(
                    StageTimingMetrics.Stage.ENTITY_CULLING,
                    System.nanoTime() - started
            );
        }
    }

    private void evaluateBlockEntityTimed(MinecraftClient client, BlockEntity blockEntity) {
        if (!StageTimingMetrics.enabled()) {
            evaluateBlockEntity(client, blockEntity);
            return;
        }
        long started = System.nanoTime();
        try {
            evaluateBlockEntity(client, blockEntity);
        } finally {
            StageTimingMetrics.record(
                    StageTimingMetrics.Stage.BLOCK_ENTITY_CULLING,
                    System.nanoTime() - started
            );
        }
    }

    private void evaluateEntity(MinecraftClient client, Entity entity) {
        entityEvaluations++;
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Box box = entity.getBoundingBox();
        Vec3d center = box.getCenter();
        double xInset = Math.min(0.2D, Math.max(0.03D, (box.maxX - box.minX) * 0.2D));
        double yInset = Math.min(0.2D, Math.max(0.05D, (box.maxY - box.minY) * 0.2D));
        double zInset = Math.min(0.2D, Math.max(0.03D, (box.maxZ - box.minZ) * 0.2D));
        boolean visible = rayVisible(client, camera, center, null)
                || rayVisible(client, camera, new Vec3d(center.x, box.maxY - yInset, center.z), null)
                || rayVisible(client, camera, new Vec3d(center.x, box.minY + yInset, center.z), null)
                || rayVisible(client, camera, new Vec3d(box.minX + xInset, center.y, center.z), null)
                || rayVisible(client, camera, new Vec3d(box.maxX - xInset, center.y, center.z), null)
                || rayVisible(client, camera, new Vec3d(center.x, center.y, box.minZ + zInset), null)
                || rayVisible(client, camera, new Vec3d(center.x, center.y, box.maxZ - zInset), null);
        entities.put(entity, new CacheEntry(
                visible ? VisibilityDecision.VISIBLE : VisibilityDecision.OCCLUDED,
                System.nanoTime(),
                camera.x,
                camera.y,
                camera.z,
                center.x,
                center.y,
                center.z
        ));
    }

    private void evaluateBlockEntity(MinecraftClient client, BlockEntity blockEntity) {
        blockEntityEvaluations++;
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Vec3d center = Vec3d.ofCenter(blockEntity.getPos());
        double sample = 0.42D;
        boolean visible = rayVisible(client, camera, center, blockEntity)
                || rayVisible(client, camera, center.add(sample, 0.0D, 0.0D), blockEntity)
                || rayVisible(client, camera, center.add(-sample, 0.0D, 0.0D), blockEntity)
                || rayVisible(client, camera, center.add(0.0D, sample, 0.0D), blockEntity)
                || rayVisible(client, camera, center.add(0.0D, -sample, 0.0D), blockEntity)
                || rayVisible(client, camera, center.add(0.0D, 0.0D, sample), blockEntity)
                || rayVisible(client, camera, center.add(0.0D, 0.0D, -sample), blockEntity);
        blockEntities.put(blockEntity, new CacheEntry(
                visible ? VisibilityDecision.VISIBLE : VisibilityDecision.OCCLUDED,
                System.nanoTime(),
                camera.x,
                camera.y,
                camera.z,
                center.x,
                center.y,
                center.z
        ));
    }

    /**
     * Tests line of sight using only opaque full cubes as definite occluders.
     * Glass, slabs, foliage, fences, and other partial/transparent collision shapes are stepped through.
     * Hitting too many such shapes fails open and renders the target.
     */
    private static boolean rayVisible(MinecraftClient client, Vec3d camera, Vec3d target, BlockEntity targetBlockEntity) {
        Entity context = client.gameRenderer.getCamera().getFocusedEntity();
        if (context == null) context = client.player;
        if (context == null || client.world == null) return true;

        double dx = target.x - camera.x;
        double dy = target.y - camera.y;
        double dz = target.z - camera.z;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared <= 1.0E-6D) return true;
        double inverseLength = 1.0D / Math.sqrt(lengthSquared);
        double directionX = dx * inverseLength;
        double directionY = dy * inverseLength;
        double directionZ = dz * inverseLength;
        Vec3d start = camera;

        for (int pass = 0; pass < MAX_TRANSPARENT_PASSES; pass++) {
            BlockHitResult hit = client.world.raycast(new RaycastContext(
                    start,
                    target,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    context
            ));
            if (hit.getType() == HitResult.Type.MISS) return true;
            if (targetBlockEntity != null && hit.getBlockPos().equals(targetBlockEntity.getPos())) return true;
            if (camera.squaredDistanceTo(hit.getPos()) + 0.01D >= camera.squaredDistanceTo(target)) return true;

            BlockState hitState = client.world.getBlockState(hit.getBlockPos());
            if (hitState.isOpaqueFullCube()) return false;

            Vec3d hitPos = hit.getPos();
            Vec3d nextStart = new Vec3d(
                    hitPos.x + directionX * RAY_ADVANCE,
                    hitPos.y + directionY * RAY_ADVANCE,
                    hitPos.z + directionZ * RAY_ADVANCE
            );
            if (nextStart.squaredDistanceTo(target) >= start.squaredDistanceTo(target)) return true;
            start = nextStart;
        }

        return true;
    }

    private static boolean fresh(
            CacheEntry entry,
            Vec3d camera,
            double targetX,
            double targetY,
            double targetZ,
            long now
    ) {
        return entry != null
                && now - entry.createdNanos <= MAX_AGE_NANOS
                && squaredDistance(
                        entry.cameraX,
                        entry.cameraY,
                        entry.cameraZ,
                        camera.x,
                        camera.y,
                        camera.z
                ) <= MAX_CAMERA_MOVE_SQ
                && squaredDistance(
                        entry.targetX,
                        entry.targetY,
                        entry.targetZ,
                        targetX,
                        targetY,
                        targetZ
                ) <= MAX_TARGET_MOVE_SQ;
    }

    private static double squaredDistance(Vec3d origin, double x, double y, double z) {
        return squaredDistance(origin.x, origin.y, origin.z, x, y, z);
    }

    private static double squaredDistance(
            double ax,
            double ay,
            double az,
            double bx,
            double by,
            double bz
    ) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private void enqueue(Entity entity) {
        if (queuedEntities.contains(entity)) return;
        if (entityQueue.size() >= MAX_ENTITY_QUEUE) {
            entityQueueDrops++;
            return;
        }
        queuedEntities.add(entity);
        entityQueue.add(entity);
    }

    private void enqueue(BlockEntity blockEntity) {
        if (queuedBlockEntities.contains(blockEntity)) return;
        if (blockEntityQueue.size() >= MAX_BLOCK_ENTITY_QUEUE) {
            blockEntityQueueDrops++;
            return;
        }
        queuedBlockEntities.add(blockEntity);
        blockEntityQueue.add(blockEntity);
    }

    public record Snapshot(
            int trackedEntities,
            int trackedBlockEntities,
            int queuedEntities,
            int queuedBlockEntities,
            long entityCacheHits,
            long entityCacheStales,
            long blockEntityCacheHits,
            long blockEntityCacheStales,
            long entityEvaluations,
            long blockEntityEvaluations,
            long entityQueueDrops,
            long blockEntityQueueDrops
    ) {}

    private record CacheEntry(
            VisibilityDecision decision,
            long createdNanos,
            double cameraX,
            double cameraY,
            double cameraZ,
            double targetX,
            double targetY,
            double targetZ
    ) {}
}

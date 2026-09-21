package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainArenaDrawDiagnostics;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainArenaDrawPlanner;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainDrawTransformStream;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainGpuResidencyTracker;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainMultiDrawCommandStream;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainMultiDrawSubmissionBackend;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainPerDrawShaderBackend;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainPhysicalArenaManager;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainSubmissionPolicy;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainVisibleDrawSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


/** Builds indexed terrain submission and guarded physical/multi-draw execution paths. */
@Mixin(WorldRenderer.class)
abstract class WorldRendererTerrainSubmissionMixin {
    @Shadow @Final private ObjectArrayList<ChunkBuilder.BuiltChunk> builtChunks;

    @Unique private ObjectArrayList<ChunkBuilder.BuiltChunk> lazybuilder$solid;
    @Unique private ObjectArrayList<ChunkBuilder.BuiltChunk> lazybuilder$cutoutMipped;
    @Unique private ObjectArrayList<ChunkBuilder.BuiltChunk> lazybuilder$cutout;
    @Unique private ObjectArrayList<ChunkBuilder.BuiltChunk> lazybuilder$translucent;
    @Unique private ObjectArrayList<ChunkBuilder.BuiltChunk> lazybuilder$tripwire;

    @Unique private boolean lazybuilder$submissionIndexDirty = true;
    @Unique private boolean lazybuilder$submissionIndexActive;
    @Unique private int lazybuilder$cachedVisibleCount = -1;
    @Unique private RenderLayer lazybuilder$currentLayer;
    @Unique private VertexBuffer lazybuilder$physicalPreparedBuffer;
    @Unique private VertexBuffer lazybuilder$blockedVanillaFallbackBuffer;
    @Unique private TerrainDrawTransformStream.Builder[] lazybuilder$transformBuilders;
    @Unique private long lazybuilder$visibleSnapshotRevision;

    @Inject(method = "applyFrustum", at = @At("TAIL"))
    private void lazybuilder$invalidateAfterFrustum(Frustum frustum, CallbackInfo ci) {
        this.lazybuilder$submissionIndexDirty = true;
    }

    @Inject(method = "addBuiltChunk", at = @At("HEAD"))
    private void lazybuilder$invalidateAfterChunkData(ChunkBuilder.BuiltChunk chunk, CallbackInfo ci) {
        this.lazybuilder$submissionIndexDirty = true;
    }

    @Inject(method = "renderLayer", at = @At("HEAD"))
    private void lazybuilder$prepareTerrainSubmissionIndex(
            RenderLayer layer,
            double x,
            double y,
            double z,
            Matrix4f matrix,
            Matrix4f positionMatrix,
            CallbackInfo ci
    ) {
        this.lazybuilder$ensureSubmissionState();
        this.lazybuilder$currentLayer = layer;
        this.lazybuilder$physicalPreparedBuffer = null;
        this.lazybuilder$blockedVanillaFallbackBuffer = null;

        if (this.lazybuilder$cachedVisibleCount != this.builtChunks.size()) {
            this.lazybuilder$submissionIndexDirty = true;
        }

        if (this.lazybuilder$submissionIndexDirty) {
            this.lazybuilder$rebuildSubmissionIndex();
        }

        if (!PerformanceManagerClient.preferences().renderingOptimizations()) {
            this.lazybuilder$submissionIndexActive = false;
            TerrainArenaDrawDiagnostics.clear();
            TerrainDrawTransformStream.clear();
            TerrainMultiDrawSubmissionBackend.clear();
            TerrainPerDrawShaderBackend.clear();
            TerrainPhysicalArenaManager.noteExternalBind();
            return;
        }
        if (!lazybuilder$isBlockLayer(layer)) {
            this.lazybuilder$submissionIndexActive = false;
            TerrainMultiDrawSubmissionBackend.finishLayer();
            TerrainPhysicalArenaManager.noteExternalBind();
            return;
        }

        this.lazybuilder$publishTransformStream(layer, x, y, z);
    }

    @Inject(
            method = "renderLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gl/ShaderProgram;bind()V",
                    shift = At.Shift.AFTER
            )
    )
    private void lazybuilder$preparePerDrawShaderData(
            RenderLayer layer,
            double x,
            double y,
            double z,
            Matrix4f matrix,
            Matrix4f positionMatrix,
            CallbackInfo ci
    ) {
        int layerSlot = lazybuilder$layerSlot(layer);
        if (layerSlot < 0 || !PerformanceManagerClient.preferences().renderingOptimizations()) return;
        ShaderProgram shader = RenderSystem.getShader();
        TerrainMultiDrawSubmissionBackend.prepare(shader, TerrainMultiDrawCommandStream.layer(layerSlot));
    }

    @Inject(method = "renderLayer", at = @At("RETURN"))
    private void lazybuilder$finishTerrainArenaPass(
            RenderLayer layer,
            double x,
            double y,
            double z,
            Matrix4f matrix,
            Matrix4f positionMatrix,
            CallbackInfo ci
    ) {
        this.lazybuilder$physicalPreparedBuffer = null;
        this.lazybuilder$blockedVanillaFallbackBuffer = null;
        TerrainMultiDrawSubmissionBackend.finishLayer();
        TerrainPhysicalArenaManager.noteExternalBind();
    }

    @Redirect(
            method = "renderLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;listIterator(I)Lit/unimi/dsi/fastutil/objects/ObjectListIterator;",
                    remap = false
            )
    )
    private ObjectListIterator<ChunkBuilder.BuiltChunk> lazybuilder$indexedIterator(
            ObjectArrayList<ChunkBuilder.BuiltChunk> source,
            int index
    ) {
        if (!this.lazybuilder$submissionIndexActive || source != this.builtChunks) {
            return source.listIterator(index);
        }

        ObjectArrayList<ChunkBuilder.BuiltChunk> filtered = this.lazybuilder$listFor(this.lazybuilder$currentLayer);
        if (filtered == null) {
            return source.listIterator(index);
        }

        int avoidedVisits = source.size() - filtered.size();
        if (avoidedVisits > 0) {
            ChunkPipelineMetrics.recordAvoidedTerrainSectionVisits(avoidedVisits);
        }

        int mappedIndex = TerrainSubmissionPolicy.mappedIteratorIndex(index, source.size(), filtered.size());
        return filtered.listIterator(mappedIndex);
    }

    @Redirect(
            method = "renderLayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/VertexBuffer;bind()V")
    )
    private void lazybuilder$bindPhysicalArenaOrVanilla(VertexBuffer buffer) {
        if (PerformanceManagerClient.preferences().renderingOptimizations()) {
            TerrainMultiDrawSubmissionBackend.BindAction multi = TerrainMultiDrawSubmissionBackend.onBind(buffer);
            if (multi == TerrainMultiDrawSubmissionBackend.BindAction.START
                    || multi == TerrainMultiDrawSubmissionBackend.BindAction.SKIP) {
                this.lazybuilder$physicalPreparedBuffer = null;
                return;
            }
            if (TerrainPhysicalArenaManager.bind(buffer)) {
                this.lazybuilder$physicalPreparedBuffer = buffer;
                return;
            }
        }

        this.lazybuilder$physicalPreparedBuffer = null;
        TerrainPhysicalArenaManager.noteExternalBind();
        if (!TerrainPhysicalArenaManager.recoverVanillaBacking(buffer)) {
            this.lazybuilder$blockedVanillaFallbackBuffer = buffer;
            return;
        }
        buffer.bind();
    }

    @Redirect(
            method = "renderLayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/VertexBuffer;draw()V")
    )
    private void lazybuilder$drawPhysicalArenaOrVanilla(VertexBuffer buffer) {
        if (this.lazybuilder$blockedVanillaFallbackBuffer == buffer) {
            this.lazybuilder$blockedVanillaFallbackBuffer = null;
            return;
        }

        TerrainMultiDrawSubmissionBackend.DrawAction multi = TerrainMultiDrawSubmissionBackend.onDraw(buffer);
        if (multi == TerrainMultiDrawSubmissionBackend.DrawAction.SUBMITTED
                || multi == TerrainMultiDrawSubmissionBackend.DrawAction.SKIP) {
            this.lazybuilder$physicalPreparedBuffer = null;
            return;
        }
        if (multi == TerrainMultiDrawSubmissionBackend.DrawAction.FALLBACK) {
            this.lazybuilder$physicalPreparedBuffer = null;
            if (TerrainPhysicalArenaManager.bind(buffer) && TerrainPhysicalArenaManager.draw(buffer)) {
                return;
            }
            TerrainPhysicalArenaManager.noteExternalBind();
            if (!TerrainPhysicalArenaManager.recoverVanillaBacking(buffer)) return;
            buffer.bind();
            buffer.draw();
            return;
        }

        boolean expectedPhysical = this.lazybuilder$physicalPreparedBuffer == buffer;
        this.lazybuilder$physicalPreparedBuffer = null;

        if (expectedPhysical && TerrainPhysicalArenaManager.draw(buffer)) {
            return;
        }

        if (expectedPhysical) {
            TerrainPhysicalArenaManager.noteExternalBind();
            if (!TerrainPhysicalArenaManager.recoverVanillaBacking(buffer)) return;
            buffer.bind();
        }
        buffer.draw();
    }

    @Unique
    private void lazybuilder$ensureSubmissionState() {
        if (this.lazybuilder$solid == null) {
            this.lazybuilder$solid = new ObjectArrayList<>();
            this.lazybuilder$cutoutMipped = new ObjectArrayList<>();
            this.lazybuilder$cutout = new ObjectArrayList<>();
            this.lazybuilder$translucent = new ObjectArrayList<>();
            this.lazybuilder$tripwire = new ObjectArrayList<>();
        }
        if (this.lazybuilder$transformBuilders == null) {
            this.lazybuilder$transformBuilders = lazybuilder$newTransformBuilders();
        }
    }

    @Unique
    private void lazybuilder$rebuildSubmissionIndex() {
        this.lazybuilder$ensureSubmissionState();
        this.lazybuilder$solid.clear();
        this.lazybuilder$cutoutMipped.clear();
        this.lazybuilder$cutout.clear();
        this.lazybuilder$translucent.clear();
        this.lazybuilder$tripwire.clear();

        RenderLayer solid = RenderLayer.getSolid();
        RenderLayer cutoutMipped = RenderLayer.getCutoutMipped();
        RenderLayer cutout = RenderLayer.getCutout();
        RenderLayer translucent = RenderLayer.getTranslucent();
        RenderLayer tripwire = RenderLayer.getTripwire();

        for (ChunkBuilder.BuiltChunk builtChunk : this.builtChunks) {
            ChunkBuilder.ChunkData data = builtChunk.getData();
            if (!data.isEmpty(solid)) this.lazybuilder$solid.add(builtChunk);
            if (!data.isEmpty(cutoutMipped)) this.lazybuilder$cutoutMipped.add(builtChunk);
            if (!data.isEmpty(cutout)) this.lazybuilder$cutout.add(builtChunk);
            if (!data.isEmpty(translucent)) this.lazybuilder$translucent.add(builtChunk);
            if (!data.isEmpty(tripwire)) this.lazybuilder$tripwire.add(builtChunk);
        }

        int layerEntries = this.lazybuilder$solid.size()
                + this.lazybuilder$cutoutMipped.size()
                + this.lazybuilder$cutout.size()
                + this.lazybuilder$translucent.size()
                + this.lazybuilder$tripwire.size();

        this.lazybuilder$cachedVisibleCount = this.builtChunks.size();
        this.lazybuilder$submissionIndexActive = TerrainSubmissionPolicy.shouldUseIndex(
                this.lazybuilder$cachedVisibleCount,
                layerEntries
        );
        this.lazybuilder$submissionIndexDirty = false;
        this.lazybuilder$publishVisibleDrawSnapshot();
        this.lazybuilder$publishArenaDrawPlan();
    }

    @Unique
    private void lazybuilder$publishVisibleDrawSnapshot() {
        int expected = this.lazybuilder$solid.size()
                + this.lazybuilder$cutoutMipped.size()
                + this.lazybuilder$cutout.size()
                + this.lazybuilder$translucent.size()
                + this.lazybuilder$tripwire.size();

        TerrainVisibleDrawSnapshot.Builder builder =
                TerrainVisibleDrawSnapshot.builder(expected)
                        .revision(++this.lazybuilder$visibleSnapshotRevision);

        this.lazybuilder$appendVisibleSnapshotLayer(builder, this.lazybuilder$solid, RenderLayer.getSolid(), 0);
        this.lazybuilder$appendVisibleSnapshotLayer(builder, this.lazybuilder$cutoutMipped, RenderLayer.getCutoutMipped(), 1);
        this.lazybuilder$appendVisibleSnapshotLayer(builder, this.lazybuilder$cutout, RenderLayer.getCutout(), 2);
        this.lazybuilder$appendVisibleSnapshotLayer(builder, this.lazybuilder$translucent, RenderLayer.getTranslucent(), 3);
        this.lazybuilder$appendVisibleSnapshotLayer(builder, this.lazybuilder$tripwire, RenderLayer.getTripwire(), 4);

        TerrainVisibleDrawSnapshot.publish(builder.finish());
    }

    @Unique
    private void lazybuilder$appendVisibleSnapshotLayer(
            TerrainVisibleDrawSnapshot.Builder builder,
            ObjectArrayList<ChunkBuilder.BuiltChunk> chunks,
            RenderLayer layer,
            int layerSlot
    ) {
        for (ChunkBuilder.BuiltChunk chunk : chunks) {
            VertexBuffer buffer = chunk.getBuffer(layer);
            BlockPos origin = chunk.getOrigin();
            builder.add(
                    buffer,
                    layerSlot,
                    origin.getX(),
                    origin.getY(),
                    origin.getZ()
            );
        }
    }

    @Unique
    private void lazybuilder$publishArenaDrawPlan() {
        if (!ChunkPipelineMetrics.detailedMetricsEnabled()) {
            TerrainArenaDrawDiagnostics.clear();
            return;
        }

        TerrainArenaDrawPlanner.Plan plan = TerrainArenaDrawPlanner.Plan.EMPTY;
        plan = TerrainArenaDrawPlanner.combine(plan, this.lazybuilder$planLayer(this.lazybuilder$solid, RenderLayer.getSolid(), 0));
        plan = TerrainArenaDrawPlanner.combine(plan, this.lazybuilder$planLayer(this.lazybuilder$cutoutMipped, RenderLayer.getCutoutMipped(), 1));
        plan = TerrainArenaDrawPlanner.combine(plan, this.lazybuilder$planLayer(this.lazybuilder$cutout, RenderLayer.getCutout(), 2));
        plan = TerrainArenaDrawPlanner.combine(plan, this.lazybuilder$planLayer(this.lazybuilder$translucent, RenderLayer.getTranslucent(), 3));
        plan = TerrainArenaDrawPlanner.combine(plan, this.lazybuilder$planLayer(this.lazybuilder$tripwire, RenderLayer.getTripwire(), 4));
        TerrainArenaDrawDiagnostics.publish(plan);
    }

    @Unique
    private TerrainArenaDrawPlanner.Plan lazybuilder$planLayer(
            ObjectArrayList<ChunkBuilder.BuiltChunk> chunks,
            RenderLayer layer,
            int layerSlot
    ) {
        if (chunks.isEmpty()) return TerrainArenaDrawPlanner.Plan.EMPTY;

        TerrainArenaDrawPlanner.Accumulator planner = TerrainArenaDrawPlanner.accumulator(layerSlot);
        for (ChunkBuilder.BuiltChunk chunk : chunks) {
            VertexBuffer buffer = chunk.getBuffer(layer);
            planner.accept(TerrainGpuResidencyTracker.drawCommand(buffer));
        }
        return planner.finish();
    }

    @Unique
    private void lazybuilder$publishTransformStream(RenderLayer layer, double x, double y, double z) {
        ObjectArrayList<ChunkBuilder.BuiltChunk> chunks = this.lazybuilder$listFor(layer);
        int layerSlot = lazybuilder$layerSlot(layer);
        if (chunks == null || layerSlot < 0) return;
        if (!TerrainPhysicalArenaManager.hasActivePhysicalResidents()) {
            TerrainDrawTransformStream.clearLayer(layerSlot);
            return;
        }

        TerrainDrawTransformStream.Builder builder = this.lazybuilder$transformBuilders[layerSlot];
        boolean reverse = layer == RenderLayer.getTranslucent();
        builder.reset(layerSlot, x, y, z, reverse, chunks.size());

        if (reverse) {
            for (int index = chunks.size() - 1; index >= 0; index--) {
                ChunkBuilder.BuiltChunk chunk = chunks.get(index);
                VertexBuffer buffer = chunk.getBuffer(layer);
                BlockPos origin = chunk.getOrigin();
                builder.accept(
                        buffer,
                        TerrainGpuResidencyTracker.drawCommand(buffer),
                        origin.getX(),
                        origin.getY(),
                        origin.getZ()
                );
            }
        } else {
            for (ChunkBuilder.BuiltChunk chunk : chunks) {
                VertexBuffer buffer = chunk.getBuffer(layer);
                BlockPos origin = chunk.getOrigin();
                builder.accept(
                        buffer,
                        TerrainGpuResidencyTracker.drawCommand(buffer),
                        origin.getX(),
                        origin.getY(),
                        origin.getZ()
                );
            }
        }

        TerrainDrawTransformStream.publish(builder.finish());
    }

    @Unique
    private ObjectArrayList<ChunkBuilder.BuiltChunk> lazybuilder$listFor(RenderLayer layer) {
        if (layer == RenderLayer.getSolid()) return this.lazybuilder$solid;
        if (layer == RenderLayer.getCutoutMipped()) return this.lazybuilder$cutoutMipped;
        if (layer == RenderLayer.getCutout()) return this.lazybuilder$cutout;
        if (layer == RenderLayer.getTranslucent()) return this.lazybuilder$translucent;
        if (layer == RenderLayer.getTripwire()) return this.lazybuilder$tripwire;
        return null;
    }

    @Unique
    private static int lazybuilder$layerSlot(RenderLayer layer) {
        if (layer == RenderLayer.getSolid()) return 0;
        if (layer == RenderLayer.getCutoutMipped()) return 1;
        if (layer == RenderLayer.getCutout()) return 2;
        if (layer == RenderLayer.getTranslucent()) return 3;
        if (layer == RenderLayer.getTripwire()) return 4;
        return -1;
    }

    @Unique
    private static boolean lazybuilder$isBlockLayer(RenderLayer layer) {
        return lazybuilder$layerSlot(layer) >= 0;
    }

    @Unique
    private static TerrainDrawTransformStream.Builder[] lazybuilder$newTransformBuilders() {
        TerrainDrawTransformStream.Builder[] builders = new TerrainDrawTransformStream.Builder[5];
        for (int i = 0; i < builders.length; i++) builders[i] = new TerrainDrawTransformStream.Builder();
        return builders;
    }
}

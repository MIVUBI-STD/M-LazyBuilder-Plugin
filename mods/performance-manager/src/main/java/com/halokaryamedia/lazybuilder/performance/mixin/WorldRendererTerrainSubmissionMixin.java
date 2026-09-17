package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainArenaDrawDiagnostics;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainArenaDrawPlanner;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainGpuResidencyTracker;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainRegionAllocationRegistry;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainSubmissionPolicy;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Builds reusable per-layer submission lists and a draw-order-preserving arena command plan. */
@Mixin(WorldRenderer.class)
abstract class WorldRendererTerrainSubmissionMixin {
    @Shadow @Final private ObjectArrayList<ChunkBuilder.BuiltChunk> builtChunks;

    @Unique private final ObjectArrayList<ChunkBuilder.BuiltChunk> lazybuilder$solid = new ObjectArrayList<>();
    @Unique private final ObjectArrayList<ChunkBuilder.BuiltChunk> lazybuilder$cutoutMipped = new ObjectArrayList<>();
    @Unique private final ObjectArrayList<ChunkBuilder.BuiltChunk> lazybuilder$cutout = new ObjectArrayList<>();
    @Unique private final ObjectArrayList<ChunkBuilder.BuiltChunk> lazybuilder$translucent = new ObjectArrayList<>();
    @Unique private final ObjectArrayList<ChunkBuilder.BuiltChunk> lazybuilder$tripwire = new ObjectArrayList<>();

    @Unique private boolean lazybuilder$submissionIndexDirty = true;
    @Unique private boolean lazybuilder$submissionIndexActive;
    @Unique private int lazybuilder$cachedVisibleCount = -1;
    @Unique private RenderLayer lazybuilder$currentLayer;

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
        this.lazybuilder$currentLayer = layer;

        if (!PerformanceManagerClient.preferences().renderingOptimizations()) {
            this.lazybuilder$submissionIndexActive = false;
            TerrainArenaDrawDiagnostics.clear();
            return;
        }
        if (!lazybuilder$isBlockLayer(layer)) {
            this.lazybuilder$submissionIndexActive = false;
            return;
        }

        if (this.lazybuilder$cachedVisibleCount != this.builtChunks.size()) {
            this.lazybuilder$submissionIndexDirty = true;
        }

        if (this.lazybuilder$submissionIndexDirty) {
            this.lazybuilder$rebuildSubmissionIndex();
        }
    }

    @Redirect(
            method = "renderLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;listIterator(I)Lit/unimi/dsi/fastutil/objects/ObjectListIterator;"
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

    @Unique
    private void lazybuilder$rebuildSubmissionIndex() {
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
        this.lazybuilder$publishArenaDrawPlan();
    }

    @Unique
    private void lazybuilder$publishArenaDrawPlan() {
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

        List<TerrainRegionAllocationRegistry.Handle> handles = new ArrayList<>(chunks.size());
        for (ChunkBuilder.BuiltChunk chunk : chunks) {
            handles.add(TerrainGpuResidencyTracker.allocationHandle(chunk.getBuffer(layer)));
        }
        return TerrainArenaDrawPlanner.plan(handles, layerSlot);
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
    private static boolean lazybuilder$isBlockLayer(RenderLayer layer) {
        return layer == RenderLayer.getSolid()
                || layer == RenderLayer.getCutoutMipped()
                || layer == RenderLayer.getCutout()
                || layer == RenderLayer.getTranslucent()
                || layer == RenderLayer.getTripwire();
    }
}

package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.rendering.TerrainGpuResidencyTracker;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainVanillaBackingAccess;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlBufferTarget;
import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.GpuBuffer;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.BufferAllocator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Owns terrain buffer residency accounting and reversible vanilla-backing retirement/recovery. */
@Mixin(VertexBuffer.class)
abstract class VertexBufferMixin implements TerrainVanillaBackingAccess {
    @Shadow @Final private GlUsage usage;
    @Shadow @Final @Mutable private GpuBuffer vertexBuffer;
    @Shadow private GpuBuffer indexBuffer;
    @Shadow private int vertexArrayId;
    @Shadow private VertexFormat vertexFormat;
    @Shadow private RenderSystem.ShapeIndexBuffer sharedSequentialIndexBuffer;
    @Shadow private VertexFormat.IndexType indexType;
    @Shadow private int indexCount;
    @Shadow private VertexFormat.DrawMode drawMode;
    @Unique private boolean lazybuilder$vanillaBackingRetired;

    @Inject(method = "upload", at = @At("HEAD"))
    private void lazybuilder$prepareRetiredBackingForUpload(BuiltBuffer data, CallbackInfo ci) {
        if (!this.lazybuilder$vanillaBackingRetired || this.vertexArrayId < 0) return;
        BufferRenderer.resetCurrentVertexBuffer();
        GlStateManager._glBindVertexArray(this.vertexArrayId);
        this.vertexBuffer.bind();
        if (this.vertexFormat != null) this.vertexFormat.setupState();
        this.lazybuilder$vanillaBackingRetired = false;
    }

    @Inject(method = "upload", at = @At("RETURN"))
    private void lazybuilder$recordVertexCapacity(BuiltBuffer data, CallbackInfo ci) {
        this.lazybuilder$recordTerrainCapacity();
    }

    @Inject(
            method = "uploadIndexBuffer(Lnet/minecraft/client/util/BufferAllocator$CloseableBuffer;)V",
            at = @At("RETURN")
    )
    private void lazybuilder$recordIndexCapacity(BufferAllocator.CloseableBuffer data, CallbackInfo ci) {
        this.lazybuilder$recordTerrainCapacity();
    }

    private void lazybuilder$recordTerrainCapacity() {
        VertexBuffer self = (VertexBuffer) (Object) this;
        TerrainGpuResidencyTracker.recordCapacity(
                self,
                this.vertexBuffer == null ? 0 : this.vertexBuffer.size,
                this.indexBuffer == null ? 0 : this.indexBuffer.size
        );
    }

    @Override public GlUsage lazybuilder$usage() { return this.usage; }
    @Override public boolean lazybuilder$isVanillaBackingRetired() { return this.lazybuilder$vanillaBackingRetired; }

    @Override
    public long lazybuilder$retireVanillaBacking() {
        if (this.lazybuilder$vanillaBackingRetired || this.vertexArrayId < 0 || this.vertexBuffer == null) return 0L;
        long bytes = Math.max(0, this.vertexBuffer.size);
        if (this.indexBuffer != null) bytes += Math.max(0, this.indexBuffer.size);
        if (bytes <= 0L) return 0L;
        this.vertexBuffer.close();
        if (this.indexBuffer != null) this.indexBuffer.close();
        this.vertexBuffer = new GpuBuffer(GlBufferTarget.VERTICES, this.usage, 0);
        this.indexBuffer = null;
        this.lazybuilder$vanillaBackingRetired = true;
        return bytes;
    }

    @Override
    public void lazybuilder$installRecoveredVanillaBacking(
            GpuBuffer recoveredVertexBuffer,
            GpuBuffer recoveredIndexBuffer,
            VertexFormat format,
            VertexFormat.DrawMode mode,
            VertexFormat.IndexType recoveredIndexType,
            int recoveredIndexCount
    ) {
        if (recoveredVertexBuffer == null || format == null || mode == null || recoveredIndexType == null
                || recoveredIndexCount <= 0 || this.vertexArrayId < 0) {
            throw new IllegalArgumentException("Incomplete recovered terrain backing");
        }
        if (this.vertexBuffer != null) this.vertexBuffer.close();
        if (this.indexBuffer != null) this.indexBuffer.close();
        this.vertexBuffer = recoveredVertexBuffer;
        this.indexBuffer = recoveredIndexBuffer;
        this.vertexFormat = format;
        this.drawMode = mode;
        this.indexType = recoveredIndexType;
        this.indexCount = recoveredIndexCount;

        BufferRenderer.resetCurrentVertexBuffer();
        GlStateManager._glBindVertexArray(this.vertexArrayId);
        this.vertexBuffer.bind();
        this.vertexFormat.setupState();
        if (this.indexBuffer != null) {
            this.indexBuffer.bind();
            this.sharedSequentialIndexBuffer = null;
        } else {
            RenderSystem.ShapeIndexBuffer sequential = RenderSystem.getSequentialBuffer(this.drawMode);
            sequential.bindAndGrow(this.indexCount);
            this.sharedSequentialIndexBuffer = sequential;
            this.indexType = sequential.getIndexType();
        }
        this.lazybuilder$vanillaBackingRetired = false;
    }
}

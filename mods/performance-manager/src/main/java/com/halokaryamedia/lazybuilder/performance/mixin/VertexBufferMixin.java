package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.GpuBufferGrowthPolicy;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainGpuResidencyTracker;
import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.GpuBuffer;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.util.BufferAllocator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses writable GPU allocations and records actual terrain buffer capacity after uploads. */
@Mixin(VertexBuffer.class)
abstract class VertexBufferMixin {
    @Shadow
    @Final
    private GlUsage usage;

    @Shadow
    @Final
    private GpuBuffer vertexBuffer;

    @Shadow
    private GpuBuffer indexBuffer;

    @Redirect(
            method = "uploadVertexBuffer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gl/GpuBuffer;resize(I)V"
            )
    )
    private void lazybuilder$resizeOnlyWhenNeeded(GpuBuffer buffer, int newSize) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()
                || this.usage == GlUsage.STATIC_WRITE) {
            buffer.resize(newSize);
            return;
        }

        if (newSize > buffer.size) {
            buffer.resize(GpuBufferGrowthPolicy.capacityFor(buffer.size, newSize));
        }
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
}

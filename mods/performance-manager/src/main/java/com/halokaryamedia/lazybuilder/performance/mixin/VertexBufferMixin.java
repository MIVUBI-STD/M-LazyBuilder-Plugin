package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.GpuBufferGrowthPolicy;
import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.GpuBuffer;
import net.minecraft.client.gl.VertexBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reuses writable GPU allocations and grows them with bounded headroom when capacity is exhausted. */
@Mixin(VertexBuffer.class)
abstract class VertexBufferMixin {
    @Shadow
    @Final
    private GlUsage usage;

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
}

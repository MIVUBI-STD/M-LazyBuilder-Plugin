package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import com.halokaryamedia.lazybuilder.performance.rendering.ParticlePressurePolicy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suppresses only distant particle creation when frame pressure is elevated or heavy. */
@Mixin(ParticleManager.class)
abstract class ParticleManagerMixin {
    @Inject(
            method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lazybuilder$throttleDistantParticles(
            ParticleEffect parameters,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfoReturnable<Particle> cir
    ) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.gameRenderer == null) return;

        Vec3d camera = client.gameRenderer.getCamera().getPos();
        double dx = x - camera.x;
        double dy = y - camera.y;
        double dz = z - camera.z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;

        if (ParticlePressurePolicy.shouldSuppress(PerformanceManagerClient.pressure(), distanceSquared)) {
            ChunkPipelineMetrics.recordParticleSuppressed();
            cir.setReturnValue(null);
        }
    }
}

package com.halokaryamedia.lazybuilder.performance.mixin;

import net.minecraft.client.render.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accessor used only by first-party baked-quad memory deduplication. */
@Mixin(BakedQuad.class)
public interface BakedQuadAccessor {
    @Accessor("vertexData")
    @Mutable
    void lazybuilder$setVertexData(int[] vertices);
}

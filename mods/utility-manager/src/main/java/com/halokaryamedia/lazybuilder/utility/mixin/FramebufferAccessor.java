package com.halokaryamedia.lazybuilder.utility.mixin;

import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Narrow access to framebuffer dimensions needed by the capture pipeline. */
@Mixin(Framebuffer.class)
public interface FramebufferAccessor {
    @Accessor("textureWidth")
    int lazybuilder$getTextureWidth();

    @Accessor("textureHeight")
    int lazybuilder$getTextureHeight();
}

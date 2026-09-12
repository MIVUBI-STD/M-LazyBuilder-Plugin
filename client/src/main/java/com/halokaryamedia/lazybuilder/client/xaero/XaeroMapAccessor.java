package com.halokaryamedia.lazybuilder.client.xaero;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Version-pinned Xaero 1.21.4 screen boundary. This is the only source that
 * knows Xaero's fullscreen-map camera fields. Runtime compatibility still
 * requires local client proof before this hook is promoted as live-verified.
 */
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public interface XaeroMapAccessor {
    @Accessor("cameraX") double lazybuilder$getCameraX();
    @Accessor("cameraZ") double lazybuilder$getCameraZ();
    @Accessor("scale") double lazybuilder$getScale();
}

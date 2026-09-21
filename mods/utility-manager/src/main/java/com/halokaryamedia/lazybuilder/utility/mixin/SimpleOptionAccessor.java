package com.halokaryamedia.lazybuilder.utility.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to the default stored by Minecraft's own SimpleOption.
 *
 * Used only for truthful modified-state presentation and category reset. LazyBuilder
 * never maintains a second table of vanilla defaults.
 */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {
    @Accessor("defaultValue")
    Object lazybuilder$getDefaultValue();
}

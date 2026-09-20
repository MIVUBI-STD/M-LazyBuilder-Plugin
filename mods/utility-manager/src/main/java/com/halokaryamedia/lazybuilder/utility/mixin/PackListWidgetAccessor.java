package com.halokaryamedia.lazybuilder.utility.mixin;

import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Presentation-only accessor used to rename vanilla Selected to the clearer Active label. */
@Mixin(PackListWidget.class)
public interface PackListWidgetAccessor {
    @Accessor("title")
    @Mutable
    @Final
    void lazybuilder$setTitle(Text title);
}

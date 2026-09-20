package com.halokaryamedia.lazybuilder.utility.mixin;

import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.PackScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Narrow presentation-only access to Minecraft's native resource-pack lists. */
@Mixin(PackScreen.class)
public interface PackScreenAccessor {
    @Accessor("availablePackList")
    PackListWidget lazybuilder$getAvailablePackList();

    @Accessor("selectedPackList")
    PackListWidget lazybuilder$getSelectedPackList();
}

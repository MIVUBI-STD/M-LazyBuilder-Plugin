package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.util.StringHelper;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets printable typing enter the vanilla creative search flow without requiring
 * a click on the search tab or search box first.
 */
@Mixin(CreativeInventoryScreen.class)
abstract class CreativeInventoryScreenMixin {
    private static final int LAZYBUILDER_BLOCKED_MODIFIERS =
            GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;

    @Shadow
    private static ItemGroup selectedTab;

    @Shadow
    private TextFieldWidget searchBox;

    @Invoker("setSelectedTab")
    protected abstract void lazybuilder$setSelectedTab(ItemGroup group);

    @Inject(method = "charTyped", at = @At("HEAD"))
    private void lazybuilder$focusCreativeSearch(
            char chr,
            int modifiers,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!UtilityManagerClient.preferences().instantCreativeSearch()) return;
        if (searchBox == null || searchBox.isFocused()) return;
        if (!StringHelper.isValidChar(chr)) return;
        if ((modifiers & LAZYBUILDER_BLOCKED_MODIFIERS) != 0) return;

        CreativeInventoryScreen screen = (CreativeInventoryScreen) (Object) this;
        Element focused = screen.getFocused();
        if (focused != null && focused != searchBox) return;

        ItemGroup searchGroup = ItemGroups.getSearchGroup();
        if (selectedTab != searchGroup) {
            lazybuilder$setSelectedTab(searchGroup);
        }

        searchBox.setFocused(true);
        // Do not cancel: vanilla charTyped now receives the original character,
        // updates the search field, and refreshes the vanilla result list.
    }
}

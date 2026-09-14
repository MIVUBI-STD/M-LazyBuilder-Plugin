package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Extends vanilla chat retention without replacing the vanilla chat UI. */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    private static final int VANILLA_HISTORY_LIMIT = 100;
    private static final int EXTENDED_HISTORY_LIMIT = 1000;

    @ModifyConstant(
            method = "addToMessageHistory(Ljava/lang/String;)V",
            constant = @Constant(intValue = VANILLA_HISTORY_LIMIT)
    )
    private int lazybuilder$extendInputHistoryLimit(int original) {
        return UtilityManagerClient.preferences().extendedChatHistory()
                ? EXTENDED_HISTORY_LIMIT
                : original;
    }

    @ModifyConstant(
            method = "addVisibleMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
            constant = @Constant(intValue = VANILLA_HISTORY_LIMIT)
    )
    private int lazybuilder$extendVisibleHistoryLimit(int original) {
        return UtilityManagerClient.preferences().extendedChatHistory()
                ? EXTENDED_HISTORY_LIMIT
                : original;
    }

    @ModifyConstant(
            method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
            constant = @Constant(intValue = VANILLA_HISTORY_LIMIT)
    )
    private int lazybuilder$extendStoredHistoryLimit(int original) {
        return UtilityManagerClient.preferences().extendedChatHistory()
                ? EXTENDED_HISTORY_LIMIT
                : original;
    }
}

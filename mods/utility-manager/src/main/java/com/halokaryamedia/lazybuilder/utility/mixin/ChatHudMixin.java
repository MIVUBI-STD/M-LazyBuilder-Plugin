package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends vanilla chat retention and observes visible text without replacing ChatHud. */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    private static final int VANILLA_HISTORY_LIMIT = 100;
    private static final int EXTENDED_HISTORY_LIMIT = 1000;

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void lazybuilder$indexVisibleMessage(Text message, CallbackInfo ci) {
        if (message == null) return;
        UtilityManagerClient.chatSearchHistory().record(message.getString(), System.currentTimeMillis());
    }

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

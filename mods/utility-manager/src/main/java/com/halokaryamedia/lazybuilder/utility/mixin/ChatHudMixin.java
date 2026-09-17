package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.chat.ChatCollapseState;
import com.halokaryamedia.lazybuilder.utility.chat.ChatTimestampFormatter;
import com.halokaryamedia.lazybuilder.utility.chat.signing.SigningPresentationPolicy;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.ZoneId;
import java.util.List;

/**
 * Thin vanilla ChatHud adapter for bounded history, timestamps, search indexing,
 * signing-indicator presentation, and conservative in-place duplicate collapse.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    private static final int VANILLA_HISTORY_LIMIT = 100;
    private static final int EXTENDED_HISTORY_LIMIT = 1000;

    @Shadow
    @Final
    private List<ChatHudLine> messages;

    @Shadow
    @Final
    private List<ChatHudLine.Visible> visibleMessages;

    @Shadow
    private int scrolledLines;

    private boolean lazybuilder$readdingCollapsedLine;

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text lazybuilder$prependTimestamp(Text message) {
        if (message == null || !UtilityManagerClient.preferences().chatTimestamps()) return message;

        String timestamp = ChatTimestampFormatter.format(System.currentTimeMillis(), ZoneId.systemDefault());
        return Text.literal(timestamp + "  ")
                .formatted(Formatting.DARK_GRAY)
                .append(message.copy());
    }

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private MessageIndicator lazybuilder$hideSigningIndicator(MessageIndicator indicator) {
        return SigningPresentationPolicy.visibleIndicator(
                indicator,
                UtilityManagerClient.preferences().hideChatSigningIndicators()
        );
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$collapseAndIndexVisibleMessage(Text message, CallbackInfo ci) {
        if (message == null) return;

        long nowMillis = System.currentTimeMillis();
        if (!this.lazybuilder$readdingCollapsedLine) {
            if (this.scrolledLines == 0) {
                ChatCollapseState.Decision collapse = UtilityManagerClient.chatCollapseState()
                        .accept(message.getString(), nowMillis);

                if (collapse.collapse() && this.lazybuilder$removeNewestEntry()) {
                    String collapsedText = collapse.collapsedText();
                    if (UtilityManagerClient.preferences().chatSearch()) {
                        UtilityManagerClient.chatSearchHistory().replaceLatest(collapsedText);
                    }

                    this.lazybuilder$readdingCollapsedLine = true;
                    try {
                        ((ChatHud) (Object) this).addMessage(Text.literal(collapsedText));
                    } finally {
                        this.lazybuilder$readdingCollapsedLine = false;
                    }
                    ci.cancel();
                    return;
                }
            } else {
                UtilityManagerClient.chatCollapseState().clear();
            }

            if (UtilityManagerClient.preferences().chatSearch()) {
                UtilityManagerClient.chatSearchHistory().record(message.getString(), nowMillis);
            }
        }
    }

    private boolean lazybuilder$removeNewestEntry() {
        if (this.messages.isEmpty()) return false;

        this.messages.remove(0);
        while (!this.visibleMessages.isEmpty()) {
            ChatHudLine.Visible removed = this.visibleMessages.remove(0);
            if (removed.endOfEntry()) break;
        }
        return true;
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

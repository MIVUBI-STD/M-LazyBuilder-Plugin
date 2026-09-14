package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.chat.ChatDraftState;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps an unsent vanilla chat draft across close/reopen within one client session. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    protected TextFieldWidget chatField;

    private boolean lazybuilder$submitted;

    @Inject(method = "init", at = @At("TAIL"))
    private void lazybuilder$restoreDraft(CallbackInfo ci) {
        if (!UtilityManagerClient.preferences().keepChatDraft()) return;
        if (!this.chatField.getText().isEmpty()) return;

        String draft = ChatDraftState.get();
        if (!draft.isEmpty()) this.chatField.setText(draft);
    }

    @Inject(method = "sendMessage", at = @At("HEAD"))
    private void lazybuilder$markSubmitted(String chatText, boolean addToHistory, CallbackInfo ci) {
        this.lazybuilder$submitted = true;
        ChatDraftState.clear();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void lazybuilder$saveDraft(CallbackInfo ci) {
        if (!UtilityManagerClient.preferences().keepChatDraft()) {
            ChatDraftState.clear();
            return;
        }

        if (this.lazybuilder$submitted) {
            ChatDraftState.clear();
        } else {
            ChatDraftState.save(this.chatField == null ? "" : this.chatField.getText());
        }
    }
}

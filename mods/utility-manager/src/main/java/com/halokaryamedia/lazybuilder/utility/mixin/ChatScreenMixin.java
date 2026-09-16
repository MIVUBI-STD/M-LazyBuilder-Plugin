package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.chat.ChatDraftState;
import com.halokaryamedia.lazybuilder.utility.chat.ChatHistoryEntry;
import com.halokaryamedia.lazybuilder.utility.chat.ChatSearchSession;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps an unsent vanilla chat draft and adds a small native Ctrl+F search overlay.
 * The vanilla chat screen remains authoritative; this mixin only adds bounded
 * session search behavior on top of it.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    private static final int SEARCH_WIDTH = 180;
    private static final int SEARCH_HEIGHT = 18;
    private static final int SEARCH_MARGIN = 6;
    private static final int SEARCH_COUNTER_WIDTH = 42;

    @Shadow
    protected TextFieldWidget chatField;

    private boolean lazybuilder$submitted;
    private boolean lazybuilder$searchOpen;
    private TextFieldWidget lazybuilder$searchField;
    private ChatSearchSession lazybuilder$searchSession;
    private int lazybuilder$searchX;
    private int lazybuilder$searchY;

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void lazybuilder$initializeUtilityChat(CallbackInfo ci) {
        if (UtilityManagerClient.preferences().keepChatDraft() && this.chatField.getText().isEmpty()) {
            String draft = ChatDraftState.get();
            if (!draft.isEmpty()) this.chatField.setText(draft);
        }

        if (!UtilityManagerClient.preferences().chatSearch()) return;

        if (this.lazybuilder$searchSession == null) {
            this.lazybuilder$searchSession = new ChatSearchSession(UtilityManagerClient.chatSearchHistory());
        }

        this.lazybuilder$searchX = Math.max(SEARCH_MARGIN, this.width - SEARCH_WIDTH - SEARCH_COUNTER_WIDTH - SEARCH_MARGIN);
        this.lazybuilder$searchY = Math.max(SEARCH_MARGIN, this.height - 38);
        this.lazybuilder$searchField = new TextFieldWidget(
                this.textRenderer,
                this.lazybuilder$searchX,
                this.lazybuilder$searchY,
                SEARCH_WIDTH,
                SEARCH_HEIGHT,
                Text.literal("Search chat")
        );
        this.lazybuilder$searchField.setPlaceholder(Text.literal("Search chat"));
        this.lazybuilder$searchField.setMaxLength(128);
        this.lazybuilder$searchField.setChangedListener(this.lazybuilder$searchSession::setQuery);
        this.lazybuilder$searchField.setVisible(this.lazybuilder$searchOpen);
        this.addDrawableChild(this.lazybuilder$searchField);

        if (this.lazybuilder$searchOpen) {
            this.lazybuilder$focusSearch();
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$handleSearchKeys(
            int keyCode,
            int scanCode,
            int modifiers,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!UtilityManagerClient.preferences().chatSearch()) return;

        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_F) {
            if (this.lazybuilder$searchOpen) {
                this.lazybuilder$closeSearch();
            } else {
                this.lazybuilder$openSearch();
            }
            cir.setReturnValue(true);
            return;
        }

        if (!this.lazybuilder$searchOpen) return;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.lazybuilder$closeSearch();
            cir.setReturnValue(true);
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.lazybuilder$searchSession != null) {
                if (Screen.hasShiftDown()) {
                    this.lazybuilder$searchSession.previous();
                } else {
                    this.lazybuilder$searchSession.next();
                }
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void lazybuilder$renderSearchOverlay(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        if (!this.lazybuilder$searchOpen || this.lazybuilder$searchSession == null) return;

        String counter = this.lazybuilder$searchSession.selectedOrdinal()
                + " / "
                + this.lazybuilder$searchSession.matchCount();
        context.drawTextWithShadow(
                this.textRenderer,
                counter,
                this.lazybuilder$searchX + SEARCH_WIDTH + SEARCH_MARGIN,
                this.lazybuilder$searchY + 5,
                0xA0A0A0
        );

        ChatHistoryEntry selected = this.lazybuilder$searchSession.selected();
        if (selected == null) return;

        String preview = this.textRenderer.trimToWidth(selected.text(), SEARCH_WIDTH + SEARCH_COUNTER_WIDTH);
        int previewY = Math.max(SEARCH_MARGIN, this.lazybuilder$searchY - 12);
        context.drawTextWithShadow(
                this.textRenderer,
                preview,
                this.lazybuilder$searchX,
                previewY,
                0xD0D0D0
        );
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

    private void lazybuilder$openSearch() {
        if (this.lazybuilder$searchField == null || this.lazybuilder$searchSession == null) return;
        this.lazybuilder$searchOpen = true;
        this.lazybuilder$searchField.setVisible(true);
        this.lazybuilder$focusSearch();
    }

    private void lazybuilder$closeSearch() {
        this.lazybuilder$searchOpen = false;
        if (this.lazybuilder$searchSession != null) this.lazybuilder$searchSession.clear();
        if (this.lazybuilder$searchField != null) {
            this.lazybuilder$searchField.setText("");
            this.lazybuilder$searchField.setFocused(false);
            this.lazybuilder$searchField.setVisible(false);
        }
        if (this.chatField != null) {
            this.setFocused(this.chatField);
            this.chatField.setFocused(true);
        }
    }

    private void lazybuilder$focusSearch() {
        if (this.lazybuilder$searchField == null) return;
        if (this.chatField != null) this.chatField.setFocused(false);
        this.setFocused(this.lazybuilder$searchField);
        this.lazybuilder$searchField.setFocused(true);
    }
}

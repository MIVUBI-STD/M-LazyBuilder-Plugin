package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.chat.ChatContextText;
import com.halokaryamedia.lazybuilder.utility.chat.ChatDraftState;
import com.halokaryamedia.lazybuilder.utility.chat.ChatHistoryEntry;
import com.halokaryamedia.lazybuilder.utility.chat.ChatSearchSession;
import com.halokaryamedia.lazybuilder.utility.clipboard.UtilityClipboard;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
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

import java.util.List;

/**
 * Keeps an unsent vanilla chat draft and adds small native search/context overlays.
 * The vanilla chat screen remains authoritative.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    private static final int SEARCH_WIDTH = 180;
    private static final int SEARCH_HEIGHT = 18;
    private static final int SEARCH_MARGIN = 6;
    private static final int SEARCH_COUNTER_WIDTH = 42;
    private static final int CONTEXT_WIDTH = 126;
    private static final int CONTEXT_ROW_HEIGHT = 18;

    @Shadow
    protected TextFieldWidget chatField;

    private boolean lazybuilder$submitted;
    private boolean lazybuilder$searchOpen;
    private TextFieldWidget lazybuilder$searchField;
    private ChatSearchSession lazybuilder$searchSession;
    private int lazybuilder$searchX;
    private int lazybuilder$searchY;
    private boolean lazybuilder$contextOpen;
    private int lazybuilder$contextX;
    private int lazybuilder$contextY;
    private String lazybuilder$contextMessage = "";
    private String lazybuilder$contextSender = "";

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
    private void lazybuilder$handleUtilityKeys(
            int keyCode,
            int scanCode,
            int modifiers,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.lazybuilder$contextOpen) {
            this.lazybuilder$closeContextMenu();
            cir.setReturnValue(true);
            return;
        }

        if (!UtilityManagerClient.preferences().chatSearch()) return;

        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_F) {
            this.lazybuilder$closeContextMenu();
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

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$handleContextClick(
            double mouseX,
            double mouseY,
            int button,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (this.lazybuilder$contextOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.lazybuilder$isInsideContext(mouseX, mouseY)) {
                int row = (int) ((mouseY - this.lazybuilder$contextY) / CONTEXT_ROW_HEIGHT);
                if (row == 0) {
                    UtilityClipboard.copy(this.lazybuilder$contextMessage, "Chat message copied.");
                } else if (row == 1 && !this.lazybuilder$contextSender.isBlank()) {
                    UtilityClipboard.copy(this.lazybuilder$contextSender, "Player name copied.");
                }
                this.lazybuilder$closeContextMenu();
                cir.setReturnValue(true);
                return;
            }
            this.lazybuilder$closeContextMenu();
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || this.client == null || this.client.inGameHud == null) return;

        ChatHud chatHud = this.client.inGameHud.getChatHud();
        ChatHudAccessor accessor = (ChatHudAccessor) chatHud;
        double chatX = accessor.lazybuilder$toChatLineX(mouseX);
        double chatY = accessor.lazybuilder$toChatLineY(mouseY);
        int messageIndex = accessor.lazybuilder$getMessageIndex(chatX, chatY);
        List<ChatHudLine> messages = accessor.lazybuilder$getMessages();
        if (messageIndex < 0 || messageIndex >= messages.size()) return;

        ChatContextText.Parsed parsed = ChatContextText.parse(messages.get(messageIndex).content().getString());
        if (parsed.fullText().isBlank()) return;

        this.lazybuilder$contextMessage = parsed.fullText();
        this.lazybuilder$contextSender = parsed.sender();
        int contextHeight = this.lazybuilder$contextHeight();
        this.lazybuilder$contextX = Math.max(SEARCH_MARGIN, Math.min((int) mouseX, this.width - CONTEXT_WIDTH - SEARCH_MARGIN));
        this.lazybuilder$contextY = Math.max(SEARCH_MARGIN, Math.min((int) mouseY, this.height - contextHeight - SEARCH_MARGIN));
        this.lazybuilder$contextOpen = true;
        cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void lazybuilder$renderUtilityOverlays(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        if (this.lazybuilder$searchOpen && this.lazybuilder$searchSession != null) {
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
            if (selected != null) {
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
        }

        if (this.lazybuilder$contextOpen) {
            int contextHeight = this.lazybuilder$contextHeight();
            context.fill(
                    this.lazybuilder$contextX,
                    this.lazybuilder$contextY,
                    this.lazybuilder$contextX + CONTEXT_WIDTH,
                    this.lazybuilder$contextY + contextHeight,
                    0xD0101010
            );

            this.lazybuilder$renderContextRow(context, mouseX, mouseY, 0, "Copy Message");
            if (!this.lazybuilder$contextSender.isBlank()) {
                this.lazybuilder$renderContextRow(context, mouseX, mouseY, 1, "Copy Player Name");
            }
        }
    }

    @Inject(method = "sendMessage", at = @At("HEAD"))
    private void lazybuilder$markSubmitted(String chatText, boolean addToHistory, CallbackInfo ci) {
        this.lazybuilder$submitted = true;
        ChatDraftState.clear();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void lazybuilder$saveDraft(CallbackInfo ci) {
        this.lazybuilder$closeContextMenu();
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

    private int lazybuilder$contextHeight() {
        return CONTEXT_ROW_HEIGHT * (this.lazybuilder$contextSender.isBlank() ? 1 : 2);
    }

    private boolean lazybuilder$isInsideContext(double mouseX, double mouseY) {
        return mouseX >= this.lazybuilder$contextX
                && mouseX <= this.lazybuilder$contextX + CONTEXT_WIDTH
                && mouseY >= this.lazybuilder$contextY
                && mouseY <= this.lazybuilder$contextY + this.lazybuilder$contextHeight();
    }

    private void lazybuilder$renderContextRow(
            DrawContext context,
            int mouseX,
            int mouseY,
            int row,
            String label
    ) {
        int top = this.lazybuilder$contextY + row * CONTEXT_ROW_HEIGHT;
        boolean hovered = mouseX >= this.lazybuilder$contextX
                && mouseX <= this.lazybuilder$contextX + CONTEXT_WIDTH
                && mouseY >= top
                && mouseY < top + CONTEXT_ROW_HEIGHT;
        if (hovered) {
            context.fill(
                    this.lazybuilder$contextX + 1,
                    top + 1,
                    this.lazybuilder$contextX + CONTEXT_WIDTH - 1,
                    top + CONTEXT_ROW_HEIGHT - 1,
                    0x80404040
            );
        }
        context.drawTextWithShadow(
                this.textRenderer,
                label,
                this.lazybuilder$contextX + 6,
                top + 5,
                0xFFFFFF
        );
    }

    private void lazybuilder$closeContextMenu() {
        this.lazybuilder$contextOpen = false;
        this.lazybuilder$contextMessage = "";
        this.lazybuilder$contextSender = "";
    }
}

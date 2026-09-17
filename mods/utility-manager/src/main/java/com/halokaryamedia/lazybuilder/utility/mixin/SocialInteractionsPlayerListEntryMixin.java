package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import net.minecraft.client.gui.screen.multiplayer.SocialInteractionsPlayerListEntry;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Presentation-only report-button suppression for the vanilla social player list.
 * Reportability data and chat/signing protocol state are intentionally untouched.
 */
@Mixin(SocialInteractionsPlayerListEntry.class)
public abstract class SocialInteractionsPlayerListEntryMixin {
    @Shadow
    private ButtonWidget reportButton;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void lazybuilder$applyReportButtonPresentation(CallbackInfo ci) {
        if (!UtilityManagerClient.preferences().hideChatReportButton()) return;
        if (this.reportButton != null) this.reportButton.visible = false;
    }
}

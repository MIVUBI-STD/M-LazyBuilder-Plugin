package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.clipboard.UtilityClipboard;
import com.halokaryamedia.lazybuilder.utility.connection.ReconnectState;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds small reconnect/copy conveniences to the existing vanilla disconnect layout. */
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    @Shadow
    @Final
    private DirectionalLayoutWidget grid;

    @Shadow
    @Final
    private DisconnectionInfo info;

    protected DisconnectedScreenMixin(Text title) {
        super(title);
    }

    @Inject(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/DirectionalLayoutWidget;refreshPositions()V"
            )
    )
    private void lazybuilder$addUtilityActions(CallbackInfo ci) {
        if (UtilityManagerClient.preferences().reconnectButton() && ReconnectState.canReconnect()) {
            this.grid.add(
                    ButtonWidget.builder(
                            Text.literal("Reconnect"),
                            button -> ReconnectState.reconnect((Screen) (Object) this)
                    ).width(200).build()
            );
        }

        String details = lazybuilder$disconnectDetails();
        if (!details.isBlank()) {
            this.grid.add(
                    ButtonWidget.builder(
                            Text.literal("Copy Details"),
                            button -> UtilityClipboard.copy(details, "Connection details copied.")
                    ).width(200).build()
            );
        }
    }

    private String lazybuilder$disconnectDetails() {
        String address = ReconnectState.serverAddress();
        String reason = this.info == null || this.info.reason() == null
                ? ""
                : this.info.reason().getString();

        if (!address.isBlank() && !reason.isBlank()) {
            return "Server: " + address + "\nReason: " + reason;
        }
        if (!address.isBlank()) return "Server: " + address;
        if (!reason.isBlank()) return "Reason: " + reason;
        return "";
    }
}

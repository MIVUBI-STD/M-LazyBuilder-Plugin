package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.connection.ReconnectState;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds one reconnect action to the existing vanilla disconnect layout. */
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    @Shadow
    @Final
    private DirectionalLayoutWidget grid;

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
    private void lazybuilder$addReconnectButton(CallbackInfo ci) {
        if (!UtilityManagerClient.preferences().reconnectButton()) return;
        if (!ReconnectState.canReconnect()) return;

        this.grid.add(
                ButtonWidget.builder(
                        Text.literal("Reconnect"),
                        button -> ReconnectState.reconnect((Screen) (Object) this)
                ).width(200).build()
        );
    }
}

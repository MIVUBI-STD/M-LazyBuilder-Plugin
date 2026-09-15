package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.connection.ReconnectState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fallback reconnect entrypoint for disconnect flows that return directly to the server list
 * instead of leaving the vanilla DisconnectedScreen visible.
 */
@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Utility/Reconnect");

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void lazybuilder$addReconnectFallback(CallbackInfo ci) {
        if (!UtilityManagerClient.preferences().reconnectButton() || !ReconnectState.canReconnect()) {
            return;
        }

        int buttonWidth = 100;
        int buttonHeight = 20;
        int x = Math.max(5, this.width - buttonWidth - 5);
        int y = 5;
        this.addDrawableChild(
                ButtonWidget.builder(
                                Text.literal("Reconnect"),
                                button -> ReconnectState.reconnect((Screen) (Object) this)
                        )
                        .dimensions(x, y, buttonWidth, buttonHeight)
                        .build()
        );
        LOGGER.info("Reconnect fallback added to MultiplayerScreen for {}", ReconnectState.serverAddress());
    }
}

package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.connection.ReconnectState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the server being attempted so failed pre-JOIN connections reconnect to the correct target. */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(
            method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V",
            at = @At("HEAD")
    )
    private static void lazybuilder$captureAttemptedServer(
            Screen parent,
            MinecraftClient client,
            ServerAddress address,
            ServerInfo info,
            boolean quickPlay,
            CookieStorage cookieStorage,
            CallbackInfo ci
    ) {
        ReconnectState.capture(info);
    }
}

package com.halokaryamedia.lazybuilder.utility.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Narrow accessor surface used only by the vanilla-shaped chat context menu. */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Accessor("messages")
    List<ChatHudLine> lazybuilder$getMessages();

    @Accessor("visibleMessages")
    List<ChatHudLine.Visible> lazybuilder$getVisibleMessages();

    @Invoker("toChatLineX")
    double lazybuilder$toChatLineX(double x);

    @Invoker("toChatLineY")
    double lazybuilder$toChatLineY(double y);

    @Invoker("getMessageIndex")
    int lazybuilder$getMessageIndex(double chatLineX, double chatLineY);
}

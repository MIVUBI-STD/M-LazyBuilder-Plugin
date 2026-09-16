package com.halokaryamedia.lazybuilder.utility.chat;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

/**
 * Thin Fabric adapter for received server/game messages.
 *
 * Action-bar messages and unrecognized system text remain untouched. Only
 * recognized GAME/WARNING/ERROR messages are compacted, which keeps this layer
 * compatible with vanilla and other mods that attach styling/click behavior to
 * unrelated chat lines.
 */
public final class MinecraftMessageBridge {
    private static boolean registered;

    private MinecraftMessageBridge() {
    }

    public static void register() {
        if (registered) return;
        registered = true;

        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (overlay || message == null) return message;

            ChatMessage classified = MinecraftMessageClassifier.systemMessage(message.getString());
            if (!shouldCompact(classified)) return message;

            String compact = ChatPresentationFormatter.chatLine(classified, 1);
            if (eligibleForCollapse(classified)) {
                UtilityManagerClient.chatCollapseState().prepareEligible(compact);
            }
            return Text.literal(compact);
        });
    }

    static boolean shouldCompact(ChatMessage message) {
        return switch (message.type()) {
            case GAME, WARNING, ERROR -> true;
            case CHAT, SYSTEM -> false;
        };
    }

    private static boolean eligibleForCollapse(ChatMessage message) {
        return switch (message.type()) {
            case GAME, WARNING -> true;
            case CHAT, SYSTEM, ERROR -> false;
        };
    }
}

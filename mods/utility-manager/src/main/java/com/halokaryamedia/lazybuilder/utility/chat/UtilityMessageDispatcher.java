package com.halokaryamedia.lazybuilder.utility.chat;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.notification.UtilityNotifications;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Minecraft-facing bridge for normalized Utility messages.
 *
 * Keeps routing/presentation outside producers. Duplicate chat lines are suppressed
 * until the later in-place collapse presenter is introduced; console diagnostics
 * remain complete.
 */
public final class UtilityMessageDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/UtilityMessages");

    private UtilityMessageDispatcher() {
    }

    public static void publish(ChatMessage message) {
        Objects.requireNonNull(message, "message");
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        client.execute(() -> dispatch(client, message, System.currentTimeMillis()));
    }

    static void dispatch(MinecraftClient client, ChatMessage message, long nowMillis) {
        UtilityMessageBus.DispatchDecision decision = UtilityManagerClient.messageBus().publish(message, nowMillis);

        if (decision.showInChat() && !decision.duplicate() && client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(Text.literal(
                    ChatPresentationFormatter.chatLine(message, 1)
            ));
        }

        if (decision.showToast()) {
            UtilityNotifications.show(
                    ChatPresentationFormatter.toastTitle(message),
                    message.text()
            );
        }

        if (decision.writeConsole()) {
            String line = ChatPresentationFormatter.consoleLine(message);
            switch (message.type()) {
                case ERROR -> LOGGER.error(line);
                case WARNING -> LOGGER.warn(line);
                case SYSTEM -> LOGGER.info(line);
                case CHAT, GAME -> LOGGER.debug(line);
            }
        }
    }
}

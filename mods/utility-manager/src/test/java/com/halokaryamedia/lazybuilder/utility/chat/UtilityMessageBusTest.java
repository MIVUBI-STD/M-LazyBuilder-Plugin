package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class UtilityMessageBusTest {
    @Test
    void playerChatOnlyUsesChatSurface() {
        UtilityMessageBus bus = new UtilityMessageBus(new ChatDeduplicator(Duration.ofSeconds(8)));
        UtilityMessageBus.DispatchDecision decision = bus.publish(
                new ChatMessage(ChatMessageType.CHAT, "Marcel", "hello", ""),
                1000
        );

        assertTrue(decision.showInChat());
        assertFalse(decision.showToast());
        assertFalse(decision.writeConsole());
        assertFalse(decision.duplicate());
    }

    @Test
    void warningUsesChatToastAndConsoleOnlyOnceForToast() {
        UtilityMessageBus bus = new UtilityMessageBus(new ChatDeduplicator(Duration.ofSeconds(8)));
        ChatMessage warning = new ChatMessage(
                ChatMessageType.WARNING,
                "Terraform",
                "Keybind conflict",
                "key.lazybuilder.terraform.toggle_panel"
        );

        UtilityMessageBus.DispatchDecision first = bus.publish(warning, 1000);
        UtilityMessageBus.DispatchDecision second = bus.publish(warning, 1200);

        assertTrue(first.showInChat());
        assertTrue(first.showToast());
        assertTrue(first.writeConsole());
        assertFalse(first.duplicate());

        assertTrue(second.showInChat());
        assertFalse(second.showToast());
        assertTrue(second.writeConsole());
        assertTrue(second.duplicate());
        assertEquals(2, second.duplicateCount());
    }

    @Test
    void sessionResetClearsDuplicateState() {
        UtilityMessageBus bus = new UtilityMessageBus(new ChatDeduplicator(Duration.ofSeconds(8)));
        ChatMessage message = new ChatMessage(ChatMessageType.SYSTEM, "Clockwork", "Server ready", "");

        bus.publish(message, 1000);
        assertTrue(bus.publish(message, 1100).duplicate());

        bus.clearSession();
        assertFalse(bus.publish(message, 1200).duplicate());
    }
}

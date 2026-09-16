package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatRoutingPolicyTest {
    @Test
    void playerChatIsNeverDeduplicatedByDefault() {
        assertFalse(ChatRoutingPolicy.eligibleForDeduplication(ChatMessageType.CHAT));
    }

    @Test
    void machineGeneratedLowRiskCategoriesMayBeDeduplicated() {
        assertTrue(ChatRoutingPolicy.eligibleForDeduplication(ChatMessageType.GAME));
        assertTrue(ChatRoutingPolicy.eligibleForDeduplication(ChatMessageType.SYSTEM));
        assertTrue(ChatRoutingPolicy.eligibleForDeduplication(ChatMessageType.WARNING));
    }

    @Test
    void warningAndErrorKeepDiagnosticDetail() {
        assertTrue(ChatRoutingPolicy.keepDiagnosticDetail(ChatMessageType.WARNING));
        assertTrue(ChatRoutingPolicy.keepDiagnosticDetail(ChatMessageType.ERROR));
        assertFalse(ChatRoutingPolicy.keepDiagnosticDetail(ChatMessageType.CHAT));
    }
}

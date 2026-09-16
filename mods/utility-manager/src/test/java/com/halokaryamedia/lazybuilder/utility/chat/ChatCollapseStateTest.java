package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ChatCollapseStateTest {
    @Test
    void consecutiveJoinMessagesCollapseInsideWindow() {
        ChatCollapseState state = new ChatCollapseState(Duration.ofSeconds(8));

        ChatCollapseState.Decision first = state.accept("02:10  Berchman joined", 1000);
        ChatCollapseState.Decision second = state.accept("02:10  Berchman joined", 1500);
        ChatCollapseState.Decision third = state.accept("02:10  Berchman joined", 2000);

        assertFalse(first.collapse());
        assertTrue(second.collapse());
        assertEquals(2, second.count());
        assertEquals("Berchman joined ×3", third.collapsedText());
    }

    @Test
    void playerChatNeverCollapses() {
        ChatCollapseState state = new ChatCollapseState(Duration.ofSeconds(8));

        assertFalse(state.accept("02:10  Marcel  hello", 1000).eligible());
        assertFalse(state.accept("02:10  Marcel  hello", 1100).collapse());
    }

    @Test
    void unrelatedMessageBreaksConsecutiveSequence() {
        ChatCollapseState state = new ChatCollapseState(Duration.ofSeconds(8));

        state.accept("Berchman joined", 1000);
        state.accept("Marcel  hello", 1100);
        assertFalse(state.accept("Berchman joined", 1200).collapse());
    }

    @Test
    void previousCounterAndExternalTimestampAreNormalized() {
        assertEquals(
                "⚠ Terraform  Keybind conflict",
                ChatCollapseState.normalize("[02:10:33] ⚠ Terraform  Keybind conflict ×7")
        );
    }

    @Test
    void duplicateWindowExpires() {
        ChatCollapseState state = new ChatCollapseState(Duration.ofSeconds(1));
        state.accept("Gamemode → Spectator", 1000);
        assertFalse(state.accept("Gamemode → Spectator", 2101).collapse());
    }
}

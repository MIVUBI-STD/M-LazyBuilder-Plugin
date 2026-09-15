package com.halokaryamedia.lazybuilder.utility.chat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatDraftStateTest {
    @AfterEach
    void clearState() {
        ChatDraftState.clear();
    }

    @Test
    void savesAndRestoresDraftWithinSession() {
        ChatDraftState.save("unfinished message");
        assertEquals("unfinished message", ChatDraftState.get());
    }

    @Test
    void nullDraftNormalizesToEmptyString() {
        ChatDraftState.save(null);
        assertEquals("", ChatDraftState.get());
    }

    @Test
    void clearRemovesPreviousDraft() {
        ChatDraftState.save("temporary");
        ChatDraftState.clear();
        assertEquals("", ChatDraftState.get());
    }
}

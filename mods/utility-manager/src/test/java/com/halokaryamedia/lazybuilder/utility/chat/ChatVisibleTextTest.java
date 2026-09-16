package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatVisibleTextTest {
    @Test
    void copiedMessageDropsPresentationTimestampOnly() {
        assertEquals("Berchman  hello", ChatVisibleText.withoutTimestamp("19:34  Berchman  hello"));
        assertEquals("no timestamp", ChatVisibleText.withoutTimestamp("no timestamp"));
    }
}

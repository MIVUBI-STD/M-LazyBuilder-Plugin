package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ChatTimestampFormatterTest {
    @Test
    void formatsLocalMinuteWithoutSeconds() {
        long timestamp = Instant.parse("2026-09-16T12:34:56Z").toEpochMilli();
        assertEquals("19:34", ChatTimestampFormatter.format(timestamp, ZoneId.of("Asia/Jakarta")));
    }
}

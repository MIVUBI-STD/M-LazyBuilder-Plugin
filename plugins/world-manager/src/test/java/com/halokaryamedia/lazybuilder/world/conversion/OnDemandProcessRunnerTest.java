package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnDemandProcessRunnerTest {
    @Test
    void boundedTailKeepsOnlyNewestBytesWithoutGrowing() {
        OnDemandProcessRunner.BoundedTailBuffer buffer =
                new OnDemandProcessRunner.BoundedTailBuffer(4);

        byte[] first = "abc".getBytes(StandardCharsets.UTF_8);
        byte[] second = "de".getBytes(StandardCharsets.UTF_8);
        buffer.append(first, 0, first.length);
        buffer.append(second, 0, second.length);

        assertArrayEquals("bcde".getBytes(StandardCharsets.UTF_8), buffer.snapshot());
        assertTrue(buffer.truncated());
        assertTrue(buffer.text("stderr").startsWith("[stderr truncated]\n"));
    }

    @Test
    void boundedTailDoesNotClaimTruncationWithinCapacity() {
        OnDemandProcessRunner.BoundedTailBuffer buffer =
                new OnDemandProcessRunner.BoundedTailBuffer(8);

        byte[] payload = "clean".getBytes(StandardCharsets.UTF_8);
        buffer.append(payload, 0, payload.length);

        assertArrayEquals(payload, buffer.snapshot());
        assertFalse(buffer.truncated());
    }
}

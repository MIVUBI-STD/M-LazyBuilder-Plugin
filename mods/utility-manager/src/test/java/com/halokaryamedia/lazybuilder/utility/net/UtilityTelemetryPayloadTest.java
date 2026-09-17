package com.halokaryamedia.lazybuilder.utility.net;

import com.halokaryamedia.lazybuilder.utility.telemetry.UtilityTelemetryWireProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class UtilityTelemetryPayloadTest {
    @Test
    void minimumAndMaximumPayloadLengthsAreAccepted() {
        assertDoesNotThrow(() -> UtilityTelemetryPayload.requireValidPayloadLength(2));
        assertDoesNotThrow(() -> UtilityTelemetryPayload.requireValidPayloadLength(
                UtilityTelemetryWireProtocol.MAX_MESSAGE_BYTES
        ));
    }

    @Test
    void payloadLengthIsRejectedBeforeAllocationWhenOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () -> UtilityTelemetryPayload.requireValidPayloadLength(1));
        assertThrows(
                IllegalArgumentException.class,
                () -> UtilityTelemetryPayload.requireValidPayloadLength(
                        UtilityTelemetryWireProtocol.MAX_MESSAGE_BYTES + 1
                )
        );
    }
}

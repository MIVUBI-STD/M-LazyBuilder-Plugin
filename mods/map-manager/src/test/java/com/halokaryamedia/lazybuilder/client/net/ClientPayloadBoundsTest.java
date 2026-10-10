package com.halokaryamedia.lazybuilder.client.net;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import com.halokaryamedia.lazybuilder.world.transfer.TransferWireProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Payload adapters reject invalid sizes before copying or allocating bytes. */
final class ClientPayloadBoundsTest {
    @Test
    void mapPayloadAcceptsLimitsAndRejectsOversizeOrEmpty() {
        assertDoesNotThrow(() -> MapPayload.requireValidPayloadLength(1));
        assertDoesNotThrow(() -> MapPayload.requireValidPayloadLength(MapActionWireProtocol.MAX_MESSAGE_BYTES));
        assertThrows(IllegalArgumentException.class, () -> MapPayload.requireValidPayloadLength(0));
        assertThrows(IllegalArgumentException.class, () -> MapPayload.requireValidPayloadLength(MapActionWireProtocol.MAX_MESSAGE_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> MapPayload.requireValidPayloadLength(Integer.MAX_VALUE));
    }

    @Test
    void worldPayloadAcceptsLimitsAndRejectsOversizeOrEmpty() {
        assertDoesNotThrow(() -> WorldPayload.requireValidPayloadLength(1));
        assertDoesNotThrow(() -> WorldPayload.requireValidPayloadLength(WorldControlWireProtocol.MAX_MESSAGE_BYTES));
        assertThrows(IllegalArgumentException.class, () -> WorldPayload.requireValidPayloadLength(0));
        assertThrows(IllegalArgumentException.class, () -> WorldPayload.requireValidPayloadLength(WorldControlWireProtocol.MAX_MESSAGE_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> WorldPayload.requireValidPayloadLength(Integer.MAX_VALUE));
    }

    @Test
    void transferPayloadAcceptsLimitsAndRejectsOversizeOrEmpty() {
        assertDoesNotThrow(() -> TransferPayload.requireValidPayloadLength(1));
        assertDoesNotThrow(() -> TransferPayload.requireValidPayloadLength(TransferWireProtocol.MAX_MESSAGE_BYTES));
        assertThrows(IllegalArgumentException.class, () -> TransferPayload.requireValidPayloadLength(0));
        assertThrows(IllegalArgumentException.class, () -> TransferPayload.requireValidPayloadLength(TransferWireProtocol.MAX_MESSAGE_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> TransferPayload.requireValidPayloadLength(Integer.MAX_VALUE));
    }
}

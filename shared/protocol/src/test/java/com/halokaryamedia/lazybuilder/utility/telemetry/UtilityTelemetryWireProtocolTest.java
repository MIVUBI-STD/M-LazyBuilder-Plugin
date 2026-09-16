package com.halokaryamedia.lazybuilder.utility.telemetry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UtilityTelemetryWireProtocolTest {
    @Test
    void subscribeRoundTrips() throws IOException {
        byte[] payload = UtilityTelemetryWireProtocol.subscribe();
        assertInstanceOf(UtilityTelemetryWireProtocol.Subscribe.class,
                UtilityTelemetryWireProtocol.decodeRequest(payload));
    }

    @Test
    void snapshotRoundTrips() throws IOException {
        byte[] payload = UtilityTelemetryWireProtocol.snapshot("TanaSamawa", 18.5D, 1024L, 4096L);
        UtilityTelemetryWireProtocol.Snapshot snapshot = assertInstanceOf(
                UtilityTelemetryWireProtocol.Snapshot.class,
                UtilityTelemetryWireProtocol.decodeResponse(payload)
        );

        assertEquals("TanaSamawa", snapshot.worldName());
        assertEquals(18.5D, snapshot.cpuPercent());
        assertEquals(1024L, snapshot.usedMemoryBytes());
        assertEquals(4096L, snapshot.maxMemoryBytes());
    }

    @Test
    void unsupportedCpuCanRemainUnavailable() throws IOException {
        UtilityTelemetryWireProtocol.Snapshot snapshot = assertInstanceOf(
                UtilityTelemetryWireProtocol.Snapshot.class,
                UtilityTelemetryWireProtocol.decodeResponse(
                        UtilityTelemetryWireProtocol.snapshot("world", Double.NaN, 1L, 2L)
                )
        );
        assertTrue(Double.isNaN(snapshot.cpuPercent()));
    }

    @Test
    void trailingOrOversizedPayloadIsRejected() {
        byte[] trailing = Arrays.copyOf(UtilityTelemetryWireProtocol.subscribe(), 3);
        trailing[2] = 1;
        assertThrows(IOException.class, () -> UtilityTelemetryWireProtocol.decodeRequest(trailing));

        byte[] oversized = new byte[UtilityTelemetryWireProtocol.MAX_MESSAGE_BYTES + 1];
        assertThrows(IOException.class, () -> UtilityTelemetryWireProtocol.decodeRequest(oversized));
    }
}

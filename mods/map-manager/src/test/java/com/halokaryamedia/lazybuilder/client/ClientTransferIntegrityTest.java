package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.transfer.TransferDescriptor;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientTransferIntegrityTest {
    @Test
    void computesExactChunkLengthsIncludingFinalPartialChunk() {
        TransferDescriptor descriptor = new TransferDescriptor(
                UUID.randomUUID(), "world.zip", 100L, 24, 5, "a".repeat(64));

        assertEquals(24, ClientTransferController.expectedDownloadChunkLength(descriptor, 0));
        assertEquals(24, ClientTransferController.expectedDownloadChunkLength(descriptor, 3));
        assertEquals(4, ClientTransferController.expectedDownloadChunkLength(descriptor, 4));
        assertThrows(IllegalArgumentException.class,
                () -> ClientTransferController.expectedDownloadChunkLength(descriptor, 5));
    }
}

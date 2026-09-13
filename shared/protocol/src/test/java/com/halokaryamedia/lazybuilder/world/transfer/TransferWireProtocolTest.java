package com.halokaryamedia.lazybuilder.world.transfer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferWireProtocolTest {
    @Test
    void requestRoundTripsAcrossSharedCodec() throws Exception {
        var begin = new TransferWireProtocol.BeginUpload("build.zip", 42L, "a".repeat(64));
        var decodedBegin = (TransferWireProtocol.BeginUpload) TransferWireProtocol.decodeRequest(
                TransferWireProtocol.encodeRequest(begin));
        assertEquals(begin, decodedBegin);

        UUID id = UUID.randomUUID();
        byte[] data = new byte[]{1, 2, 3};
        var chunk = new TransferWireProtocol.UploadChunk(id, 0, data);
        var decodedChunk = (TransferWireProtocol.UploadChunk) TransferWireProtocol.decodeRequest(
                TransferWireProtocol.encodeRequest(chunk));
        assertEquals(id, decodedChunk.sessionId());
        assertEquals(0, decodedChunk.chunkIndex());
        assertArrayEquals(data, decodedChunk.data());
        byte[] returned = decodedChunk.data();
        returned[0] = 99;
        assertArrayEquals(data, decodedChunk.data());
    }

    @Test
    void responseRoundTripsForClientConsumption() throws Exception {
        UUID id = UUID.randomUUID();
        TransferDescriptor descriptor = new TransferDescriptor(
                id, "build.zip", 100L, 24, 5, "b".repeat(64));
        var accepted = (TransferWireProtocol.DownloadAccepted) TransferWireProtocol.decodeResponse(
                TransferWireProtocol.downloadAccepted(descriptor));
        assertEquals(descriptor, accepted.descriptor());

        byte[] data = new byte[]{4, 5, 6};
        var chunk = (TransferWireProtocol.DownloadChunkData) TransferWireProtocol.decodeResponse(
                TransferWireProtocol.downloadChunk(id, 2, data, true));
        assertEquals(id, chunk.sessionId());
        assertEquals(2, chunk.chunkIndex());
        assertTrue(chunk.last());
        assertArrayEquals(data, chunk.data());
    }

    @Test
    void rejectsUnknownVersionAndTrailingBytesOnBothDirections() throws Exception {
        byte[] request = TransferWireProtocol.encodeRequest(new TransferWireProtocol.BeginDownload("build.zip"));
        byte[] wrongVersion = request.clone();
        wrongVersion[0] = (byte) (TransferWireProtocol.VERSION == 1 ? 2 : 1);
        assertThrows(IOException.class, () -> TransferWireProtocol.decodeRequest(wrongVersion));

        byte[] response = TransferWireProtocol.ack(TransferWireProtocol.opcode(
                new TransferWireProtocol.FinishDownload(UUID.randomUUID())));
        byte[] trailing = Arrays.copyOf(response, response.length + 1);
        assertThrows(IOException.class, () -> TransferWireProtocol.decodeResponse(trailing));
    }

    @Test
    void responseChunkRemainsInsideWireCeiling() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] data = new byte[TransferWireProtocol.MAX_CHUNK_BYTES];
        byte[] payload = TransferWireProtocol.downloadChunk(id, 0, data, false);
        assertTrue(payload.length <= TransferWireProtocol.MAX_MESSAGE_BYTES);
        assertEquals(4, TransferWireProtocol.PIPELINE_WINDOW);
    }
}

package com.halokaryamedia.lazybuilder.world.transfer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferWireProtocolTest {
    @Test
    void decodesBeginUploadWithVersionedLengthPrefixedStrings() throws Exception {
        byte[] payload = request(1, out -> {
            string(out, "build.zip");
            out.writeLong(42L);
            string(out, "a".repeat(64));
        });

        var request = (TransferWireProtocol.BeginUpload) TransferWireProtocol.decodeRequest(payload);
        assertEquals("build.zip", request.fileName());
        assertEquals(42L, request.totalBytes());
        assertEquals("a".repeat(64), request.sha256());
    }

    @Test
    void decodesUploadChunkWithoutSharingMutableInput() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] data = new byte[]{1, 2, 3};
        byte[] payload = request(2, out -> {
            uuid(out, id);
            out.writeInt(0);
            out.writeInt(data.length);
            out.write(data);
        });

        var request = (TransferWireProtocol.UploadChunk) TransferWireProtocol.decodeRequest(payload);
        assertEquals(id, request.sessionId());
        assertEquals(0, request.chunkIndex());
        assertArrayEquals(data, request.data());
        byte[] returned = request.data();
        returned[0] = 99;
        assertArrayEquals(data, request.data());
    }

    @Test
    void rejectsUnknownVersionAndTrailingBytes() throws Exception {
        byte[] wrongVersion = requestWithVersion(2, 5, out -> string(out, "build.zip"));
        assertThrows(java.io.IOException.class, () -> TransferWireProtocol.decodeRequest(wrongVersion));

        byte[] valid = request(5, out -> string(out, "build.zip"));
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(java.io.IOException.class, () -> TransferWireProtocol.decodeRequest(trailing));
    }

    @Test
    void responseChunkRemainsInsideWireCeiling() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] data = new byte[TransferWireProtocol.MAX_CHUNK_BYTES];
        byte[] payload = TransferWireProtocol.downloadChunk(
                id,
                new TransferSessionService.DownloadChunk(0, data, false)
        );
        org.junit.jupiter.api.Assertions.assertTrue(payload.length <= TransferWireProtocol.MAX_MESSAGE_BYTES);
    }

    private static byte[] request(int opcode, Writer writer) throws Exception {
        return requestWithVersion(TransferWireProtocol.VERSION, opcode, writer);
    }

    private static byte[] requestWithVersion(int version, int opcode, Writer writer) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeByte(version);
            out.writeByte(opcode);
            writer.write(out);
        }
        return buffer.toByteArray();
    }

    private static void string(DataOutputStream out, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void uuid(DataOutputStream out, UUID id) throws Exception {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    @FunctionalInterface
    private interface Writer { void write(DataOutputStream out) throws Exception; }
}

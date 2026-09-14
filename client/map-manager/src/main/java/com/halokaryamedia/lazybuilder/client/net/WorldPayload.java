package com.halokaryamedia.lazybuilder.client.net;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Objects;

public record WorldPayload(byte[] bytes) implements CustomPayload {
    public static final Id<WorldPayload> ID = new Id<>(Identifier.of("lazybuilder", "world"));
    public static final PacketCodec<RegistryByteBuf, WorldPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> buf.writeBytes(payload.bytes),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new WorldPayload(bytes);
            }
    );

    public WorldPayload {
        bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
        if (bytes.length < 1 || bytes.length > WorldControlWireProtocol.MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Invalid LazyBuilder world-control payload size");
        }
    }

    @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}

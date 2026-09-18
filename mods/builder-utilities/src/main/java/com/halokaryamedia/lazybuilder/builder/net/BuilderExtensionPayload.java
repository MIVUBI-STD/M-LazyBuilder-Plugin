package com.halokaryamedia.lazybuilder.builder.net;

import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionTransport;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Objects;

/** Opaque bounded carrier for the shared Builder extension wire protocol. */
public record BuilderExtensionPayload(byte[] bytes) implements CustomPayload {
    public static final Id<BuilderExtensionPayload> ID =
            new Id<>(Identifier.of("lazybuilder", "builder_ext"));

    public static final PacketCodec<RegistryByteBuf, BuilderExtensionPayload> CODEC =
            PacketCodec.ofStatic(
                    (buf, payload) -> buf.writeBytes(payload.bytes),
                    buf -> {
                        byte[] bytes = new byte[buf.readableBytes()];
                        buf.readBytes(bytes);
                        return new BuilderExtensionPayload(bytes);
                    }
            );

    public BuilderExtensionPayload {
        bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
        if (bytes.length < 2
                || bytes.length > BuilderExtensionTransport.MAX_PLUGIN_MESSAGE_BYTES) {
            throw new IllegalArgumentException("invalid Builder extension payload size");
        }
    }

    @Override public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    @Override public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

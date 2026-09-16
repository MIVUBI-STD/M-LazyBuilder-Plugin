package com.halokaryamedia.lazybuilder.utility.net;

import com.halokaryamedia.lazybuilder.utility.telemetry.UtilityTelemetryWireProtocol;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Objects;

/** Raw bounded payload wrapper for Compact Debug server telemetry. */
public record UtilityTelemetryPayload(byte[] bytes) implements CustomPayload {
    public static final Id<UtilityTelemetryPayload> ID =
            new Id<>(Identifier.of("lazybuilder", "utility_telemetry"));
    public static final PacketCodec<RegistryByteBuf, UtilityTelemetryPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> buf.writeBytes(payload.bytes),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new UtilityTelemetryPayload(bytes);
            }
    );

    public UtilityTelemetryPayload {
        bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
        if (bytes.length < 2 || bytes.length > UtilityTelemetryWireProtocol.MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Invalid LazyBuilder utility telemetry payload size");
        }
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

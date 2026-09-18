package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.mutation.HistoryExtensionMutationTarget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Read-only client-world reconciliation adapter for BIOME extension frames. */
public final class AxiomBiomeExtensionReadTarget implements HistoryExtensionMutationTarget {
    private final ClientWorld world;

    public AxiomBiomeExtensionReadTarget(ClientWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public byte[] read(String typeId, int chunkX, int chunkZ, long localKey) {
        if (!HistoryExtensionTypes.BIOME.equals(typeId)) {
            throw new IllegalArgumentException("Unsupported client extension type: " + typeId);
        }
        int x = Math.addExact(Math.multiplyExact(chunkX, 16), LocalBlockPosition.localX(localKey));
        int z = Math.addExact(Math.multiplyExact(chunkZ, 16), LocalBlockPosition.localZ(localKey));
        int y = LocalBlockPosition.y(localKey);
        String biome = world.getBiome(new BlockPos(x, y, z))
                .getKey()
                .map(key -> key.getValue().toString())
                .orElseThrow(() -> new IllegalStateException(
                        "Biome at " + x + "," + y + "," + z + " has no registry key"));
        return biome.getBytes(StandardCharsets.UTF_8);
    }
}

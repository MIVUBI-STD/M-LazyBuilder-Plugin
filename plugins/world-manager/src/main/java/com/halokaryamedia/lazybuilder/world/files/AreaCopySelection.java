package com.halokaryamedia.lazybuilder.world.files;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Inclusive chunk rectangle plus cooperative cancellation for filesystem-level area staging. */
public final class AreaCopySelection {
    private static final int REGION_CHUNKS = 32;
    private static final Set<String> VANILLA_DIMENSIONS = Set.of(
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end"
    );
    private static final BooleanSupplier NEVER_CANCELLED = () -> false;

    private final String dimensionId;
    private final int minChunkX;
    private final int minChunkZ;
    private final int maxChunkX;
    private final int maxChunkZ;
    private final BooleanSupplier cancellationRequested;

    public AreaCopySelection(
            String dimensionId,
            int minChunkX,
            int minChunkZ,
            int maxChunkX,
            int maxChunkZ
    ) {
        this(dimensionId, minChunkX, minChunkZ, maxChunkX, maxChunkZ, NEVER_CANCELLED);
    }

    public AreaCopySelection(
            String dimensionId,
            int minChunkX,
            int minChunkZ,
            int maxChunkX,
            int maxChunkZ,
            BooleanSupplier cancellationRequested
    ) {
        if (dimensionId == null || dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
        String normalizedDimension = dimensionId.strip();
        if (!VANILLA_DIMENSIONS.contains(normalizedDimension)) {
            throw new IllegalArgumentException("Area snapshot does not support dimension: " + normalizedDimension);
        }
        if (minChunkX > maxChunkX) throw new IllegalArgumentException("minChunkX must not exceed maxChunkX");
        if (minChunkZ > maxChunkZ) throw new IllegalArgumentException("minChunkZ must not exceed maxChunkZ");

        this.dimensionId = normalizedDimension;
        this.minChunkX = minChunkX;
        this.minChunkZ = minChunkZ;
        this.maxChunkX = maxChunkX;
        this.maxChunkZ = maxChunkZ;
        this.cancellationRequested = Objects.requireNonNull(cancellationRequested, "cancellationRequested");
    }

    public String dimensionId() { return dimensionId; }
    public int minChunkX() { return minChunkX; }
    public int minChunkZ() { return minChunkZ; }
    public int maxChunkX() { return maxChunkX; }
    public int maxChunkZ() { return maxChunkZ; }

    public int minRegionX() { return Math.floorDiv(minChunkX, REGION_CHUNKS); }
    public int minRegionZ() { return Math.floorDiv(minChunkZ, REGION_CHUNKS); }
    public int maxRegionX() { return Math.floorDiv(maxChunkX, REGION_CHUNKS); }
    public int maxRegionZ() { return Math.floorDiv(maxChunkZ, REGION_CHUNKS); }

    public boolean includesRegion(int regionX, int regionZ) {
        return regionX >= minRegionX() && regionX <= maxRegionX()
                && regionZ >= minRegionZ() && regionZ <= maxRegionZ();
    }

    public boolean isCancellationRequested() {
        return cancellationRequested.getAsBoolean();
    }

    void requireActive() throws IOException {
        if (isCancellationRequested()) {
            throw new IOException("Selected area snapshot was cancelled");
        }
    }
}

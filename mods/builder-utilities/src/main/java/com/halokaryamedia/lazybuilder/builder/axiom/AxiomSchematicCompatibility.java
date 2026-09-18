package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.SchematicDataVersionPolicy;
import com.halokaryamedia.lazybuilder.builder.structure.SpongeSchematicImport;
import net.minecraft.client.world.ClientWorld;

import java.util.Objects;

/** Current-registry compatibility validation for imported schematic block states. */
public final class AxiomSchematicCompatibility {
    private AxiomSchematicCompatibility() {}

    public static SchematicDataVersionPolicy.Compatibility validateForApply(
            SpongeSchematicImport imported,
            ClientWorld world
    ) {
        Objects.requireNonNull(imported, "imported");
        Objects.requireNonNull(world, "world");
        SchematicDataVersionPolicy.requireNotFuture(imported.dataVersion());
        var compatibility = SchematicDataVersionPolicy.classify(imported.dataVersion());
        if (compatibility
                == SchematicDataVersionPolicy.Compatibility.LEGACY_REQUIRES_VALIDATION
                && (imported.snapshot().biomeCount() != 0
                || imported.snapshot().entityCount() != 0
                || imported.snapshot().blockEntityCount() != 0)) {
            throw new IllegalArgumentException(
                    "Legacy schematic DataVersion " + imported.dataVersion()
                            + " contains auxiliary BIOME/ENTITY/BLOCK_ENTITY payloads. "
                            + "LazyBuilder preserves them but does not apply them until a "
                            + "verified per-type DataFix migration is available.");
        }

        AxiomBlockStateCodec codec = new AxiomBlockStateCodec(world);
        for (var block : imported.snapshot().blocks()) {
            try {
                codec.decode(block.blockState());
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException(
                        "Schematic block state is not valid in the current runtime: "
                                + block.blockState()
                                + " (DataVersion=" + imported.dataVersion() + ")",
                        failure);
            }
        }
        return compatibility;
    }

    public static String status(SpongeSchematicImport imported) {
        var compatibility = SchematicDataVersionPolicy.classify(imported.dataVersion());
        return switch (compatibility) {
            case CURRENT -> "current";
            case LEGACY_REQUIRES_VALIDATION -> "legacy-validated-on-apply";
            case FUTURE_UNSUPPORTED -> "future-unsupported";
        };
    }
}

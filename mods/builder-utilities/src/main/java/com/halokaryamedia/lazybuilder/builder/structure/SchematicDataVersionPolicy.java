package com.halokaryamedia.lazybuilder.builder.structure;

import net.minecraft.SharedConstants;

/**
 * Explicit compatibility policy for Sponge schematic DataVersion.
 *
 * <p>Newer-than-runtime schematics are rejected. Older schematics remain importable,
 * but callers must validate every canonical block-state string against the current
 * registry before world mutation. Opaque entity/block-entity payloads are preserved
 * byte-for-byte; this class does not pretend to datafix them.</p>
 */
public final class SchematicDataVersionPolicy {
    private SchematicDataVersionPolicy() {}

    public static Compatibility classify(int dataVersion) {
        if (dataVersion < 0) {
            throw new IllegalArgumentException("DataVersion must be >= 0");
        }
        int current = currentDataVersion();
        if (dataVersion > current) return Compatibility.FUTURE_UNSUPPORTED;
        if (dataVersion < current) return Compatibility.LEGACY_REQUIRES_VALIDATION;
        return Compatibility.CURRENT;
    }

    public static void requireNotFuture(int dataVersion) {
        Compatibility compatibility = classify(dataVersion);
        if (compatibility == Compatibility.FUTURE_UNSUPPORTED) {
            throw new IllegalArgumentException(
                    "Schematic DataVersion " + dataVersion
                            + " is newer than runtime DataVersion " + currentDataVersion());
        }
    }

    public static int currentDataVersion() {
        return SharedConstants.getGameVersion().getSaveVersion().getId();
    }

    public enum Compatibility {
        CURRENT,
        LEGACY_REQUIRES_VALIDATION,
        FUTURE_UNSUPPORTED
    }
}

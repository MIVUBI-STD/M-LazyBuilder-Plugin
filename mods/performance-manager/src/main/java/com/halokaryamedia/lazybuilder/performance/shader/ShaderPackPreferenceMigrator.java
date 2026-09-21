package com.halokaryamedia.lazybuilder.performance.shader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns persisted shader-pack identity migration independently from runtime orchestration. */
final class ShaderPackPreferenceMigrator {
    private ShaderPackPreferenceMigrator() {
    }

    static ShaderRuntimePreferences migrate(
            ShaderRuntimePreferences persisted,
            List<ShaderPackDescriptor> packs
    ) {
        ShaderRuntimePreferences migrated =
                persisted == null ? ShaderRuntimePreferences.defaults() : persisted;
        if (packs == null || packs.isEmpty()) return migrated;

        Map<String, Integer> legacyCounts = new LinkedHashMap<>();
        for (ShaderPackDescriptor pack : packs) {
            String legacyId = ShaderPackCatalog.legacyId(pack);
            if (!legacyId.isBlank()) legacyCounts.merge(legacyId, 1, Integer::sum);
        }

        for (ShaderPackDescriptor pack : packs) {
            String legacyId = ShaderPackCatalog.legacyId(pack);
            if (!legacyId.isBlank()
                    && !legacyId.equals(pack.id())
                    && legacyCounts.getOrDefault(legacyId, 0) == 1) {
                migrated = migrated.migratePackId(legacyId, pack.id());
            }

            String sourceDerivedId = ShaderPackCatalog.sourceDerivedId(pack);
            if (!sourceDerivedId.isBlank() && !sourceDerivedId.equals(pack.id())) {
                migrated = migrated.migratePackId(sourceDerivedId, pack.id());
            }
        }
        return migrated;
    }
}

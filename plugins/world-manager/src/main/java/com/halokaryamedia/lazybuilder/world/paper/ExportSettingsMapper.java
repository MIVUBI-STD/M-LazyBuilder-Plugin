package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.application.WorldDifficulty;
import com.halokaryamedia.lazybuilder.world.application.WorldExportOptions;
import com.halokaryamedia.lazybuilder.world.application.WorldGameMode;
import com.halokaryamedia.lazybuilder.world.application.WorldWeather;
import com.halokaryamedia.lazybuilder.world.export.ExportSettingsWire;

import java.util.Locale;
import java.util.Objects;

/** Single Paper-side mapping from shared export wire values to domain options. */
final class ExportSettingsMapper {
    private ExportSettingsMapper() {}

    static WorldExportOptions toOptions(ExportSettingsWire.Settings settings) {
        Objects.requireNonNull(settings, "settings");
        WorldGameMode gameMode = parseOptional(settings.gameMode(), WorldGameMode.class);
        WorldDifficulty difficulty = parseOptional(settings.difficulty(), WorldDifficulty.class);
        WorldWeather weather = parseOptional(settings.weather(), WorldWeather.class);
        return new WorldExportOptions(
                gameMode,
                difficulty,
                settings.spawnX(),
                settings.spawnY(),
                settings.spawnZ(),
                settings.timeOfDayTicks(),
                weather,
                settings.gameRules(),
                settings.optimizeOutput()
        );
    }

    private static <E extends Enum<E>> E parseOptional(String raw, Class<E> type) {
        if (raw == null || raw.isBlank()) return null;
        return Enum.valueOf(type, raw.strip().toUpperCase(Locale.ROOT));
    }
}

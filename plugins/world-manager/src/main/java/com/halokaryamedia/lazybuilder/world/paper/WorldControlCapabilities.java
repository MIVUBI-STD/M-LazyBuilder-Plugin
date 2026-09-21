package com.halokaryamedia.lazybuilder.world.paper;

import java.util.List;

final class WorldControlCapabilities {
    private static final List<String> ADVERTISED = List.of(
            "world.list",
            "world.create",
            "world.settings",
            "world.tasks",
            "world.archive",
            "world.restore",
            "world.backup",
            "world.duplicate",
            "world.export",
            "world.import",
            "world.delete"
    );

    private WorldControlCapabilities() {}

    static List<String> advertised() {
        return ADVERTISED;
    }
}

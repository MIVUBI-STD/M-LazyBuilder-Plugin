package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;

import java.util.Objects;
import java.util.function.Predicate;

/** Central permission gate for map requests before any domain action is dispatched. */
final class MapActionPermissionPolicy {
    private MapActionPermissionPolicy() {}

    static String denial(
            MapActionWireProtocol.Request request,
            Predicate<String> hasPermission
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(hasPermission, "hasPermission");

        return switch (request) {
            case MapActionWireProtocol.CurrentWorldRequest ignored ->
                    hasPermission.test(WorldPermissionNodes.TELEPORT)
                            || hasPermission.test(WorldPermissionNodes.MANAGE)
                            ? null
                            : "Missing LazyBuilder world permission";
            case MapActionWireProtocol.TeleportLocation ignored ->
                    hasPermission.test(WorldPermissionNodes.TELEPORT)
                            ? null
                            : "Missing permission: " + WorldPermissionNodes.TELEPORT;
            case MapActionWireProtocol.ExportArea ignored ->
                    hasPermission.test(WorldPermissionNodes.MANAGE)
                            ? null
                            : "Missing permission: " + WorldPermissionNodes.MANAGE;
        };
    }
}

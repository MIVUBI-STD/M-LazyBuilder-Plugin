package com.halokaryamedia.lazybuilder.client;

import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint. Keeps initialization event-driven and side-local. */
public final class LazyBuilderClient implements ClientModInitializer {
    private static final ClientTransferController TRANSFERS = new ClientTransferController();
    private static final ClientWorldController WORLDS = new ClientWorldController(TRANSFERS::downloadExport);
    private static final ClientMapController MAPS = new ClientMapController(TRANSFERS::downloadExport);

    @Override
    public void onInitializeClient() {
        new LazyBuilderClientNetworking(WORLDS, MAPS, TRANSFERS).register();
        WorldManagerClientUi.register(WORLDS, TRANSFERS, MAPS);
    }

    public static ClientWorldController worlds() { return WORLDS; }
    public static ClientMapController maps() { return MAPS; }
    public static ClientTransferController transfers() { return TRANSFERS; }
}

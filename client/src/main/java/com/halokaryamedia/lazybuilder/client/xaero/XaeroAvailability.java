package com.halokaryamedia.lazybuilder.client.xaero;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional Xaero boundary. Missing Xaero never disables core LazyBuilder networking. */
public final class XaeroAvailability {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Xaero");
    public static final String MOD_ID = "xaeroworldmap";

    private XaeroAvailability() {}

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static void logStatus() {
        if (isAvailable()) {
            LOGGER.info("Xaero World Map detected; LazyBuilder map integration boundary is available.");
        } else {
            LOGGER.info("Xaero World Map not detected; LazyBuilder core networking remains available.");
        }
    }
}

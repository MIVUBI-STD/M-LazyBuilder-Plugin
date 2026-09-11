package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;

/**
 * Runtime boundary used by World Manager application services.
 *
 * <p>Paper/Bukkit details stay behind the implementation of this interface so
 * lifecycle policy and registry behavior remain independently testable.</p>
 */
public interface WorldRuntimeGateway {
    void createNewWorld(WorldRecord world, BuildReadyPolicy policy);

    void rollbackCreatedWorld(WorldRecord world);
}

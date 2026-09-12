package com.halokaryamedia.lazybuilder;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Compatibility base for adapters that still accept the historical plugin type.
 *
 * <p>Lifecycle ownership has moved to {@link WorldManagerPlugin}. This class intentionally
 * contains no bootstrap state or behavior and can be removed once remaining adapter
 * constructor types are generalized to {@link JavaPlugin}.</p>
 */
@Deprecated(forRemoval = true)
public abstract class LazyBuilderPlugin extends JavaPlugin {
}

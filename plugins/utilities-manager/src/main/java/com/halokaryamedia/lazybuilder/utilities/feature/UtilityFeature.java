package com.halokaryamedia.lazybuilder.utilities.feature;

/**
 * Small lifecycle contract for one independently owned Utilities-Manager feature.
 *
 * <p>Features must keep their implementation inside their own feature package and must not
 * reach into another feature's internals.</p>
 */
public interface UtilityFeature {
    String id();

    void enable();

    void disable();
}

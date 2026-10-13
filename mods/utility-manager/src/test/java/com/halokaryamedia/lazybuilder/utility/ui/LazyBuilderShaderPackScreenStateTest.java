package com.halokaryamedia.lazybuilder.utility.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LazyBuilderShaderPackScreenStateTest {
    @Test
    void distinguishesCatalogLoadingFromEmptyOrFailedCatalog() {
        assertTrue(LazyBuilderShaderPackScreen.catalogPending("catalog-deferred"));
        assertTrue(LazyBuilderShaderPackScreen.catalogPending("catalog-refresh-queued"));
        assertTrue(LazyBuilderShaderPackScreen.catalogPending("catalog-refresh-pending"));
        assertTrue(LazyBuilderShaderPackScreen.catalogPending("catalog-scanning"));

        assertFalse(LazyBuilderShaderPackScreen.catalogPending("catalog-error"));
        assertFalse(LazyBuilderShaderPackScreen.catalogPending("source-ready"));
        assertFalse(LazyBuilderShaderPackScreen.catalogPending("external-owner"));
        assertFalse(LazyBuilderShaderPackScreen.catalogPending(null));
    }
}

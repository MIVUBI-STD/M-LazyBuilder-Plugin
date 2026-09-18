package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

/** Adds a stable world-scope envelope to operation ids before durable storage begins. */
public final class ScopedChangeSetStorage implements ChangeSetStorage {
    private final ChangeSetStorage delegate;
    private final Supplier<String> scopeSupplier;

    public ScopedChangeSetStorage(ChangeSetStorage delegate, Supplier<String> scopeSupplier) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.scopeSupplier = Objects.requireNonNull(scopeSupplier, "scopeSupplier");
    }

    @Override
    public HistoryStorageTier tier() {
        return delegate.tier();
    }

    @Override
    public ChangeSetWriter begin(String operationId) throws IOException {
        String scope = Objects.requireNonNull(scopeSupplier.get(), "world history scope");
        return delegate.begin(ScopedOperationIds.scope(scope, operationId));
    }
}

package com.halokaryamedia.lazybuilder.builder.material;

/**
 * @deprecated Use {@link CellularNoiseField}; retained as a source-compatible façade.
 */
@Deprecated
public final class CellNoiseField implements ScalarField {
    private final CellularNoiseField delegate;

    public CellNoiseField(double frequency, long channel) {
        this.delegate = new CellularNoiseField(frequency, channel);
    }

    @Override
    public double sample(MaterialContext context) {
        return delegate.sample(context);
    }
}

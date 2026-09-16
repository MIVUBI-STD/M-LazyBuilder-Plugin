package com.halokaryamedia.lazybuilder.terraform;

/** Public artistic control. Internal geometry derives detailed amplitudes from this level. */
public enum TerrainVariation {
    SOFT(0.55, 0.55, 0.50),
    NATURAL(1.00, 1.00, 1.00),
    DRAMATIC(1.45, 1.35, 1.25);

    private final double macro;
    private final double meso;
    private final double micro;

    TerrainVariation(double macro, double meso, double micro) {
        this.macro = macro;
        this.meso = meso;
        this.micro = micro;
    }

    public double macro() { return macro; }
    public double meso() { return meso; }
    public double micro() { return micro; }
}

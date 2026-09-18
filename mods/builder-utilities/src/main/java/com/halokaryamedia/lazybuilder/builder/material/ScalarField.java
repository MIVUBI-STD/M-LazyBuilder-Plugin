package com.halokaryamedia.lazybuilder.builder.material;

@FunctionalInterface
public interface ScalarField {
    double sample(MaterialContext context);
}

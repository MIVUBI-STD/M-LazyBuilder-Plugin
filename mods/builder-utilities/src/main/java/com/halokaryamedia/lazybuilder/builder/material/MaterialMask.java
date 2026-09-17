package com.halokaryamedia.lazybuilder.builder.material;

@FunctionalInterface
public interface MaterialMask {
    boolean test(MaterialContext context);

    static MaterialMask all() {
        return context -> true;
    }
}

package com.halokaryamedia.lazybuilder.builder.material;

@FunctionalInterface
public interface SurfaceHeightFieldSource {
    double heightAt(int x, int z);
}

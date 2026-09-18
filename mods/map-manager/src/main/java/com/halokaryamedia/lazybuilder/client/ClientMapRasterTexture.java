package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Arrays;

/**
 * Owns one nearest-filtered dynamic texture for the fullscreen map raster.
 *
 * <p>This class is intentionally presentation-only. It does not sample world
 * data, own map persistence, or decide world scope. The caller supplies an ARGB
 * raster and this class keeps the GPU texture stable until dimensions or pixels
 * actually change.</p>
 */
final class ClientMapRasterTexture implements AutoCloseable {
    private static final Identifier TEXTURE_ID = Identifier.of("lazybuilder", "map/runtime_raster");

    private final MinecraftClient client;
    private NativeImageBackedTexture texture;
    private int width;
    private int height;
    private int[] uploadedPixels = new int[0];

    ClientMapRasterTexture(MinecraftClient client) {
        this.client = client;
    }

    Identifier id() {
        return TEXTURE_ID;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    boolean ready() {
        return texture != null && width > 0 && height > 0;
    }

    /**
     * Uploads a complete ARGB raster. Reuses the existing GL texture when its
     * dimensions match, skips identical frames entirely, and avoids rewriting
     * unchanged NativeImage texels when only part of the raster changed.
     */
    void upload(int[] argb, int width, int height) {
        if (width <= 0 || height <= 0 || argb == null || argb.length != width * height) {
            throw new IllegalArgumentException("Map raster dimensions do not match pixel data");
        }
        boolean reusablePixels = texture != null
                && this.width == width
                && this.height == height
                && uploadedPixels.length == argb.length;
        if (reusablePixels && Arrays.equals(uploadedPixels, argb)) {
            return;
        }

        ensureTexture(width, height);
        NativeImage image = texture.getImage();
        if (image == null) throw new IllegalStateException("Map raster texture has no backing image");

        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!reusablePixels || uploadedPixels[index] != argb[index]) {
                    image.setColorArgb(x, y, argb[index]);
                }
                index++;
            }
        }
        texture.upload();
        if (uploadedPixels.length != argb.length) uploadedPixels = new int[argb.length];
        System.arraycopy(argb, 0, uploadedPixels, 0, argb.length);
    }

    /**
     * Draws the complete retained raster as one textured quad. Destination size
     * is independent from the source region size so nearest filtering can scale
     * one map texel to multiple screen pixels without reading outside the image.
     */
    void draw(DrawContext context, int x, int y, int drawWidth, int drawHeight) {
        if (!ready() || drawWidth <= 0 || drawHeight <= 0) return;
        context.drawTexture(
                RenderLayer::getGuiTextured,
                TEXTURE_ID,
                x,
                y,
                0.0F,
                0.0F,
                drawWidth,
                drawHeight,
                width,
                height,
                width,
                height
        );
    }

    private void ensureTexture(int width, int height) {
        if (texture != null && this.width == width && this.height == height) return;

        destroyTexture();
        NativeImageBackedTexture created = new NativeImageBackedTexture(width, height, false);
        created.setFilter(false, false);
        created.setClamp(true);
        client.getTextureManager().registerTexture(TEXTURE_ID, created);
        texture = created;
        this.width = width;
        this.height = height;
    }

    @Override
    public void close() {
        destroyTexture();
    }

    private void destroyTexture() {
        if (texture != null) client.getTextureManager().destroyTexture(TEXTURE_ID);
        texture = null;
        width = 0;
        height = 0;
        uploadedPixels = new int[0];
    }
}

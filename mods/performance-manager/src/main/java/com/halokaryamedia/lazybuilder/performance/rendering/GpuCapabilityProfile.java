package com.halokaryamedia.lazybuilder.performance.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;

/** Render-context capability snapshot used for diagnostics and fast-path gating. */
public final class GpuCapabilityProfile {
    private static volatile Snapshot cached;

    private GpuCapabilityProfile() {
    }

    public static Snapshot current() {
        Snapshot snapshot = cached;
        if (snapshot != null) return snapshot;
        if (!RenderSystem.isOnRenderThread()) return Snapshot.UNKNOWN;

        try {
            var caps = GL.getCapabilities();
            String vendor = safeString(GL11C.glGetString(GL11C.GL_VENDOR));
            String renderer = safeString(GL11C.glGetString(GL11C.GL_RENDERER));
            String version = safeString(GL11C.glGetString(GL11C.GL_VERSION));
            snapshot = new Snapshot(
                    true,
                    vendor,
                    renderer,
                    version,
                    caps.OpenGL33,
                    caps.OpenGL46 || caps.GL_ARB_shader_draw_parameters,
                    caps.OpenGL32
            );
        } catch (RuntimeException error) {
            snapshot = Snapshot.UNKNOWN;
        }
        cached = snapshot;
        return snapshot;
    }

    public static void invalidate() {
        cached = null;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    public record Snapshot(
            boolean available,
            String vendor,
            String renderer,
            String version,
            boolean timerQueries,
            boolean drawId,
            boolean baseVertex
    ) {
        private static final Snapshot UNKNOWN =
                new Snapshot(false, "", "", "", false, false, false);

        public Snapshot {
            vendor = vendor == null ? "" : vendor;
            renderer = renderer == null ? "" : renderer;
            version = version == null ? "" : version;
        }

        public String tier() {
            if (!available) return "unknown";
            if (drawId && baseVertex && timerQueries) return "advanced";
            if (baseVertex) return "arena";
            return "safe";
        }
    }
}

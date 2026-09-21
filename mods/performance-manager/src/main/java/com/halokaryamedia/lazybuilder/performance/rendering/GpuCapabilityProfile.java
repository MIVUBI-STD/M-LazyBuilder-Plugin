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
                    caps.OpenGL32,
                    reportedVramBytes(caps)
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

    private static long reportedVramBytes(Object capabilities) {
        // NVX_gpu_memory_info is the only common desktop extension that exposes a
        // useful total-memory figure. Reflection keeps this optional capability from
        // becoming a compile/runtime requirement on non-NVIDIA drivers.
        try {
            var field = capabilities.getClass().getField("GL_NVX_gpu_memory_info");
            if (!field.getBoolean(capabilities)) return 0L;
            int totalKiB = GL11C.glGetInteger(0x9048); // GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX
            return totalKiB <= 0 ? 0L : totalKiB * 1024L;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0L;
        }
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
            boolean baseVertex,
            long reportedVramBytes
    ) {
        private static final Snapshot UNKNOWN =
                new Snapshot(false, "", "", "", false, false, false, 0L);

        public Snapshot {
            vendor = vendor == null ? "" : vendor;
            renderer = renderer == null ? "" : renderer;
            version = version == null ? "" : version;
            reportedVramBytes = Math.max(0L, reportedVramBytes);
        }

        public String tier() {
            if (!available) return "unknown";
            if (drawId && baseVertex && timerQueries) return "advanced";
            if (baseVertex) return "arena";
            return "safe";
        }
    }
}

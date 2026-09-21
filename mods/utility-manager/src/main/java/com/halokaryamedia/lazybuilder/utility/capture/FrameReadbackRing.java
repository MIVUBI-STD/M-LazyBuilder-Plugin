package com.halokaryamedia.lazybuilder.utility.capture;

import com.halokaryamedia.lazybuilder.utility.mixin.FramebufferAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;

import java.nio.ByteBuffer;

/**
 * Triple-PBO framebuffer readback with non-blocking GPU fences.
 *
 * A frame is copied to CPU memory only after its fence signals. If the GPU or encoder is behind,
 * the capture frame is dropped instead of stalling Minecraft's render thread.
 */
final class FrameReadbackRing implements AutoCloseable {
    private static final int SLOT_COUNT = 3;

    private final Slot[] slots = new Slot[SLOT_COUNT];
    private int cursor;

    FrameReadbackRing() {
        for (int i = 0; i < slots.length; i++) slots[i] = new Slot();
    }

    boolean capture(
            Framebuffer framebuffer,
            int repeatCount,
            VideoCaptureSession session
    ) {
        if (framebuffer == null || session == null || !RenderSystem.isOnRenderThread()) return false;

        drainReady(session);

        Slot slot = slots[cursor];
        if (slot.pending()) {
            session.recordDroppedFrame();
            return false;
        }

        FramebufferAccessor access = (FramebufferAccessor) (Object) framebuffer;
        int width = Math.max(1, access.lazybuilder$getTextureWidth());
        int height = Math.max(1, access.lazybuilder$getTextureHeight());
        long requiredLong = (long) width * (long) height * 4L;
        if (requiredLong <= 0L || requiredLong > Integer.MAX_VALUE) {
            session.fail("Video framebuffer is too large to capture safely.");
            return false;
        }
        int required = (int) requiredLong;

        slot.ensureCapacity(required);
        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, slot.pbo);
        try {
            framebuffer.beginRead();
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 1);
            GL11C.glGetTexImage(
                    GL11C.GL_TEXTURE_2D,
                    0,
                    GL11C.GL_BGRA,
                    GL11C.GL_UNSIGNED_BYTE,
                    0L
            );
            framebuffer.endRead();

            slot.fence = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            slot.width = width;
            slot.height = height;
            slot.repeatCount = Math.max(1, repeatCount);
        } finally {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
        }

        cursor = (cursor + 1) % SLOT_COUNT;
        return true;
    }

    void drainReady(VideoCaptureSession session) {
        if (!RenderSystem.isOnRenderThread() || session == null) return;

        for (Slot slot : slots) {
            if (!slot.pending()) continue;
            int wait = GL32C.glClientWaitSync(slot.fence, 0, 0L);
            if (wait != GL32C.GL_ALREADY_SIGNALED && wait != GL32C.GL_CONDITION_SATISFIED) {
                continue;
            }

            VideoCaptureSession.FramePacket packet = session.acquireFramePacket(slot.capacity);
            if (packet == null) {
                session.recordDroppedFrame();
                slot.releaseFence();
                continue;
            }

            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, slot.pbo);
            ByteBuffer mapped = null;
            try {
                mapped = GL30C.glMapBufferRange(
                        GL21C.GL_PIXEL_PACK_BUFFER,
                        0L,
                        slot.capacity,
                        GL30C.GL_MAP_READ_BIT
                );
                if (mapped == null) {
                    session.recycleFramePacket(packet);
                    session.recordDroppedFrame();
                    continue;
                }
                packet.buffer().clear();
                mapped.position(0);
                mapped.limit(slot.capacity);
                packet.buffer().put(mapped);
                packet.buffer().flip();
                packet.repeatCount(slot.repeatCount);
                session.submitFramePacket(packet);
            } finally {
                if (mapped != null) GL15C.glUnmapBuffer(GL21C.GL_PIXEL_PACK_BUFFER);
                GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
                slot.releaseFence();
            }
        }
    }

    boolean hasPending() {
        for (Slot slot : slots) {
            if (slot.pending()) return true;
        }
        return false;
    }

    void dropPending() {
        if (!RenderSystem.isOnRenderThread()) return;
        for (Slot slot : slots) slot.releaseFence();
    }

    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) return;
        for (Slot slot : slots) slot.close();
    }

    private static final class Slot {
        private int pbo;
        private long fence;
        private int capacity;
        private int width;
        private int height;
        private int repeatCount;

        boolean pending() {
            return fence != 0L;
        }

        void ensureCapacity(int required) {
            if (pbo == 0) pbo = GL15C.glGenBuffers();
            if (capacity >= required) return;

            capacity = required;
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, pbo);
            GL15C.glBufferData(GL21C.GL_PIXEL_PACK_BUFFER, required, GL15C.GL_STREAM_READ);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
        }

        void releaseFence() {
            if (fence != 0L) {
                GL32C.glDeleteSync(fence);
                fence = 0L;
            }
            width = 0;
            height = 0;
            repeatCount = 0;
        }

        void close() {
            releaseFence();
            if (pbo != 0) {
                GL15C.glDeleteBuffers(pbo);
                pbo = 0;
            }
            capacity = 0;
        }
    }
}

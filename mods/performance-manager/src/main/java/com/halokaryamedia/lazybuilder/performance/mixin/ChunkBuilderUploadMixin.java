package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkUploadTask;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.util.BufferAllocator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;

/** Batches consecutive chunk uploads targeting the same GPU buffer without changing queue order. */
@Mixin(ChunkBuilder.class)
abstract class ChunkBuilderUploadMixin {
    @Shadow
    @Final
    private Queue<Runnable> uploadQueue;

    @Shadow
    private boolean stopped;

    @Inject(method = "scheduleUpload", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$scheduleVertexUpload(
            BuiltBuffer builtBuffer,
            VertexBuffer glBuffer,
            CallbackInfoReturnable<CompletableFuture<Void>> cir
    ) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()) return;
        if (this.stopped) {
            cir.setReturnValue(CompletableFuture.completedFuture(null));
            return;
        }

        ChunkUploadTask task = ChunkUploadTask.vertex(builtBuffer, glBuffer);
        this.uploadQueue.add(task);
        cir.setReturnValue(task.future());
    }

    @Inject(method = "scheduleIndexBufferUpload", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$scheduleIndexUpload(
            BufferAllocator.CloseableBuffer indexBuffer,
            VertexBuffer vertexBuffer,
            CallbackInfoReturnable<CompletableFuture<Void>> cir
    ) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()) return;
        if (this.stopped) {
            cir.setReturnValue(CompletableFuture.completedFuture(null));
            return;
        }

        ChunkUploadTask task = ChunkUploadTask.index(indexBuffer, vertexBuffer);
        this.uploadQueue.add(task);
        cir.setReturnValue(task.future());
    }

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$batchUploads(CallbackInfo ci) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()) return;

        Runnable runnable;
        while ((runnable = this.uploadQueue.poll()) != null) {
            if (!(runnable instanceof ChunkUploadTask task)) {
                runnable.run();
                continue;
            }
            lazybuilder$runUploadBatch(task);
        }
        ci.cancel();
    }

    private void lazybuilder$runUploadBatch(ChunkUploadTask first) {
        VertexBuffer buffer = first.buffer();
        if (buffer.isClosed()) {
            first.discard();
            return;
        }

        try {
            buffer.bind();
        } catch (Throwable throwable) {
            first.fail(throwable);
            return;
        }

        int taskCount = 0;
        try {
            first.executeBound();
            taskCount++;

            while (true) {
                Runnable next = this.uploadQueue.peek();
                if (!(next instanceof ChunkUploadTask nextTask) || nextTask.buffer() != buffer) break;
                this.uploadQueue.poll();
                nextTask.executeBound();
                taskCount++;
            }
        } finally {
            VertexBuffer.unbind();
        }

        ChunkPipelineMetrics.recordUploadBatch(taskCount);
    }
}

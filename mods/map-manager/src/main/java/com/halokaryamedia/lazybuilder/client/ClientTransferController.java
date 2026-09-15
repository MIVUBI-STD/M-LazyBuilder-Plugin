package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.transfer.TransferDescriptor;
import com.halokaryamedia.lazybuilder.world.transfer.TransferWireProtocol;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Client-side bounded transfer coordinator. Server sessions remain authoritative;
 * local state tracks one explicit transfer operation at a time.
 *
 * <p>Chunks use one seekable channel per active file and the protocol-owned
 * bounded credit window. Every asynchronous continuation is tied to a local
 * lifecycle generation so disconnect/reset/shutdown cannot resurrect stale work.</p>
 */
public final class ClientTransferController {
    private static final Consumer<String> NO_UPLOAD_CALLBACK = ignored -> { };
    private static final Runnable NO_CANCEL_CALLBACK = () -> { };
    private static final long SHUTDOWN_WAIT_SECONDS = 2L;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LazyBuilder-Transfer-IO");
        thread.setDaemon(true);
        return thread;
    });

    private Upload upload;
    private Download download;
    private Consumer<String> uploadFinished = NO_UPLOAD_CALLBACK;
    private Runnable uploadCancelled = NO_CANCEL_CALLBACK;
    private TransferStatus status = TransferStatus.idle();
    private long revision;
    private long lifecycleGeneration;

    public void chooseAndUploadImport() {
        chooseAndUploadImport(NO_UPLOAD_CALLBACK, NO_CANCEL_CALLBACK);
    }

    public void chooseAndUploadImport(Consumer<String> onUploaded) {
        chooseAndUploadImport(onUploaded, NO_CANCEL_CALLBACK);
    }

    public void chooseAndUploadImport(Consumer<String> onUploaded, Runnable onCancelled) {
        requireIdle();
        long generation = beginOperation();
        uploadFinished = Objects.requireNonNull(onUploaded, "onUploaded");
        uploadCancelled = Objects.requireNonNull(onCancelled, "onCancelled");
        setStatus(new TransferStatus(TransferPhase.CHOOSING_IMPORT, "", 0, 0, "Choose a world file"));
        ClientFileDialogs.chooseImport().thenAccept(optional -> clientExecute(() -> {
            if (!isCurrent(generation)) return;
            if (optional.isEmpty()) {
                uploadFinished = NO_UPLOAD_CALLBACK;
                Runnable callback = uploadCancelled;
                uploadCancelled = NO_CANCEL_CALLBACK;
                setStatus(TransferStatus.idle());
                callback.run();
                return;
            }
            beginUpload(optional.get(), generation);
        }));
    }

    public void downloadExport(String fileName) {
        requireIdle();
        long generation = beginOperation();
        String safeName = Objects.requireNonNull(fileName, "fileName");
        setStatus(new TransferStatus(TransferPhase.CHOOSING_EXPORT_DESTINATION,
                safeName, 0, 0, "Choose where to save the export"));
        ClientFileDialogs.chooseExportDestination(safeName).thenAccept(optional -> clientExecute(() -> {
            if (!isCurrent(generation)) return;
            if (optional.isEmpty()) {
                setStatus(TransferStatus.idle());
                return;
            }
            beginDownload(safeName, optional.get(), generation);
        }));
    }

    public void accept(TransferWireProtocol.Response response) {
        Objects.requireNonNull(response, "response");
        switch (response) {
            case TransferWireProtocol.UploadAccepted accepted -> onUploadAccepted(accepted.descriptor());
            case TransferWireProtocol.UploadProgressResponse progress -> onUploadProgress(progress);
            case TransferWireProtocol.UploadFinished finished -> {
                Upload state = upload;
                if (state == null || !isCurrent(state.generation)) return;
                upload = null;
                closeQuietly(state.channel);
                Consumer<String> callback = uploadFinished;
                uploadFinished = NO_UPLOAD_CALLBACK;
                uploadCancelled = NO_CANCEL_CALLBACK;
                setStatus(TransferStatus.idle());
                LazyBuilderClientNetworking.notifyPlayer("Upload complete: " + finished.fileName());
                callback.accept(finished.fileName());
            }
            case TransferWireProtocol.DownloadAccepted accepted -> onDownloadAccepted(accepted.descriptor());
            case TransferWireProtocol.DownloadChunkData chunk -> onDownloadChunk(chunk);
            case TransferWireProtocol.Ack ignored -> onAck();
            case TransferWireProtocol.ErrorResponse error -> {
                invalidateLifecycle();
                cleanupLocalUpload();
                cleanupLocalDownload();
                setStatus(new TransferStatus(TransferPhase.FAILED, "", 0, 0, error.message()));
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder transfer: " + error.message());
            }
        }
    }

    public void reset() {
        invalidateLifecycle();
        cleanupLocalUpload();
        cleanupLocalDownload();
        setStatus(TransferStatus.idle());
    }

    /** Releases transfer-local resources and prevents stale queued I/O from re-entering the client. */
    public void shutdownIo() {
        reset();
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            ioExecutor.shutdownNow();
        }
    }

    public TransferStatus status() {
        return status;
    }

    public long revision() {
        return revision;
    }

    private void requireIdle() {
        if (upload != null || download != null
                || status.phase() == TransferPhase.CHOOSING_IMPORT
                || status.phase() == TransferPhase.CHOOSING_EXPORT_DESTINATION
                || status.phase() == TransferPhase.PREPARING_UPLOAD) {
            throw new IllegalStateException("A file transfer is already active");
        }
    }

    private long beginOperation() {
        return ++lifecycleGeneration;
    }

    private void invalidateLifecycle() {
        lifecycleGeneration++;
    }

    private boolean isCurrent(long generation) {
        return generation == lifecycleGeneration && !ioExecutor.isShutdown();
    }

    private void beginUpload(Path source, long generation) {
        if (!isCurrent(generation)) return;
        Path normalized = source.toAbsolutePath().normalize();
        setStatus(new TransferStatus(TransferPhase.PREPARING_UPLOAD,
                normalized.getFileName().toString(), 0, 0, "Preparing world file"));
        CompletableFuture.supplyAsync(() -> {
            try {
                if (!Files.isRegularFile(normalized)) throw new IOException("Selected import file is missing");
                long size = Files.size(normalized);
                String digest = sha256(normalized);
                return new PreparedUpload(normalized, size, digest);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }, ioExecutor).whenComplete((prepared, failure) -> clientExecute(() -> {
            if (!isCurrent(generation)) return;
            if (failure != null) {
                uploadFinished = NO_UPLOAD_CALLBACK;
                uploadCancelled = NO_CANCEL_CALLBACK;
                String message = "Could not prepare import file: " + rootMessage(failure);
                setStatus(new TransferStatus(TransferPhase.FAILED,
                        normalized.getFileName().toString(), 0, 0, message));
                LazyBuilderClientNetworking.notifyPlayer(message);
                return;
            }
            try {
                upload = new Upload(prepared.path(), generation);
                setStatus(new TransferStatus(TransferPhase.UPLOADING,
                        prepared.path().getFileName().toString(), 0, prepared.size(), "Uploading world"));
                send(new TransferWireProtocol.BeginUpload(
                        prepared.path().getFileName().toString(), prepared.size(), prepared.sha256()));
            } catch (Exception exception) {
                cleanupLocalUpload();
                String message = "Could not start upload: " + exception.getMessage();
                setStatus(new TransferStatus(TransferPhase.FAILED,
                        prepared.path().getFileName().toString(), 0, prepared.size(), message));
                LazyBuilderClientNetworking.notifyPlayer(message);
            }
        }));
    }

    private void onUploadAccepted(TransferDescriptor descriptor) {
        Upload state = upload;
        if (state == null || !isCurrent(state.generation)) return;
        try {
            state.descriptor = descriptor;
            state.channel = FileChannel.open(state.source, StandardOpenOption.READ);
            setStatus(new TransferStatus(TransferPhase.UPLOADING,
                    state.source.getFileName().toString(), 0, descriptor.totalBytes(), "Uploading world"));
            pumpUploadBatch(state);
        } catch (IOException exception) {
            abortUpload("Could not open import file: " + exception.getMessage());
        }
    }

    private void onUploadProgress(TransferWireProtocol.UploadProgressResponse progress) {
        Upload state = upload;
        if (state == null || !isCurrent(state.generation) || state.descriptor == null
                || !state.descriptor.sessionId().equals(progress.sessionId())) return;
        state.acknowledged = Math.max(state.acknowledged, progress.nextChunkIndex());
        long completed = Math.min(state.descriptor.totalBytes(),
                (long) state.acknowledged * state.descriptor.chunkBytes());
        setStatus(new TransferStatus(TransferPhase.UPLOADING,
                state.source.getFileName().toString(), completed, state.descriptor.totalBytes(), "Uploading world"));
        if (state.acknowledged >= progress.totalChunks()) {
            if (!state.finishSent) {
                state.finishSent = true;
                send(new TransferWireProtocol.FinishUpload(progress.sessionId()));
            }
            return;
        }
        if (!state.batchReadInFlight && state.acknowledged >= state.nextChunkToSend) {
            pumpUploadBatch(state);
        }
    }

    private void pumpUploadBatch(Upload state) {
        if (upload != state || !isCurrent(state.generation)
                || state.descriptor == null || state.channel == null || state.batchReadInFlight) return;
        int start = state.nextChunkToSend;
        if (start >= state.descriptor.totalChunks()) return;
        int end = Math.min(start + TransferWireProtocol.PIPELINE_WINDOW, state.descriptor.totalChunks());
        state.batchReadInFlight = true;

        CompletableFuture.supplyAsync(() -> {
            try {
                List<byte[]> batch = new ArrayList<>(end - start);
                for (int index = start; index < end; index++) {
                    long offset = (long) index * state.descriptor.chunkBytes();
                    int length = (int) Math.min(
                            state.descriptor.chunkBytes(), state.descriptor.totalBytes() - offset);
                    byte[] bytes = new byte[length];
                    readFully(state.channel, ByteBuffer.wrap(bytes), offset);
                    batch.add(bytes);
                }
                return batch;
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }, ioExecutor).whenComplete((batch, failure) -> clientExecute(() -> {
            if (upload != state || !isCurrent(state.generation)) return;
            state.batchReadInFlight = false;
            if (failure != null) {
                abortUpload("Upload read failed: " + rootMessage(failure));
                return;
            }
            try {
                for (int i = 0; i < batch.size(); i++) {
                    send(new TransferWireProtocol.UploadChunk(
                            state.descriptor.sessionId(), start + i, batch.get(i)));
                }
                state.nextChunkToSend = end;
            } catch (RuntimeException exception) {
                abortUpload("Upload send failed: " + exception.getMessage());
            }
        }));
    }

    private void beginDownload(String fileName, Path destination, long generation) {
        if (!isCurrent(generation)) return;
        Path target = destination.toAbsolutePath().normalize();
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        download = new Download(fileName, target, partial, generation);
        setStatus(new TransferStatus(TransferPhase.DOWNLOADING, fileName, 0, 0, "Starting download"));
        send(new TransferWireProtocol.BeginDownload(fileName));
    }

    private void onDownloadAccepted(TransferDescriptor descriptor) {
        Download state = download;
        if (state == null || !isCurrent(state.generation)) return;
        try {
            state.descriptor = descriptor;
            Files.deleteIfExists(state.partial);
            Path parent = state.partial.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path storageRoot = parent == null ? state.partial.toAbsolutePath().getParent() : parent;
            if (storageRoot != null && Files.getFileStore(storageRoot).getUsableSpace() < descriptor.totalBytes()) {
                abortDownload("Not enough space at the selected save location for this export");
                return;
            }
            state.channel = FileChannel.open(state.partial,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            setStatus(new TransferStatus(TransferPhase.DOWNLOADING,
                    state.fileName, 0, descriptor.totalBytes(), "Downloading export"));
            requestDownloadBatch(state);
        } catch (IOException exception) {
            abortDownload("Could not create local export file: " + exception.getMessage());
        }
    }

    private void requestDownloadBatch(Download state) {
        if (download != state || !isCurrent(state.generation)
                || state.descriptor == null || state.awaitingFinishAck) return;
        int start = state.nextChunkToRequest;
        if (start >= state.descriptor.totalChunks()) return;
        int end = Math.min(start + TransferWireProtocol.PIPELINE_WINDOW, state.descriptor.totalChunks());
        state.batchStart = start;
        state.batchEnd = end;
        state.receivedInBatch = 0;
        state.pendingWrites = 0;
        state.batchSawLast = false;
        state.nextChunkToRequest = end;
        for (int index = start; index < end; index++) {
            send(new TransferWireProtocol.DownloadChunkRequest(state.descriptor.sessionId(), index));
        }
    }

    private void onDownloadChunk(TransferWireProtocol.DownloadChunkData chunk) {
        Download state = download;
        if (state == null || !isCurrent(state.generation) || state.descriptor == null || state.channel == null
                || !state.descriptor.sessionId().equals(chunk.sessionId())) return;
        if (chunk.chunkIndex() < state.batchStart || chunk.chunkIndex() >= state.batchEnd) {
            abortDownload("Download response arrived outside the active pipeline window");
            return;
        }

        state.receivedInBatch++;
        state.receivedChunks++;
        state.pendingWrites++;
        state.batchSawLast |= chunk.last();
        long completed = Math.min(state.descriptor.totalBytes(),
                (long) state.receivedChunks * state.descriptor.chunkBytes());
        setStatus(new TransferStatus(TransferPhase.DOWNLOADING,
                state.fileName, completed, state.descriptor.totalBytes(), "Downloading export"));

        long offset = (long) chunk.chunkIndex() * state.descriptor.chunkBytes();
        CompletableFuture.runAsync(() -> {
            try {
                writeFully(state.channel, ByteBuffer.wrap(chunk.data()), offset);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }, ioExecutor).whenComplete((ignored, failure) -> clientExecute(() -> {
            if (download != state || !isCurrent(state.generation)) return;
            state.pendingWrites--;
            if (failure != null) {
                abortDownload("Download write failed: " + rootMessage(failure));
                return;
            }
            completeDownloadBatchIfReady(state);
        }));
    }

    private void completeDownloadBatchIfReady(Download state) {
        if (!isCurrent(state.generation)) return;
        int expected = state.batchEnd - state.batchStart;
        if (state.receivedInBatch != expected || state.pendingWrites != 0) return;
        if (state.batchSawLast || state.nextChunkToRequest >= state.descriptor.totalChunks()) {
            state.awaitingFinishAck = true;
            setStatus(new TransferStatus(TransferPhase.FINALIZING_DOWNLOAD,
                    state.fileName, state.descriptor.totalBytes(), state.descriptor.totalBytes(), "Finalizing export"));
            send(new TransferWireProtocol.FinishDownload(state.descriptor.sessionId()));
        } else {
            requestDownloadBatch(state);
        }
    }

    private void onAck() {
        Download state = download;
        if (state == null || !isCurrent(state.generation) || !state.awaitingFinishAck || state.descriptor == null) return;
        state.awaitingFinishAck = false;
        setStatus(new TransferStatus(TransferPhase.FINALIZING_DOWNLOAD,
                state.fileName, state.descriptor.totalBytes(), state.descriptor.totalBytes(), "Verifying export"));
        CompletableFuture.runAsync(() -> {
            try {
                if (state.channel != null) {
                    state.channel.force(false);
                    state.channel.close();
                }
                String actual = sha256(state.partial);
                if (!actual.equalsIgnoreCase(state.descriptor.sha256())) {
                    throw new IOException("Downloaded export checksum mismatch");
                }
                move(state.partial, state.target);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }, ioExecutor).whenComplete((ignored, failure) -> clientExecute(() -> {
            if (download != state || !isCurrent(state.generation)) return;
            if (failure != null) {
                cleanupLocalDownload();
                String message = "Could not finalize export: " + rootMessage(failure);
                setStatus(new TransferStatus(TransferPhase.FAILED, state.fileName, 0, 0, message));
                LazyBuilderClientNetworking.notifyPlayer(message);
                return;
            }
            String name = state.target.getFileName().toString();
            download = null;
            setStatus(TransferStatus.idle());
            LazyBuilderClientNetworking.notifyPlayer("Export saved: " + name);
        }));
    }

    private void abortUpload(String message) {
        Upload state = upload;
        upload = null;
        uploadFinished = NO_UPLOAD_CALLBACK;
        uploadCancelled = NO_CANCEL_CALLBACK;
        closeQuietly(state == null ? null : state.channel);
        if (state != null && state.descriptor != null && isCurrent(state.generation)) {
            send(new TransferWireProtocol.AbortUpload(state.descriptor.sessionId()));
        }
        setStatus(new TransferStatus(TransferPhase.FAILED,
                state == null ? "" : state.source.getFileName().toString(), 0, 0, message));
        LazyBuilderClientNetworking.notifyPlayer(message);
    }

    private void abortDownload(String message) {
        Download state = download;
        download = null;
        closeQuietly(state == null ? null : state.channel);
        if (state != null && state.descriptor != null && isCurrent(state.generation)) {
            send(new TransferWireProtocol.AbortDownload(state.descriptor.sessionId()));
        }
        if (state != null) {
            try { Files.deleteIfExists(state.partial); } catch (IOException ignored) { }
        }
        setStatus(new TransferStatus(TransferPhase.FAILED,
                state == null ? "" : state.fileName, 0, 0, message));
        LazyBuilderClientNetworking.notifyPlayer(message);
    }

    private void cleanupLocalUpload() {
        Upload state = upload;
        upload = null;
        uploadFinished = NO_UPLOAD_CALLBACK;
        uploadCancelled = NO_CANCEL_CALLBACK;
        closeQuietly(state == null ? null : state.channel);
    }

    private void cleanupLocalDownload() {
        Download state = download;
        download = null;
        closeQuietly(state == null ? null : state.channel);
        if (state != null) {
            try { Files.deleteIfExists(state.partial); } catch (IOException ignored) { }
        }
    }

    private void setStatus(TransferStatus status) {
        this.status = Objects.requireNonNull(status, "status");
        revision++;
    }

    private static void send(TransferWireProtocol.Request request) {
        try {
            LazyBuilderClientNetworking.sendTransfer(TransferWireProtocol.encodeRequest(request));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode transfer request", exception);
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer target, long position) throws IOException {
        long cursor = position;
        while (target.hasRemaining()) {
            int read = channel.read(target, cursor);
            if (read < 0) throw new IOException("Import file changed while uploading");
            if (read == 0) continue;
            cursor += read;
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer source, long position) throws IOException {
        long cursor = position;
        while (source.hasRemaining()) {
            int written = channel.write(source, cursor);
            if (written < 0) throw new IOException("Local export channel closed while writing");
            if (written == 0) continue;
            cursor += written;
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = in.read(buffer)) >= 0;) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        if (Files.exists(target)) throw new IOException("Destination already exists: " + target.getFileName());
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target); }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) return;
        try { channel.close(); } catch (IOException ignored) { }
    }

    private static void clientExecute(Runnable task) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) client.execute(task);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return Objects.toString(current.getMessage(), current.getClass().getSimpleName());
    }

    public enum TransferPhase {
        IDLE,
        CHOOSING_IMPORT,
        PREPARING_UPLOAD,
        UPLOADING,
        CHOOSING_EXPORT_DESTINATION,
        DOWNLOADING,
        FINALIZING_DOWNLOAD,
        FAILED
    }

    public record TransferStatus(
            TransferPhase phase,
            String fileName,
            long completedBytes,
            long totalBytes,
            String message
    ) {
        public TransferStatus {
            Objects.requireNonNull(phase, "phase");
            fileName = Objects.requireNonNullElse(fileName, "");
            message = Objects.requireNonNullElse(message, "");
            completedBytes = Math.max(0, completedBytes);
            totalBytes = Math.max(0, totalBytes);
        }

        public static TransferStatus idle() {
            return new TransferStatus(TransferPhase.IDLE, "", 0, 0, "");
        }

        public boolean active() {
            return phase != TransferPhase.IDLE && phase != TransferPhase.FAILED;
        }

        public int percent() {
            if (totalBytes <= 0) return -1;
            return (int) Math.min(100, Math.round(completedBytes * 100.0 / totalBytes));
        }
    }

    private record PreparedUpload(Path path, long size, String sha256) { }

    private static final class Upload {
        private final Path source;
        private final long generation;
        private TransferDescriptor descriptor;
        private FileChannel channel;
        private int nextChunkToSend;
        private int acknowledged;
        private boolean batchReadInFlight;
        private boolean finishSent;

        private Upload(Path source, long generation) {
            this.source = source;
            this.generation = generation;
        }
    }

    private static final class Download {
        private final String fileName;
        private final Path target;
        private final Path partial;
        private final long generation;
        private TransferDescriptor descriptor;
        private FileChannel channel;
        private int nextChunkToRequest;
        private int batchStart;
        private int batchEnd;
        private int receivedInBatch;
        private int receivedChunks;
        private int pendingWrites;
        private boolean batchSawLast;
        private boolean awaitingFinishAck;

        private Download(String fileName, Path target, Path partial, long generation) {
            this.fileName = fileName;
            this.target = target;
            this.partial = partial;
            this.generation = generation;
        }
    }
}

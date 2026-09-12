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
import java.util.function.Consumer;

/**
 * Client-side bounded transfer coordinator. Server sessions remain authoritative;
 * local state tracks one explicit transfer operation at a time.
 *
 * <p>Chunks use one seekable channel per active file and the protocol-owned
 * bounded credit window. This avoids reopen/skip I/O and one RTT per individual
 * 24 KiB payload while keeping memory and queue depth bounded.</p>
 */
public final class ClientTransferController {
    private static final Consumer<String> NO_UPLOAD_CALLBACK = ignored -> { };

    private Upload upload;
    private Download download;
    private Consumer<String> uploadFinished = NO_UPLOAD_CALLBACK;

    public void chooseAndUploadImport() {
        chooseAndUploadImport(NO_UPLOAD_CALLBACK);
    }

    public void chooseAndUploadImport(Consumer<String> onUploaded) {
        requireIdle();
        uploadFinished = Objects.requireNonNull(onUploaded, "onUploaded");
        ClientFileDialogs.chooseImport().thenAccept(optional -> clientExecute(() -> {
            if (optional.isEmpty()) {
                uploadFinished = NO_UPLOAD_CALLBACK;
                return;
            }
            beginUpload(optional.get());
        }));
    }

    public void downloadExport(String fileName) {
        requireIdle();
        ClientFileDialogs.chooseExportDestination(fileName).thenAccept(optional ->
                optional.ifPresent(destination -> beginDownload(fileName, destination)));
    }

    public void accept(TransferWireProtocol.Response response) {
        Objects.requireNonNull(response, "response");
        switch (response) {
            case TransferWireProtocol.UploadAccepted accepted -> onUploadAccepted(accepted.descriptor());
            case TransferWireProtocol.UploadProgressResponse progress -> onUploadProgress(progress);
            case TransferWireProtocol.UploadFinished finished -> {
                Upload state = upload;
                upload = null;
                closeQuietly(state == null ? null : state.channel);
                Consumer<String> callback = uploadFinished;
                uploadFinished = NO_UPLOAD_CALLBACK;
                LazyBuilderClientNetworking.notifyPlayer("Upload complete: " + finished.fileName());
                callback.accept(finished.fileName());
            }
            case TransferWireProtocol.DownloadAccepted accepted -> onDownloadAccepted(accepted.descriptor());
            case TransferWireProtocol.DownloadChunkData chunk -> onDownloadChunk(chunk);
            case TransferWireProtocol.Ack ignored -> onAck();
            case TransferWireProtocol.ErrorResponse error -> {
                cleanupLocalUpload();
                cleanupLocalDownload();
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder transfer: " + error.message());
            }
        }
    }

    public void reset() {
        cleanupLocalUpload();
        cleanupLocalDownload();
    }

    private void requireIdle() {
        if (upload != null || download != null) {
            throw new IllegalStateException("A file transfer is already active");
        }
    }

    private void beginUpload(Path source) {
        Path normalized = source.toAbsolutePath().normalize();
        CompletableFuture.supplyAsync(() -> {
            try {
                if (!Files.isRegularFile(normalized)) throw new IOException("Selected import file is missing");
                long size = Files.size(normalized);
                String digest = sha256(normalized);
                return new PreparedUpload(normalized, size, digest);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((prepared, failure) -> clientExecute(() -> {
            if (failure != null) {
                uploadFinished = NO_UPLOAD_CALLBACK;
                LazyBuilderClientNetworking.notifyPlayer("Could not prepare import file: " + rootMessage(failure));
                return;
            }
            try {
                requireIdle();
                upload = new Upload(prepared.path());
                send(new TransferWireProtocol.BeginUpload(
                        prepared.path().getFileName().toString(), prepared.size(), prepared.sha256()));
            } catch (Exception exception) {
                cleanupLocalUpload();
                LazyBuilderClientNetworking.notifyPlayer("Could not start upload: " + exception.getMessage());
            }
        }));
    }

    private void onUploadAccepted(TransferDescriptor descriptor) {
        Upload state = upload;
        if (state == null) return;
        try {
            state.descriptor = descriptor;
            state.channel = FileChannel.open(state.source, StandardOpenOption.READ);
            pumpUploadBatch(state);
        } catch (IOException exception) {
            abortUpload("Could not open import file: " + exception.getMessage());
        }
    }

    private void onUploadProgress(TransferWireProtocol.UploadProgressResponse progress) {
        Upload state = upload;
        if (state == null || state.descriptor == null
                || !state.descriptor.sessionId().equals(progress.sessionId())) return;
        state.acknowledged = Math.max(state.acknowledged, progress.nextChunkIndex());
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
        if (upload != state || state.descriptor == null || state.channel == null || state.batchReadInFlight) return;
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
        }).whenComplete((batch, failure) -> clientExecute(() -> {
            if (upload != state) return;
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

    private void beginDownload(String fileName, Path destination) {
        Path target = destination.toAbsolutePath().normalize();
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        download = new Download(fileName, target, partial);
        send(new TransferWireProtocol.BeginDownload(fileName));
    }

    private void onDownloadAccepted(TransferDescriptor descriptor) {
        Download state = download;
        if (state == null) return;
        try {
            Files.deleteIfExists(state.partial);
            Path parent = state.partial.getParent();
            if (parent != null) Files.createDirectories(parent);
            state.channel = FileChannel.open(state.partial,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            state.descriptor = descriptor;
            requestDownloadBatch(state);
        } catch (IOException exception) {
            abortDownload("Could not create local export file: " + exception.getMessage());
        }
    }

    private void requestDownloadBatch(Download state) {
        if (download != state || state.descriptor == null || state.awaitingFinishAck) return;
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
        if (state == null || state.descriptor == null || state.channel == null
                || !state.descriptor.sessionId().equals(chunk.sessionId())) return;
        if (chunk.chunkIndex() < state.batchStart || chunk.chunkIndex() >= state.batchEnd) {
            abortDownload("Download response arrived outside the active pipeline window");
            return;
        }

        state.receivedInBatch++;
        state.pendingWrites++;
        state.batchSawLast |= chunk.last();
        long offset = (long) chunk.chunkIndex() * state.descriptor.chunkBytes();
        CompletableFuture.runAsync(() -> {
            try {
                writeFully(state.channel, ByteBuffer.wrap(chunk.data()), offset);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((ignored, failure) -> clientExecute(() -> {
            if (download != state) return;
            state.pendingWrites--;
            if (failure != null) {
                abortDownload("Download write failed: " + rootMessage(failure));
                return;
            }
            completeDownloadBatchIfReady(state);
        }));
    }

    private void completeDownloadBatchIfReady(Download state) {
        int expected = state.batchEnd - state.batchStart;
        if (state.receivedInBatch != expected || state.pendingWrites != 0) return;
        if (state.batchSawLast || state.nextChunkToRequest >= state.descriptor.totalChunks()) {
            state.awaitingFinishAck = true;
            send(new TransferWireProtocol.FinishDownload(state.descriptor.sessionId()));
        } else {
            requestDownloadBatch(state);
        }
    }

    private void onAck() {
        Download state = download;
        if (state == null || !state.awaitingFinishAck || state.descriptor == null) return;
        state.awaitingFinishAck = false;
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
        }).whenComplete((ignored, failure) -> clientExecute(() -> {
            if (download != state) return;
            if (failure != null) {
                cleanupLocalDownload();
                LazyBuilderClientNetworking.notifyPlayer("Could not finalize export: " + rootMessage(failure));
                return;
            }
            String name = state.target.getFileName().toString();
            download = null;
            LazyBuilderClientNetworking.notifyPlayer("Export saved: " + name);
        }));
    }

    private void abortUpload(String message) {
        Upload state = upload;
        upload = null;
        uploadFinished = NO_UPLOAD_CALLBACK;
        closeQuietly(state == null ? null : state.channel);
        if (state != null && state.descriptor != null) {
            send(new TransferWireProtocol.AbortUpload(state.descriptor.sessionId()));
        }
        LazyBuilderClientNetworking.notifyPlayer(message);
    }

    private void abortDownload(String message) {
        Download state = download;
        download = null;
        closeQuietly(state == null ? null : state.channel);
        if (state != null && state.descriptor != null) {
            send(new TransferWireProtocol.AbortDownload(state.descriptor.sessionId()));
        }
        if (state != null) {
            try { Files.deleteIfExists(state.partial); } catch (IOException ignored) { }
        }
        LazyBuilderClientNetworking.notifyPlayer(message);
    }

    private void cleanupLocalUpload() {
        Upload state = upload;
        upload = null;
        uploadFinished = NO_UPLOAD_CALLBACK;
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
        MinecraftClient.getInstance().execute(task);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return Objects.toString(current.getMessage(), current.getClass().getSimpleName());
    }

    private record PreparedUpload(Path path, long size, String sha256) {}

    private static final class Upload {
        private final Path source;
        private TransferDescriptor descriptor;
        private FileChannel channel;
        private int nextChunkToSend;
        private int acknowledged;
        private boolean batchReadInFlight;
        private boolean finishSent;

        private Upload(Path source) {
            this.source = source;
        }
    }

    private static final class Download {
        private final String fileName;
        private final Path target;
        private final Path partial;
        private TransferDescriptor descriptor;
        private FileChannel channel;
        private int nextChunkToRequest;
        private int batchStart;
        private int batchEnd;
        private int receivedInBatch;
        private int pendingWrites;
        private boolean batchSawLast;
        private boolean awaitingFinishAck;

        private Download(String fileName, Path target, Path partial) {
            this.fileName = fileName;
            this.target = target;
            this.partial = partial;
        }
    }
}

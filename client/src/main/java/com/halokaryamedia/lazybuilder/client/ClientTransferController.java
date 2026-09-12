package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.transfer.TransferDescriptor;
import com.halokaryamedia.lazybuilder.world.transfer.TransferWireProtocol;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side stop-and-wait transfer coordinator. Server sessions remain authoritative;
 * local state only tracks the file selected by this client and one request chain.
 */
public final class ClientTransferController {
    private Upload upload;
    private Download download;

    public void chooseAndUploadImport() {
        if (upload != null) throw new IllegalStateException("An upload is already active");
        ClientFileDialogs.chooseImport().thenAccept(optional -> optional.ifPresent(this::beginUpload));
    }

    public void downloadExport(String fileName) {
        if (download != null) throw new IllegalStateException("A download is already active");
        ClientFileDialogs.chooseExportDestination(fileName).thenAccept(optional ->
                optional.ifPresent(destination -> beginDownload(fileName, destination)));
    }

    public void accept(TransferWireProtocol.Response response) {
        Objects.requireNonNull(response, "response");
        switch (response) {
            case TransferWireProtocol.UploadAccepted accepted -> onUploadAccepted(accepted.descriptor());
            case TransferWireProtocol.UploadProgressResponse progress -> onUploadProgress(progress);
            case TransferWireProtocol.UploadFinished finished -> {
                upload = null;
                LazyBuilderClientNetworking.notifyPlayer("Upload complete: " + finished.fileName());
            }
            case TransferWireProtocol.DownloadAccepted accepted -> onDownloadAccepted(accepted.descriptor());
            case TransferWireProtocol.DownloadChunkData chunk -> onDownloadChunk(chunk);
            case TransferWireProtocol.Ack ignored -> onAck();
            case TransferWireProtocol.ErrorResponse error -> {
                cleanupLocalDownload();
                upload = null;
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder transfer: " + error.message());
            }
        }
    }

    public void reset() {
        upload = null;
        cleanupLocalDownload();
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
                LazyBuilderClientNetworking.notifyPlayer("Could not prepare import file: " + rootMessage(failure));
                return;
            }
            try {
                upload = new Upload(prepared.path(), null, 0, 0);
                send(new TransferWireProtocol.BeginUpload(
                        prepared.path().getFileName().toString(), prepared.size(), prepared.sha256()));
            } catch (Exception exception) {
                upload = null;
                LazyBuilderClientNetworking.notifyPlayer("Could not start upload: " + exception.getMessage());
            }
        }));
    }

    private void onUploadAccepted(TransferDescriptor descriptor) {
        if (upload == null) return;
        upload = new Upload(upload.source(), descriptor, 0, 0);
        sendUploadChunk(0);
    }

    private void onUploadProgress(TransferWireProtocol.UploadProgressResponse progress) {
        if (upload == null || upload.descriptor() == null
                || !upload.descriptor().sessionId().equals(progress.sessionId())) return;
        if (progress.nextChunkIndex() >= progress.totalChunks()) {
            send(new TransferWireProtocol.FinishUpload(progress.sessionId()));
        } else {
            sendUploadChunk(progress.nextChunkIndex());
        }
    }

    private void sendUploadChunk(int index) {
        Upload state = upload;
        if (state == null || state.descriptor() == null) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                TransferDescriptor descriptor = state.descriptor();
                long offset = (long) index * descriptor.chunkBytes();
                int length = (int) Math.min(descriptor.chunkBytes(), descriptor.totalBytes() - offset);
                byte[] bytes = new byte[length];
                try (InputStream in = Files.newInputStream(state.source())) {
                    in.skipNBytes(offset);
                    int read = 0;
                    while (read < length) {
                        int count = in.read(bytes, read, length - read);
                        if (count < 0) throw new IOException("Import file changed while uploading");
                        read += count;
                    }
                }
                return bytes;
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((bytes, failure) -> clientExecute(() -> {
            if (failure != null) {
                abortUpload("Upload read failed: " + rootMessage(failure));
                return;
            }
            Upload current = upload;
            if (current == null || current.descriptor() == null) return;
            send(new TransferWireProtocol.UploadChunk(current.descriptor().sessionId(), index, bytes));
        }));
    }

    private void beginDownload(String fileName, Path destination) {
        Path target = destination.toAbsolutePath().normalize();
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        download = new Download(fileName, target, partial, null, false);
        send(new TransferWireProtocol.BeginDownload(fileName));
    }

    private void onDownloadAccepted(TransferDescriptor descriptor) {
        if (download == null) return;
        try {
            Files.deleteIfExists(download.partial());
            Path parent = download.partial().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.createFile(download.partial());
            download = new Download(download.fileName(), download.target(), download.partial(), descriptor, false);
            send(new TransferWireProtocol.DownloadChunkRequest(descriptor.sessionId(), 0));
        } catch (IOException exception) {
            abortDownload("Could not create local export file: " + exception.getMessage());
        }
    }

    private void onDownloadChunk(TransferWireProtocol.DownloadChunkData chunk) {
        Download state = download;
        if (state == null || state.descriptor() == null
                || !state.descriptor().sessionId().equals(chunk.sessionId())) return;
        CompletableFuture.runAsync(() -> {
            try (OutputStream out = Files.newOutputStream(state.partial(),
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                out.write(chunk.data());
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((ignored, failure) -> clientExecute(() -> {
            if (failure != null) {
                abortDownload("Download write failed: " + rootMessage(failure));
                return;
            }
            Download current = download;
            if (current == null || current.descriptor() == null) return;
            if (chunk.last()) {
                download = new Download(current.fileName(), current.target(), current.partial(), current.descriptor(), true);
                send(new TransferWireProtocol.FinishDownload(chunk.sessionId()));
            } else {
                send(new TransferWireProtocol.DownloadChunkRequest(chunk.sessionId(), chunk.chunkIndex() + 1));
            }
        }));
    }

    private void onAck() {
        Download state = download;
        if (state == null || !state.awaitingFinishAck() || state.descriptor() == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                String actual = sha256(state.partial());
                if (!actual.equalsIgnoreCase(state.descriptor().sha256())) {
                    throw new IOException("Downloaded export checksum mismatch");
                }
                move(state.partial(), state.target());
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((ignored, failure) -> clientExecute(() -> {
            if (failure != null) {
                cleanupLocalDownload();
                LazyBuilderClientNetworking.notifyPlayer("Could not finalize export: " + rootMessage(failure));
                return;
            }
            String name = state.target().getFileName().toString();
            download = null;
            LazyBuilderClientNetworking.notifyPlayer("Export saved: " + name);
        }));
    }

    private void abortUpload(String message) {
        Upload state = upload;
        upload = null;
        if (state != null && state.descriptor() != null) {
            send(new TransferWireProtocol.AbortUpload(state.descriptor().sessionId()));
        }
        LazyBuilderClientNetworking.notifyPlayer(message);
    }

    private void abortDownload(String message) {
        Download state = download;
        download = null;
        if (state != null && state.descriptor() != null) {
            send(new TransferWireProtocol.AbortDownload(state.descriptor().sessionId()));
        }
        if (state != null) {
            try { Files.deleteIfExists(state.partial()); } catch (IOException ignored) { }
        }
        LazyBuilderClientNetworking.notifyPlayer(message);
    }

    private void cleanupLocalDownload() {
        Download state = download;
        download = null;
        if (state != null) {
            try { Files.deleteIfExists(state.partial()); } catch (IOException ignored) { }
        }
    }

    private static void send(TransferWireProtocol.Request request) {
        try {
            LazyBuilderClientNetworking.sendTransfer(TransferWireProtocol.encodeRequest(request));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode transfer request", exception);
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

    private static void clientExecute(Runnable task) {
        MinecraftClient.getInstance().execute(task);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return Objects.toString(current.getMessage(), current.getClass().getSimpleName());
    }

    private record PreparedUpload(Path path, long size, String sha256) {}
    private record Upload(Path source, TransferDescriptor descriptor, int nextChunk, long sentBytes) {}
    private record Download(
            String fileName,
            Path target,
            Path partial,
            TransferDescriptor descriptor,
            boolean awaitingFinishAck
    ) {}
}

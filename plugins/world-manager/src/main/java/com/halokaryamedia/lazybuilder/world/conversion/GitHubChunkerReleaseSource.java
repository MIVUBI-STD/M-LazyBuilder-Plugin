package com.halokaryamedia.lazybuilder.world.conversion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stable-release source for the internal Chunker CLI runtime.
 *
 * <p>This adapter is only invoked by a user-triggered/lazy update check. It owns
 * no timer, polling loop, or background downloader.</p>
 */
public final class GitHubChunkerReleaseSource
        implements ConversionReleaseSource, ConversionReleaseSource.ArtifactDownloader {
    static final URI DEFAULT_LATEST_RELEASE = URI.create("https://api.github.com/repos/HiveGamesOSS/Chunker/releases/latest");
    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(3);

    private final HttpClient client;
    private final URI latestReleaseUri;

    public GitHubChunkerReleaseSource() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(15))
                .build(), DEFAULT_LATEST_RELEASE);
    }

    GitHubChunkerReleaseSource(HttpClient client, URI latestReleaseUri) {
        this.client = Objects.requireNonNull(client, "client");
        this.latestReleaseUri = Objects.requireNonNull(latestReleaseUri, "latestReleaseUri");
    }

    @Override
    public Optional<ConversionRelease> latestStable() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(latestReleaseUri)
                .timeout(METADATA_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "LazyBuilder-WorldManager")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return Optional.empty();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Conversion runtime release metadata request failed with HTTP " + response.statusCode());
            }
            return parseStableRelease(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking conversion runtime release metadata", interrupted);
        } catch (RuntimeException malformed) {
            throw new IOException("Invalid conversion runtime release metadata", malformed);
        }
    }

    @Override
    public Path download(ConversionRelease release, Path destinationDirectory) throws IOException {
        Objects.requireNonNull(release, "release");
        Path root = Objects.requireNonNull(destinationDirectory, "destinationDirectory").toAbsolutePath().normalize();
        Files.createDirectories(root);
        String finalName = "converter-" + safeVersion(release.version()) + ".jar";
        Path destination = root.resolve(finalName).normalize();
        if (!root.equals(destination.getParent())) throw new IOException("Runtime download path escaped destination root");
        Path temporary = root.resolve("download-" + UUID.randomUUID() + ".tmp");

        HttpRequest request = HttpRequest.newBuilder(release.artifactUri())
                .timeout(DOWNLOAD_TIMEOUT)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "LazyBuilder-WorldManager")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream ignored = response.body()) {
                    // Close response stream before returning the failure.
                }
                throw new IOException("Conversion runtime download failed with HTTP " + response.statusCode());
            }
            try (InputStream in = response.body()) {
                Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            moveIntoPlace(temporary, destination);
            return destination;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading conversion runtime", interrupted);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Optional<ConversionRelease> parseStableRelease(String json) throws IOException {
        JsonObject root;
        try {
            root = JsonParser.parseString(Objects.requireNonNull(json, "json")).getAsJsonObject();
        } catch (RuntimeException malformed) {
            throw new IOException("Release metadata is not valid JSON", malformed);
        }
        if (booleanValue(root, "draft") || booleanValue(root, "prerelease")) return Optional.empty();

        String version = requiredString(root, "tag_name").strip();
        String expectedAsset = "chunker-cli-" + version + ".jar";
        JsonArray assets = requiredArray(root, "assets");
        for (JsonElement item : assets) {
            if (!item.isJsonObject()) continue;
            JsonObject asset = item.getAsJsonObject();
            if (!expectedAsset.equals(stringValue(asset, "name"))) continue;
            String digest = requiredString(asset, "digest").strip().toLowerCase(Locale.ROOT);
            if (!digest.startsWith("sha256:")) {
                throw new IOException("Stable CLI asset does not provide a SHA-256 digest");
            }
            URI uri;
            try {
                uri = URI.create(requiredString(asset, "browser_download_url"));
            } catch (IllegalArgumentException invalidUri) {
                throw new IOException("Stable CLI asset has an invalid download URL", invalidUri);
            }
            return Optional.of(new ConversionRelease(version, uri, digest.substring("sha256:".length())));
        }
        throw new IOException("Stable Chunker release does not contain expected CLI artifact " + expectedAsset);
    }

    private static boolean booleanValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static String requiredString(JsonObject object, String key) throws IOException {
        String value = stringValue(object, key);
        if (value == null || value.isBlank()) throw new IOException("Missing release metadata property: " + key);
        return value;
    }

    private static JsonArray requiredArray(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) throw new IOException("Missing release metadata array: " + key);
        return value.getAsJsonArray();
    }

    private static String safeVersion(String version) {
        if (!version.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("Unsafe runtime version: " + version);
        return version;
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

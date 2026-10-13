package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubChunkerReleaseSourceTest {
    @TempDir Path tempDir;

    @Test
    void downloadPublicationNeverClobbersAnExistingFile() throws Exception {
        Path incoming = tempDir.resolve("download.tmp");
        Path existing = tempDir.resolve("converter.jar");
        Files.writeString(incoming, "new bytes");
        Files.writeString(existing, "preserve existing");

        assertThrows(FileAlreadyExistsException.class,
                () -> GitHubChunkerReleaseSource.moveIntoPlace(incoming, existing));
        assertEquals("preserve existing", Files.readString(existing));
        assertEquals("new bytes", Files.readString(incoming));
    }

    @Test
    void downloadPublicationUsesItsNewPathOnly() throws Exception {
        Path incoming = tempDir.resolve("download.tmp");
        Path destination = tempDir.resolve("converter-unique.jar");
        Files.writeString(incoming, "completed bytes");

        GitHubChunkerReleaseSource.moveIntoPlace(incoming, destination);

        assertEquals("completed bytes", Files.readString(destination));
        assertTrue(Files.notExists(incoming));
    }

    @Test
    void selectsExactCliJarAndReleaseDigest() throws Exception {
        String json = """
                {
                  "tag_name": "1.20.0",
                  "draft": false,
                  "prerelease": false,
                  "assets": [
                    {
                      "name": "Chunker-1.20.0-windows-x86.exe",
                      "digest": "sha256:b28233de731d04517528d8b021a42f4df86280d0dadd02ae420759694d4db6e5",
                      "browser_download_url": "https://example.invalid/gui.exe"
                    },
                    {
                      "name": "chunker-cli-1.20.0.jar",
                      "digest": "sha256:41efb80bba57c4eb08a810e5cc121211e2c0585aa8ff6eaf63d949782ecea502",
                      "browser_download_url": "https://example.invalid/chunker-cli-1.20.0.jar"
                    }
                  ]
                }
                """;

        ConversionRelease release = GitHubChunkerReleaseSource.parseStableRelease(json).orElseThrow();
        assertEquals("1.20.0", release.version());
        assertEquals("41efb80bba57c4eb08a810e5cc121211e2c0585aa8ff6eaf63d949782ecea502", release.sha256());
        assertEquals("https://example.invalid/chunker-cli-1.20.0.jar", release.artifactUri().toString());
    }

    @Test
    void boundedCopyRejectsOversizedRuntimeBeforeUnboundedWrite() throws Exception {
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        IOException error = assertThrows(IOException.class, () ->
                GitHubChunkerReleaseSource.copyBounded(
                        new ByteArrayInputStream(payload),
                        output,
                        4,
                        "test artifact"
                ));
        assertTrue(error.getMessage().contains("safety limit"));
        assertEquals(0, output.size(), "oversized read must fail before writing that chunk");
    }

    @Test
    void rejectsPrereleaseAsStableRuntime() throws Exception {
        String json = """
                {"tag_name":"2.0.0-rc1","draft":false,"prerelease":true,"assets":[]}
                """;

        Optional<ConversionRelease> release = GitHubChunkerReleaseSource.parseStableRelease(json);
        assertTrue(release.isEmpty());
    }
}

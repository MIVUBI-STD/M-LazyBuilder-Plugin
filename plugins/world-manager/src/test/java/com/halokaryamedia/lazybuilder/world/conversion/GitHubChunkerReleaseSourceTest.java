package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubChunkerReleaseSourceTest {
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
        assertEquals(4, output.size());
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

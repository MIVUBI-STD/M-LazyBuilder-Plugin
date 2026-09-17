package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PerformanceMixinRegistrationTest {
    @Test
    void performanceMixinsStayRegisteredInClientRuntime() throws IOException {
        try (var stream = PerformanceMixinRegistrationTest.class.getClassLoader()
                .getResourceAsStream("lazybuilder-performance-manager.mixins.json")) {
            assertNotNull(stream, "Performance mixin configuration must be packaged in the mod JAR");
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("WorldRendererMixin"), "entity culling mixin must stay registered");
            assertTrue(json.contains("BlockEntityRenderDispatcherMixin"), "block entity culling mixin must stay registered");
            assertTrue(json.contains("TextRendererDrawerMixin"), "text render lookup mixin must stay registered");
            assertTrue(json.contains("VertexBufferMixin"), "GPU buffer resize mixin must stay registered");
            assertTrue(json.contains("ChunkBuilderBuiltChunkMixin"), "chunk rebuild coalescing mixin must stay registered");
            assertTrue(json.contains("ChunkBuilderUploadMixin"), "chunk upload batching mixin must stay registered");
            assertTrue(json.contains("BuiltChunkStorageMixin"), "render-region ring storage mixin must stay registered");
            assertTrue(json.contains("BlockBufferBuilderPoolMixin"), "chunk buffer pool metrics mixin must stay registered");
            assertTrue(json.contains("BlockColorsMixin"), "block color provider cache mixin must stay registered");
            assertTrue(json.contains("BlockSideVisibilityMixin"), "block side visibility cache mixin must stay registered");
            assertTrue(json.contains("BakedQuadAccessor"), "baked quad memory accessor must stay registered");
            assertTrue(json.contains("BasicBakedModelBuilderMixin"), "baked quad dedup mixin must stay registered");
            assertTrue(json.contains("PerformanceMixinPlugin"), "migration compatibility plugin must stay registered");
            assertTrue(json.contains("\"required\": true"), "performance mixin failures must fail loudly");
            assertTrue(json.contains("\"defaultRequire\": 1"), "performance injections must require their target");
        }
    }
}

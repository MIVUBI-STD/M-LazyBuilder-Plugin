package com.halokaryamedia.lazybuilder.utility;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilityMixinRegistrationTest {
    @Test
    void utilityMixinsStayRegisteredInClientRuntime() throws IOException {
        try (var stream = UtilityMixinRegistrationTest.class.getClassLoader()
                .getResourceAsStream("lazybuilder-utility-manager.mixins.json")) {
            assertNotNull(stream, "Utility mixin configuration must be packaged in the mod JAR");
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("ConnectScreenMixin"), "connect capture mixin must stay registered");
            assertTrue(json.contains("ChatHudMixin"), "chat presentation/history mixin must stay registered");
            assertTrue(json.contains("ChatHudAccessor"), "chat context-menu accessor must stay registered");
            assertTrue(json.contains("ChatScreenMixin"), "chat search/context mixin must stay registered");
            assertTrue(json.contains("CreativeInventoryScreenMixin"), "creative instant-search mixin must stay registered");
            assertTrue(json.contains("DebugHudMixin"), "compact F3 renderer mixin must stay registered");
            assertTrue(json.contains("KeyboardMixin"), "compact Alt interaction keyboard mixin must stay registered");
            assertTrue(json.contains("CompactDebugMouseMixin"), "compact coordinate pointer mixin must stay registered");
            assertTrue(json.contains("DisconnectedScreenMixin"), "disconnect action mixin must stay registered");
            assertTrue(json.contains("MultiplayerScreenMixin"), "server-list reconnect fallback must stay registered");
            assertTrue(json.contains("\"required\": true"), "mixin failures must fail loudly instead of silently disabling Utility Manager behavior");
        }
    }
}

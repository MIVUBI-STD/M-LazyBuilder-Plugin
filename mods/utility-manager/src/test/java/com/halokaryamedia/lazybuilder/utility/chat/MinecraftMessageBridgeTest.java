package com.halokaryamedia.lazybuilder.utility.chat;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class MinecraftMessageBridgeTest {
    @Test
    void plainRecognizedGameTextMayBeCompacted() {
        Text original = Text.literal("Berchman joined the game");

        Text transformed = MinecraftMessageBridge.transformMessage(original, false);

        assertEquals("Berchman joined", transformed.getString());
    }

    @Test
    void styledRecognizedTextIsPreservedInsteadOfFlattened() {
        Text original = Text.literal("Berchman joined the game").formatted(Formatting.GOLD);

        Text transformed = MinecraftMessageBridge.transformMessage(original, false);

        assertSame(original, transformed);
    }

    @Test
    void translatableTextIsPreservedInsteadOfFlattened() {
        Text original = Text.translatable("multiplayer.player.joined", Text.literal("Berchman"));

        Text transformed = MinecraftMessageBridge.transformMessage(original, false);

        assertSame(original, transformed);
    }

    @Test
    void actionBarTextIsNeverModified() {
        Text original = Text.literal("Berchman joined the game");

        Text transformed = MinecraftMessageBridge.transformMessage(original, true);

        assertSame(original, transformed);
    }
}

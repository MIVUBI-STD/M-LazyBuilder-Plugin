package com.halokaryamedia.lazybuilder.builder.wire;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class BuilderExtensionTransportTest {
    @Test
    void smallMessagePassesThroughRaw() throws Exception {
        byte[] message = new byte[]{0, 3, 1, 2, 3};
        var fragments = BuilderExtensionTransport.fragment(message);
        assertEquals(1, fragments.size());
        assertArrayEquals(message, fragments.get(0));

        var reassembler = new BuilderExtensionTransport.Reassembler();
        assertArrayEquals(message, reassembler.accept(message).orElseThrow());
    }

    @Test
    void largeMessageReassemblesOutOfOrderAndWithinPaperLimit() throws Exception {
        byte[] message = new byte[BuilderExtensionWireProtocol.MAX_MESSAGE_BYTES];
        for (int i = 0; i < message.length; i++) message[i] = (byte) (i * 31);
        message[0] = 0;
        message[1] = 3;

        var fragments = new ArrayList<>(BuilderExtensionTransport.fragment(message));
        assertTrue(fragments.size() > 1);
        assertTrue(fragments.stream().allMatch(
                f -> f.length <= BuilderExtensionTransport.MAX_PLUGIN_MESSAGE_BYTES));
        Collections.reverse(fragments);

        var reassembler = new BuilderExtensionTransport.Reassembler();
        byte[] result = null;
        for (byte[] fragment : fragments) {
            var completed = reassembler.accept(fragment);
            if (completed.isPresent()) result = completed.get();
        }
        assertArrayEquals(message, result);
    }
}

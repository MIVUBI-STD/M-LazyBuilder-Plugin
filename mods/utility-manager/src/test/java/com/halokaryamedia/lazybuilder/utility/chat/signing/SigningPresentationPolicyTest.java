package com.halokaryamedia.lazybuilder.utility.chat.signing;

import net.minecraft.client.gui.hud.MessageIndicator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SigningPresentationPolicyTest {
    @Test
    void hiddenIndicatorReturnsNullWithoutMutatingSource() {
        MessageIndicator indicator = MessageIndicator.system();
        assertNull(SigningPresentationPolicy.visibleIndicator(indicator, true));
    }

    @Test
    void visibleIndicatorKeepsVanillaInstance() {
        MessageIndicator indicator = MessageIndicator.system();
        assertSame(indicator, SigningPresentationPolicy.visibleIndicator(indicator, false));
    }
}

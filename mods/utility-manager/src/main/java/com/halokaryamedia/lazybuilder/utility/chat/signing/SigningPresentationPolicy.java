package com.halokaryamedia.lazybuilder.utility.chat.signing;

import net.minecraft.client.gui.hud.MessageIndicator;

/**
 * Presentation-only secure-chat policy.
 *
 * This class intentionally never mutates signatures, reportability, packets, or
 * server negotiation. It owns only whether Minecraft's per-line signing indicator
 * is shown in the local HUD.
 */
public final class SigningPresentationPolicy {
    private SigningPresentationPolicy() {
    }

    public static MessageIndicator visibleIndicator(MessageIndicator indicator, boolean hideIndicator) {
        return hideIndicator ? null : indicator;
    }
}

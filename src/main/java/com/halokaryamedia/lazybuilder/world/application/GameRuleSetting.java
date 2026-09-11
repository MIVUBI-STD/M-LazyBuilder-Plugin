package com.halokaryamedia.lazybuilder.world.application;

import java.util.Objects;

/** One gamerule exposed from the active Paper API. */
public record GameRuleSetting(String name, GameRuleValueType type, String value) {
    public GameRuleSetting {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }
}

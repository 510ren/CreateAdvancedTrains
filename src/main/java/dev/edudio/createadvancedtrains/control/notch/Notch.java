package dev.edudio.createadvancedtrains.control.notch;

import java.util.Locale;
import java.util.Optional;

/**
 * Discrete notch values used by the Phase 5A profile and response model.
 */
public enum Notch {
    COAST,
    B1,
    B2,
    B3,
    B4,
    B5,
    B6,
    B7;

    public boolean isServiceBrake() {
        return this != COAST;
    }

    public static Optional<Notch> parseServiceBrake(String value) {
        if (value == null) {
            return Optional.empty();
        }

        try {
            Notch notch = valueOf(value.trim().toUpperCase(Locale.ROOT));
            return notch.isServiceBrake() ? Optional.of(notch) : Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}

package dev.edudio.createadvancedtrains.control.notch;

import java.util.Locale;
import java.util.Optional;

/**
 * Discrete product P/N/B notches.
 */
public enum Notch {
    P1,
    P2,
    P3,
    P4,
    P5,
    N,
    B1,
    B2,
    B3,
    B4,
    B5,
    B6,
    B7;

    public boolean isServiceBrake() {
        return ordinal() >= B1.ordinal();
    }

    public boolean isPower() {
        return ordinal() >= P1.ordinal() && ordinal() <= P5.ordinal();
    }

    public boolean isNeutral() {
        return this == N;
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

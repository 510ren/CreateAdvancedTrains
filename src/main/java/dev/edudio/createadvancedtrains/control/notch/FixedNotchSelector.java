package dev.edudio.createadvancedtrains.control.notch;

import java.util.Objects;

/**
 * Latches one service-brake notch for a complete Phase 5A test session.
 */
public final class FixedNotchSelector {

    private final Notch fixedNotch;

    public FixedNotchSelector(Notch fixedNotch) {
        this.fixedNotch = Objects.requireNonNull(fixedNotch, "fixedNotch");
        if (!fixedNotch.isServiceBrake()) {
            throw new IllegalArgumentException("FIXED_FOR_TEST requires B1 through B7");
        }
    }

    public Notch select() {
        return fixedNotch;
    }
}

package dev.edudio.createadvancedtrains.train.error;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of recovery-condition checks performed by a future wrench handler.
 */
public record CatErrorRecoveryValidation(
        boolean recoveryAllowed,
        Optional<String> failureReason) {

    public CatErrorRecoveryValidation {
        Objects.requireNonNull(failureReason, "failureReason");
        if (recoveryAllowed == failureReason.isPresent()) {
            throw new IllegalArgumentException("Allowed recovery has no failure reason; rejected recovery requires one");
        }
    }

    public static CatErrorRecoveryValidation allowed() {
        return new CatErrorRecoveryValidation(true, Optional.empty());
    }

    public static CatErrorRecoveryValidation rejected(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason must not be blank");
        }
        return new CatErrorRecoveryValidation(false, Optional.of(failureReason));
    }
}

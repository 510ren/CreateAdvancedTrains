package dev.edudio.createadvancedtrains.train.error;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable server-side display data for a future controller or HUD.
 */
public record CatCalculationErrorSnapshot(
        boolean active,
        Optional<CatCalculationErrorCode> code,
        Optional<String> reason,
        Optional<String> lastRecoveryFailureReason) {

    public CatCalculationErrorSnapshot {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(lastRecoveryFailureReason, "lastRecoveryFailureReason");
        if (active != code.isPresent() || active != reason.isPresent()) {
            throw new IllegalArgumentException("Active error state must have a code and reason");
        }
    }

    public static CatCalculationErrorSnapshot inactive() {
        return new CatCalculationErrorSnapshot(
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}

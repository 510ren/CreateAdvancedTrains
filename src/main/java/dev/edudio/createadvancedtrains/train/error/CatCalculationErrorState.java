package dev.edudio.createadvancedtrains.train.error;

import java.util.Objects;
import java.util.Optional;

/**
 * Latched per-train CAT calculation error. It never clears automatically.
 */
public final class CatCalculationErrorState {

    private CatCalculationErrorCode code;
    private String reason;
    private String lastRecoveryFailureReason;

    public void latch(CatCalculationErrorCode newCode, String newReason) {
        Objects.requireNonNull(newCode, "newCode");
        if (newReason == null || newReason.isBlank()) {
            throw new IllegalArgumentException("newReason must not be blank");
        }
        if (code != null) {
            return;
        }
        code = newCode;
        reason = newReason;
        lastRecoveryFailureReason = null;
    }

    public boolean isActive() {
        return code != null;
    }

    public CatCalculationErrorSnapshot snapshot() {
        if (!isActive()) {
            return CatCalculationErrorSnapshot.inactive();
        }
        return new CatCalculationErrorSnapshot(
                true,
                Optional.of(code),
                Optional.of(reason),
                Optional.ofNullable(lastRecoveryFailureReason));
    }

    /**
     * May only be called by the future server-side wrench recovery boundary,
     * after it has evaluated the recovery conditions.
     */
    public CatErrorRecoveryResult recoverAfterWrench(CatErrorRecoveryValidation validation) {
        Objects.requireNonNull(validation, "validation");
        if (!isActive()) {
            return new CatErrorRecoveryResult(
                    CatErrorRecoveryResult.Status.NO_ERROR,
                    Optional.empty());
        }
        if (!validation.recoveryAllowed()) {
            lastRecoveryFailureReason = validation.failureReason().orElseThrow();
            return new CatErrorRecoveryResult(
                    CatErrorRecoveryResult.Status.REJECTED,
                    Optional.of(lastRecoveryFailureReason));
        }

        code = null;
        reason = null;
        lastRecoveryFailureReason = null;
        return new CatErrorRecoveryResult(
                CatErrorRecoveryResult.Status.RECOVERED,
                Optional.empty());
    }
}

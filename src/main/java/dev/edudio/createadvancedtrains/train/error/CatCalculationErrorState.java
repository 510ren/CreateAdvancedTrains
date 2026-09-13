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

    /**
     * 仕様書に独立した関数契約がないため、{@code latch}が示す状態を保持状態へ記録します。
     * @param newCode 仕様書に個別説明がないため、現在の処理内容から推定した、{@code newCode}として使用される入力値。
     * @param newReason 仕様書に個別説明がないため、{@code newReason}が示す理由または診断情報。
     */
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

    /**
     * Activeに関する条件を判定します。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    public boolean isActive() {
        return code != null;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code snapshot}としてまとめられている処理を実行します。
     * @return 処理によって得られた結果。
     */
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
     * @param validation 復旧可否を判定する検証結果。
     * @return 処理によって得られた結果。
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

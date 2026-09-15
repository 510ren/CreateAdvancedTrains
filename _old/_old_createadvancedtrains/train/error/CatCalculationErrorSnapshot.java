package dev.edudio.createadvancedtrains.train.error;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable server-side display data for a future controller or HUD.
 * @param active 仕様書に個別説明がないため、{@code active}が示す条件の有効・無効を表す値。
 * @param code 計算エラーの分類コード。
 * @param reason 処理を行う理由を表す文字列。
 * @param lastRecoveryFailureReason 仕様書に個別説明がないため、{@code lastRecoveryFailureReason}が示す理由または診断情報。
 */
public record CatCalculationErrorSnapshot(
        boolean active,
        Optional<CatCalculationErrorCode> code,
        Optional<String> reason,
        Optional<String> lastRecoveryFailureReason) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param active 仕様書に個別説明がないため、{@code active}が示す条件の有効・無効を表す値。
     * @param code 計算エラーの分類コード。
     * @param reason 処理を行う理由を表す文字列。
     * @param lastRecoveryFailureReason 仕様書に個別説明がないため、{@code lastRecoveryFailureReason}が示す理由または診断情報。
     */
    public CatCalculationErrorSnapshot {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(lastRecoveryFailureReason, "lastRecoveryFailureReason");
        if (active != code.isPresent() || active != reason.isPresent()) {
            throw new IllegalArgumentException("Active error state must have a code and reason");
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code inactive}が示す状態の結果オブジェクトを生成します。
     * @return 処理によって得られた結果。
     */
    public static CatCalculationErrorSnapshot inactive() {
        return new CatCalculationErrorSnapshot(
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}

package dev.edudio.createadvancedtrains.train.error;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of recovery-condition checks performed by a future wrench handler.
 * @param recoveryAllowed 仕様書に個別説明がないため、{@code recoveryAllowed}が示す条件の有効・無効を表す値。
 * @param failureReason 仕様書に個別説明がないため、{@code failureReason}が示す理由または診断情報。
 */
public record CatErrorRecoveryValidation(
        boolean recoveryAllowed,
        Optional<String> failureReason) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param recoveryAllowed 仕様書に個別説明がないため、{@code recoveryAllowed}が示す条件の有効・無効を表す値。
     * @param failureReason 仕様書に個別説明がないため、{@code failureReason}が示す理由または診断情報。
     */
    public CatErrorRecoveryValidation {
        Objects.requireNonNull(failureReason, "failureReason");
        if (recoveryAllowed == failureReason.isPresent()) {
            throw new IllegalArgumentException("Allowed recovery has no failure reason; rejected recovery requires one");
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code allowed}が示す状態の結果オブジェクトを生成します。
     * @return 処理によって得られた結果。
     */
    public static CatErrorRecoveryValidation allowed() {
        return new CatErrorRecoveryValidation(true, Optional.empty());
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code rejected}が示す状態の結果オブジェクトを生成します。
     * @param failureReason 仕様書に個別説明がないため、{@code failureReason}が示す理由または診断情報。
     * @return 処理によって得られた結果。
     */
    public static CatErrorRecoveryValidation rejected(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason must not be blank");
        }
        return new CatErrorRecoveryValidation(false, Optional.of(failureReason));
    }
}

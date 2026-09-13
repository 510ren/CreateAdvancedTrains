package dev.edudio.createadvancedtrains.control;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Distinguishes a calculated CAT target from intentionally emitting no new
 * automatic acceleration or service-brake intent.
 * @param state 仕様書に個別説明がないため、{@code state}が示す現在または判定後の状態。
 * @param targetSpeed 仕様書に個別説明がないため、{@code targetSpeed}が示す速度値。単位は呼出元の境界定義に従います。
 */
public record AtoTargetSpeedDecision(
        AtoOperatingState state,
        OptionalDouble targetSpeed) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param state 仕様書に個別説明がないため、{@code state}が示す現在または判定後の状態。
     * @param targetSpeed 仕様書に個別説明がないため、{@code targetSpeed}が示す速度値。単位は呼出元の境界定義に従います。
     */
    public AtoTargetSpeedDecision {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(targetSpeed, "targetSpeed");
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code target}が示す状態の結果オブジェクトを生成します。
     * @param targetSpeed 仕様書に個別説明がないため、{@code targetSpeed}が示す速度値。単位は呼出元の境界定義に従います。
     * @return 処理によって得られた結果。
     */
    public static AtoTargetSpeedDecision target(double targetSpeed) {
        if (!Double.isFinite(targetSpeed)) {
            throw new IllegalArgumentException("targetSpeed must be finite");
        }
        return new AtoTargetSpeedDecision(
                AtoOperatingState.ACTIVE,
                OptionalDouble.of(targetSpeed));
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code suppressed}が示す状態の結果オブジェクトを生成します。
     * @param state 仕様書に個別説明がないため、{@code state}が示す現在または判定後の状態。
     * @return 処理によって得られた結果。
     */
    public static AtoTargetSpeedDecision suppressed(AtoOperatingState state) {
        if (state == AtoOperatingState.ACTIVE) {
            throw new IllegalArgumentException("ACTIVE must provide a target speed");
        }
        return new AtoTargetSpeedDecision(state, OptionalDouble.empty());
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code emitsTargetSpeedIntent}が示す条件を判定します。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    public boolean emitsTargetSpeedIntent() {
        return targetSpeed.isPresent();
    }
}

package dev.edudio.createadvancedtrains.debug.notchtest;

/**
 * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
 * @param state 仕様書に個別説明がないため、{@code state}が示す現在または判定後の状態。
 * @param overrideApplied 仕様書に個別説明がないため、{@code overrideApplied}が示す条件の有効・無効を表す値。
 * @param triggerReason 仕様書に個別説明がないため、{@code triggerReason}が示す理由または診断情報。
 */
public record StopTargetHoldResult(
        State state,
        boolean overrideApplied,
        String triggerReason) {

    private static final StopTargetHoldResult INACTIVE =
            new StopTargetHoldResult(State.INACTIVE, false, null);
    private static final StopTargetHoldResult RELEASED =
            new StopTargetHoldResult(State.RELEASED, false, null);

    /**
     * 仕様書に独立した関数契約がないため、{@code inactive}が示す状態の結果オブジェクトを生成します。
     * @return 処理によって得られた結果。
     */
    static StopTargetHoldResult inactive() {
        return INACTIVE;
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code latched}が示す状態を保持状態へ記録します。
     * @param triggerReason 仕様書に個別説明がないため、{@code triggerReason}が示す理由または診断情報。
     * @return 処理によって得られた結果。
     */
    static StopTargetHoldResult latched(String triggerReason) {
        return new StopTargetHoldResult(State.LATCHED, true, triggerReason);
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code released}が示す状態の結果オブジェクトを生成します。
     * @return 処理によって得られた結果。
     */
    static StopTargetHoldResult released() {
        return RELEASED;
    }

    public enum State {
        INACTIVE,
        LATCHED,
        RELEASED
    }
}

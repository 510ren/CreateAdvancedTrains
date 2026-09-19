package dev.edudio.createadvancedtrains.control.notch;

import java.util.Objects;

/**
 * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
 * @param finalTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code finalTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
 * @param accelerationMod Createへ渡される加速度倍率。
 * @param response 仕様書に個別説明がないため、{@code response}が示す計算・判定・観測結果。
 * @param suspended 仕様書に個別説明がないため、{@code suspended}が示す条件の有効・無効を表す値。
 */
public record NotchControlResult(
        double finalTargetSpeedBlocksPerTick,
        float accelerationMod,
        NotchResponseModel.Response response,
        boolean suspended) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param finalTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code finalTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @param accelerationMod Createへ渡される加速度倍率。
     * @param response 仕様書に個別説明がないため、{@code response}が示す計算・判定・観測結果。
     * @param suspended 仕様書に個別説明がないため、{@code suspended}が示す条件の有効・無効を表す値。
     */
    public NotchControlResult {
        Objects.requireNonNull(response, "response");
        if (!Double.isFinite(finalTargetSpeedBlocksPerTick)
                || !Float.isFinite(accelerationMod)
                || accelerationMod < 0.0f) {
            throw new IllegalArgumentException("Create boundary result is invalid");
        }
    }
}

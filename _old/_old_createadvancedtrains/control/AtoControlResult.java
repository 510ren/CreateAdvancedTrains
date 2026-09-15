package dev.edudio.createadvancedtrains.control;

import java.util.Objects;
import java.util.Optional;

import dev.edudio.createadvancedtrains.control.notch.NotchControlResult;
import dev.edudio.createadvancedtrains.control.notch.Notch;
import dev.edudio.createadvancedtrains.control.notch.NotchSelection;
import dev.edudio.createadvancedtrains.control.speed.TargetSpeedResolution;

/**
 * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
 * @param finalTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code finalTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
 * @param accelerationMod Createへ渡される加速度倍率。
 * @param operatingState 仕様書に個別説明がないため、{@code operatingState}が示す現在または判定後の状態。
 * @param suspended 仕様書に個別説明がないため、{@code suspended}が示す条件の有効・無効を表す値。
 * @param responseAdvanced 仕様書に個別説明がないため、{@code responseAdvanced}が示す計算・判定・観測結果。
 * @param previousRequestedNotch 仕様書に個別説明がないため、{@code previousRequestedNotch}が示すノッチ状態またはノッチ候補。
 * @param resolution 仕様書に個別説明がないため、{@code resolution}が示す計算・判定・観測結果。
 * @param notchSelection 仕様書に個別説明がないため、{@code notchSelection}が示す計算・判定・観測結果。
 * @param notchControl 仕様書に個別説明がないため、現在の処理内容から推定した、{@code notchControl}として使用される入力値。
 */
public record AtoControlResult(
        double finalTargetSpeedBlocksPerTick,
        float accelerationMod,
        AtoOperatingState operatingState,
        boolean suspended,
        boolean responseAdvanced,
        Optional<Notch> previousRequestedNotch,
        Optional<TargetSpeedResolution> resolution,
        Optional<NotchSelection> notchSelection,
        Optional<NotchControlResult> notchControl) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param finalTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code finalTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @param accelerationMod Createへ渡される加速度倍率。
     * @param operatingState 仕様書に個別説明がないため、{@code operatingState}が示す現在または判定後の状態。
     * @param suspended 仕様書に個別説明がないため、{@code suspended}が示す条件の有効・無効を表す値。
     * @param responseAdvanced 仕様書に個別説明がないため、{@code responseAdvanced}が示す計算・判定・観測結果。
     * @param previousRequestedNotch 仕様書に個別説明がないため、{@code previousRequestedNotch}が示すノッチ状態またはノッチ候補。
     * @param resolution 仕様書に個別説明がないため、{@code resolution}が示す計算・判定・観測結果。
     * @param notchSelection 仕様書に個別説明がないため、{@code notchSelection}が示す計算・判定・観測結果。
     * @param notchControl 仕様書に個別説明がないため、現在の処理内容から推定した、{@code notchControl}として使用される入力値。
     */
    public AtoControlResult {
        Objects.requireNonNull(operatingState, "operatingState");
        previousRequestedNotch = Objects.requireNonNull(previousRequestedNotch, "previousRequestedNotch");
        resolution = Objects.requireNonNull(resolution, "resolution");
        notchSelection = Objects.requireNonNull(notchSelection, "notchSelection");
        notchControl = Objects.requireNonNull(notchControl, "notchControl");
        if (!suspended && (!Double.isFinite(finalTargetSpeedBlocksPerTick)
                || !Float.isFinite(accelerationMod)
                || accelerationMod < 0.0f)) {
            throw new IllegalArgumentException("ATO Create-boundary result is invalid");
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code passThrough}としてまとめられている処理を実行します。
     * @param nativeTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code nativeTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @param originalAccelerationMod CATが変更する前のCreate加速度倍率。
     * @param state 仕様書に個別説明がないため、{@code state}が示す現在または判定後の状態。
     * @param suspended 仕様書に個別説明がないため、{@code suspended}が示す条件の有効・無効を表す値。
     * @return 処理によって得られた結果。
     */
    public static AtoControlResult passThrough(
            double nativeTargetSpeedBlocksPerTick,
            float originalAccelerationMod,
            AtoOperatingState state,
            boolean suspended) {
        return new AtoControlResult(
                nativeTargetSpeedBlocksPerTick,
                originalAccelerationMod,
                state,
                suspended,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}

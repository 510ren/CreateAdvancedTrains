package dev.edudio.createadvancedtrains.control.notch;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;

import java.util.Objects;
import java.util.Optional;

/** Owns the response state and the minimal Create target/modifier boundary. */
public final class NotchController {

    private final NotchProfile profile;
    private final NotchResponseModel responseModel;
    private NotchResponseModel.Response lastResponse;

    /**
     * このクラスのインスタンスを初期化します。
     * @param profile 仕様書に個別説明がないため、{@code profile}が示すノッチまたは制動特性。
     */
    public NotchController(NotchProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.responseModel = new NotchResponseModel();
        this.lastResponse = NotchResponseModel.neutralResponse();
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code advance}としてまとめられている処理を実行します。
     * @param requestedNotch 仕様書に個別説明がないため、{@code requestedNotch}が示すノッチ状態またはノッチ候補。
     * @param currentSpeedBlocksPerSecond 現在速度の大きさ。単位はblocks/s。
     * @param baseAccelerationBlocksPerSecondSquared Create基本加速度の大きさ。単位はblocks/s^2。
     * @return 処理によって得られた結果。
     */
    public NotchResponseModel.Response advance(
            Notch requestedNotch,
            double currentSpeedBlocksPerSecond,
            double baseAccelerationBlocksPerSecondSquared) {
        double targetAcceleration = profile.targetAcceleration(
                requestedNotch,
                currentSpeedBlocksPerSecond,
                baseAccelerationBlocksPerSecondSquared);
        lastResponse = responseModel.step(requestedNotch, targetAcceleration);
        return lastResponse;
    }

    /**
     * 仕様書に独立した関数契約がないため、入力へ{@code applyBoundary}が示す境界処理を適用します。
     * @param signedResolvedSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code signedResolvedSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
     * @param currentSpeedBlocksPerTick 仕様書に個別説明がないため、{@code currentSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @param baseAccelerationBlocksPerSecondSquared Create基本加速度の大きさ。単位はblocks/s^2。
     * @param effectiveAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code effectiveAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
     * @return 処理によって得られた結果。
     */
    public NotchControlResult applyBoundary(
            double signedResolvedSpeedBlocksPerSecond,
            double currentSpeedBlocksPerTick,
            double baseAccelerationBlocksPerSecondSquared,
            double effectiveAccelerationBlocksPerSecondSquared) {
        if (!Double.isFinite(signedResolvedSpeedBlocksPerSecond)
                || !Double.isFinite(currentSpeedBlocksPerTick)
                || !Double.isFinite(baseAccelerationBlocksPerSecondSquared)
                || baseAccelerationBlocksPerSecondSquared <= 0.0
                || !Double.isFinite(effectiveAccelerationBlocksPerSecondSquared)) {
            throw new IllegalArgumentException("Create boundary values are invalid");
        }
        double targetBlocksPerTick;
        if (effectiveAccelerationBlocksPerSecondSquared > 0.0) {
            targetBlocksPerTick = signedResolvedSpeedBlocksPerSecond / TICKS_PER_SECOND;
        } else if (effectiveAccelerationBlocksPerSecondSquared < 0.0) {
            targetBlocksPerTick = 0.0;
        } else {
            targetBlocksPerTick = currentSpeedBlocksPerTick;
        }
        double modifier = Math.abs(effectiveAccelerationBlocksPerSecondSquared)
                / baseAccelerationBlocksPerSecondSquared;
        if (!Double.isFinite(modifier) || modifier > Float.MAX_VALUE) {
            throw new IllegalArgumentException("Calculated acceleration modifier is invalid");
        }
        return new NotchControlResult(
                targetBlocksPerTick,
                (float) modifier,
                lastResponse,
                false);
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code suspend}としてまとめられている処理を実行します。
     */
    public void suspend() {
        responseModel.reset();
        lastResponse = NotchResponseModel.neutralResponse();
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code effectiveAcceleration}としてまとめられている処理を実行します。
     * @return 処理または計算によって得られた数値。
     */
    public double effectiveAcceleration() {
        return responseModel.effectiveAcceleration();
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code requestedNotch}としてまとめられている処理を実行します。
     * @return 結果が存在する場合はその値、存在しない場合は空のOptional。
     */
    public Optional<Notch> requestedNotch() {
        return Optional.of(lastResponse.commandedNotch());
    }

    /**
     * 仕様書に独立した関数契約がないため、現在保持している{@code last response}を返します。
     * @return 処理によって得られた結果。
     */
    public NotchResponseModel.Response lastResponse() {
        return lastResponse;
    }
}

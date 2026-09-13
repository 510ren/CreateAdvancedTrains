package dev.edudio.createadvancedtrains.debug.notchtest;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;

import com.simibubi.create.content.trains.entity.Train;

final class StopTargetHoldLatch {

    static final double STOP_SPEED_THRESHOLD_BLOCKS_PER_SECOND = 0.01;
    static final String NAVIGATION_NATIVE_STOP_TARGET = "navigation_native_stop_target";

    private long releasePendingSinceServerTick = Long.MIN_VALUE;

    /**
     * このクラスのインスタンスを初期化します。
     */
    StopTargetHoldLatch() {
    }

    /**
     * Latchに関する条件を判定します。
     * @param train 対象となるCreate列車。
     * @param nativeTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code nativeTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    static boolean canLatch(Train train, double nativeTargetSpeedBlocksPerTick) {
        return isMoving(train)
                && train.navigation != null
                && train.navigation.destination != null
                && nativeTargetSpeedBlocksPerTick == 0.0;
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code shouldRelease}が示す条件を判定します。
     * @param train 対象となるCreate列車。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    static boolean shouldRelease(Train train) {
        double speedBlocksPerSecond = train.speed * TICKS_PER_SECOND;
        return !Double.isFinite(speedBlocksPerSecond)
                || Math.abs(speedBlocksPerSecond) < STOP_SPEED_THRESHOLD_BLOCKS_PER_SECOND;
    }

    /**
     * ReleasePendingに関する条件を判定します。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    boolean isReleasePending() {
        return releasePendingSinceServerTick != Long.MIN_VALUE;
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code markReleasePending}が示す状態を保持状態へ記録します。
     * @param serverTick 処理対象となるserver tick。
     */
    void markReleasePending(long serverTick) {
        if (!isReleasePending()) {
            releasePendingSinceServerTick = serverTick;
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code releaseWasPendingBefore}が示す条件を判定します。
     * @param serverTick 処理対象となるserver tick。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    boolean releaseWasPendingBefore(long serverTick) {
        return isReleasePending() && releasePendingSinceServerTick < serverTick;
    }

    /**
     * Movingに関する条件を判定します。
     * @param train 対象となるCreate列車。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    private static boolean isMoving(Train train) {
        double speedBlocksPerSecond = train.speed * TICKS_PER_SECOND;
        return Double.isFinite(speedBlocksPerSecond)
                && Math.abs(speedBlocksPerSecond) >= STOP_SPEED_THRESHOLD_BLOCKS_PER_SECOND;
    }
}

package dev.edudio.createadvancedtrains.debug.notchtest;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;

import com.simibubi.create.content.trains.entity.Train;

final class StopTargetHoldLatch {

    static final double STOP_SPEED_THRESHOLD_BLOCKS_PER_SECOND = 0.01;
    static final String NAVIGATION_NATIVE_STOP_TARGET = "navigation_native_stop_target";

    private long releasePendingSinceServerTick = Long.MIN_VALUE;

    StopTargetHoldLatch() {
    }

    static boolean canLatch(Train train, double nativeTargetSpeedBlocksPerTick) {
        return isMoving(train)
                && train.navigation != null
                && train.navigation.destination != null
                && nativeTargetSpeedBlocksPerTick == 0.0;
    }

    static boolean shouldRelease(Train train) {
        double speedBlocksPerSecond = train.speed * TICKS_PER_SECOND;
        return !Double.isFinite(speedBlocksPerSecond)
                || Math.abs(speedBlocksPerSecond) < STOP_SPEED_THRESHOLD_BLOCKS_PER_SECOND;
    }

    boolean isReleasePending() {
        return releasePendingSinceServerTick != Long.MIN_VALUE;
    }

    void markReleasePending(long serverTick) {
        if (!isReleasePending()) {
            releasePendingSinceServerTick = serverTick;
        }
    }

    boolean releaseWasPendingBefore(long serverTick) {
        return isReleasePending() && releasePendingSinceServerTick < serverTick;
    }

    private static boolean isMoving(Train train) {
        double speedBlocksPerSecond = train.speed * TICKS_PER_SECOND;
        return Double.isFinite(speedBlocksPerSecond)
                && Math.abs(speedBlocksPerSecond) >= STOP_SPEED_THRESHOLD_BLOCKS_PER_SECOND;
    }
}

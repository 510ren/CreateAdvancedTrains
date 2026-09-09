package dev.edudio.createadvancedtrains.debug.notchtest;

import java.util.List;
import java.util.UUID;

import dev.edudio.createadvancedtrains.control.notch.Notch;

record NotchTestSnapshot(
        long serverTick,
        UUID trainId,
        Notch fixedTestNotch,
        Notch commandedNotch,
        Notch appliedNotch,
        int transitionElapsedTicks,
        double transitionProgress,
        Double currentSpeedBlocksPerSecond,
        Double preCatTargetSpeedBlocksPerSecond,
        Double finalTargetSpeedBlocksPerSecond,
        Double baseAccelerationBlocksPerSecondSquared,
        Double profileTargetAccelerationBlocksPerSecondSquared,
        double effectiveAccelerationBlocksPerSecondSquared,
        Double measuredAccelerationBlocksPerSecondSquared,
        Double signedVelocityAccelerationBlocksPerSecondSquared,
        String applicationState,
        String applicationReason,
        boolean controlApplied,
        Float originalAccelerationMod,
        Float appliedAccelerationMod,
        int approachCallCount,
        int brakingDemandCallCount,
        int notchModifierAppliedCallCount,
        List<NotchTestApproachCall> approachCalls,
        NotchTestResponseState responseState,
        Double distanceToDestinationBlocks) {
}

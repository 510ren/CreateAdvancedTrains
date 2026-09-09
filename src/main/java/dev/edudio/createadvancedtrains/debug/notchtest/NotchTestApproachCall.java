package dev.edudio.createadvancedtrains.debug.notchtest;

record NotchTestApproachCall(
        int sequenceInServerTick,
        Double preCatTargetSpeedBlocksPerSecond,
        Double nativeTargetSpeedBlocksPerSecond,
        Double finalTargetSpeedBlocksPerSecond,
        boolean brakingDemand,
        String applicationReason,
        Float originalAccelerationMod,
        Float returnedAccelerationMod,
        boolean notchModifierApplied,
        StopTargetHoldResult.State stopTargetHoldState,
        boolean stopTargetHoldOverrideApplied,
        String stopTargetHoldTriggerReason) {
}

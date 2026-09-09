package dev.edudio.createadvancedtrains.debug.notchtest;

record NotchTestResponseState(
        double storedEffectiveAccelerationBlocksPerSecondSquared,
        Double activeEffectiveAccelerationBlocksPerSecondSquared,
        int transitionElapsedTicks,
        String transitionTimebase) {
}

package dev.edudio.createadvancedtrains.control.braking;

public enum BrakingCurveFailure {
    NONE,
    NON_FINITE_INPUT,
    NON_POSITIVE_FORWARD_DISTANCE,
    NEGATIVE_SPEED,
    INVALID_SPEED_CEILING,
    INVALID_BASE_ACCELERATION,
    INVALID_PROFILE,
    ITERATION_LIMIT_REACHED
}

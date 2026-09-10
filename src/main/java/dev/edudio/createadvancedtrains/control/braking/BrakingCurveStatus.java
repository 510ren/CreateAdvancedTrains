package dev.edudio.createadvancedtrains.control.braking;

public enum BrakingCurveStatus {
    AVAILABLE,
    NO_FORWARD_STOP_TARGET,
    INSUFFICIENT_DISTANCE,
    INVALID_INPUT,
    ITERATION_LIMIT_REACHED;

    public boolean isCalculationError() {
        return this == INVALID_INPUT || this == ITERATION_LIMIT_REACHED;
    }
}

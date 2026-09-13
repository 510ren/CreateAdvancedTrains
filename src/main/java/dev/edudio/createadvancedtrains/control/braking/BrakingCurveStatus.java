package dev.edudio.createadvancedtrains.control.braking;

public enum BrakingCurveStatus {
    AVAILABLE,
    NO_FORWARD_STOP_TARGET,
    INSUFFICIENT_DISTANCE,
    INVALID_INPUT,
    ITERATION_LIMIT_REACHED;

    /**
     * CalculationErrorに関する条件を判定します。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    public boolean isCalculationError() {
        return this == INVALID_INPUT || this == ITERATION_LIMIT_REACHED;
    }
}

package dev.edudio.createadvancedtrains.train.error;

/** CAT制御を停止させる計算・入力・Navigationエラーを分類します。 */
public enum CatCalculationErrorCode {
    NON_FINITE_INPUT,
    UNKNOWN_UNITS,
    INVALID_BASE_ACCELERATION,
    INVALID_PROFILE,
    INVALID_BRAKING_CURVE_INPUT,
    ITERATION_LIMIT_REACHED,
    INVALID_NAVIGATION_STATE,
    NAVIGATION_DESTINATION_OVERRUN
}

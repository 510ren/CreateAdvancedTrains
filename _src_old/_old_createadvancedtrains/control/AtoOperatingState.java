package dev.edudio.createadvancedtrains.control;

/** ATOが現在通常制御可能か、到着待ちまたは計算エラーで停止中かを表します。 */
public enum AtoOperatingState {
    ACTIVE,
    ARRIVAL_PENDING,
    CALCULATION_ERROR
}

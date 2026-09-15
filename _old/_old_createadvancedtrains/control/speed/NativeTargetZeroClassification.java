package dev.edudio.createadvancedtrains.control.speed;

/** Create由来のnative targetが0である理由を安全側に分類します。 */
public enum NativeTargetZeroClassification {
    NOT_ZERO,
    SIGNAL_STOP,
    DESTINATION_STOP,
    CREATE_MANUAL_INPUT,
    UNCLASSIFIED
}

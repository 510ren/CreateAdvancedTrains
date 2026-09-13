package dev.edudio.createadvancedtrains.control.speed;

/** TargetSpeedResolverへ渡される速度候補の供給元を表します。 */
public enum TargetSpeedCandidateSource {
    CREATE_NATIVE,
    CREATE_MAX_SPEED,
    CAT_SPEED_LIMIT,
    BRAKING_CURVE
}

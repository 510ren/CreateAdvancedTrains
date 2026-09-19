package dev.edudio.createadvancedtrains.control.notch;

import java.util.Locale;
import java.util.Optional;

/**
 * Discrete product P/N/B notches.
 */
public enum Notch {
    P1,
    P2,
    P3,
    P4,
    P5,
    N,
    B1,
    B2,
    B3,
    B4,
    B5,
    B6,
    B7;

    /**
     * ServiceBrakeに関する条件を判定します。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    public boolean isServiceBrake() {
        return ordinal() >= B1.ordinal();
    }

    /**
     * Powerに関する条件を判定します。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    public boolean isPower() {
        return ordinal() >= P1.ordinal() && ordinal() <= P5.ordinal();
    }

    /**
     * Neutralに関する条件を判定します。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    public boolean isNeutral() {
        return this == N;
    }

    /**
     * 仕様書に独立した関数契約がないため、入力表現を解析し、{@code parseServiceBrake}が示す値へ変換します。
     * @param value 処理対象の値。
     * @return 結果が存在する場合はその値、存在しない場合は空のOptional。
     */
    public static Optional<Notch> parseServiceBrake(String value) {
        if (value == null) {
            return Optional.empty();
        }

        try {
            Notch notch = valueOf(value.trim().toUpperCase(Locale.ROOT));
            return notch.isServiceBrake() ? Optional.of(notch) : Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}

package dev.edudio.createadvancedtrains.control.notch;

import java.util.Objects;

/**
 * Latches one service-brake notch for a complete Phase 5A test session.
 */
public final class FixedNotchSelector {

    private final Notch fixedNotch;

    /**
     * このクラスのインスタンスを初期化します。
     * @param fixedNotch 仕様書に個別説明がないため、{@code fixedNotch}が示すノッチ状態またはノッチ候補。
     */
    public FixedNotchSelector(Notch fixedNotch) {
        this.fixedNotch = Objects.requireNonNull(fixedNotch, "fixedNotch");
        if (!fixedNotch.isServiceBrake()) {
            throw new IllegalArgumentException("FIXED_FOR_TEST requires B1 through B7");
        }
    }

    /**
     * 現在状態と予測値から使用するノッチを選択します。
     * @return 処理によって得られた結果。
     */
    public Notch select() {
        return fixedNotch;
    }
}

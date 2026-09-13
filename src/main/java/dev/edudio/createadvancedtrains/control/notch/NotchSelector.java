package dev.edudio.createadvancedtrains.control.notch;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Pure response-aware Phase 6 P/N/B selector. */
public final class NotchSelector {

    public static final double SAFE_SPEED_FACTOR = 0.95;
    public static final double HYSTERESIS_BLOCKS_PER_SECOND = 0.5;
    public static final double NORMAL_BAND_OFFSET_BLOCKS_PER_SECOND = 1.0;
    public static final int PREDICTION_TICKS = 10;

    private static final Notch[] POWER_NOTCHES = {
            Notch.P1, Notch.P2, Notch.P3, Notch.P4, Notch.P5
    };

    private final NotchProfile profile;

    /**
     * このクラスのインスタンスを初期化します。
     * @param profile 仕様書に個別説明がないため、{@code profile}が示すノッチまたは制動特性。
     */
    public NotchSelector(NotchProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    /**
     * 現在状態と予測値から使用するノッチを選択します。
     * @param input 計算または判定に使用する入力値。
     * @return 処理によって得られた結果。
     */
    public NotchSelection select(Input input) {
        validate(input);

        double safeSpeed = input.resolvedSpeedLimitBlocksPerSecond() * SAFE_SPEED_FACTOR;
        double lower = safeSpeed - NORMAL_BAND_OFFSET_BLOCKS_PER_SECOND - HYSTERESIS_BLOCKS_PER_SECOND;
        double upper = safeSpeed + NORMAL_BAND_OFFSET_BLOCKS_PER_SECOND + HYSTERESIS_BLOCKS_PER_SECOND;
        double brakeLimit = input.brakingCurveLimitBlocksPerSecond().isPresent()
                ? Math.min(safeSpeed, input.brakingCurveLimitBlocksPerSecond().getAsDouble())
                : safeSpeed;
        boolean brakingDemand = input.currentSpeedBlocksPerSecond() > upper
                || input.brakingCurveLimitBlocksPerSecond().isPresent()
                        && input.currentSpeedBlocksPerSecond() > input.brakingCurveLimitBlocksPerSecond().getAsDouble();

        Optional<Notch> current = input.currentRequestedNotch();
        if (current.isPresent() && current.get().isServiceBrake()) {
            return selectFromBrake(input, current.get(), brakeLimit);
        }
        if (brakingDemand) {
            double prediction = predict(input, Notch.B4);
            return new NotchSelection(Notch.B4, false, prediction, brakeLimit);
        }

        if (input.currentSpeedBlocksPerSecond() < lower) {
            if (current.isPresent() && current.get().isPower()) {
                Notch currentPower = current.get();
                double prediction = predict(input, currentPower);
                if (prediction < lower) {
                    Notch stronger = strongerPower(currentPower);
                    return selection(input, stronger, safeSpeed);
                }
                if (prediction > upper) {
                    Notch weaker = weakerPowerOrNeutral(currentPower);
                    return selection(input, weaker, safeSpeed);
                }
                return new NotchSelection(currentPower, false, prediction, safeSpeed);
            }
            Notch strongest = Notch.N;
            double strongestPrediction = predict(input, Notch.N);
            for (Notch candidate : POWER_NOTCHES) {
                double prediction = predict(input, candidate);
                if (prediction <= safeSpeed) {
                    strongest = candidate;
                    strongestPrediction = prediction;
                }
            }
            return new NotchSelection(strongest, false, strongestPrediction, safeSpeed);
        }

        if (current.isPresent() && current.get().isPower()) {
            double prediction = predict(input, current.get());
            if (prediction >= lower && prediction <= upper) {
                return new NotchSelection(current.get(), false, prediction, safeSpeed);
            }
            if (prediction < lower) {
                return selection(input, Notch.N, safeSpeed);
            }
            return selection(input, weakerPowerOrNeutral(current.get()), safeSpeed);
        }
        return selection(input, Notch.N, safeSpeed);
    }

    /**
     * 仕様書に独立した関数契約がないため、入力条件から{@code selectFromBrake}が示す候補を選択します。
     * @param input 計算または判定に使用する入力値。
     * @param current 仕様書に個別説明がないため、現在の処理内容から推定した、{@code current}として使用される入力値。
     * @param brakeLimit 仕様書に個別説明がないため、{@code brakeLimit}が示す上限値。
     * @return 処理によって得られた結果。
     */
    private NotchSelection selectFromBrake(Input input, Notch current, double brakeLimit) {
        double currentPrediction = predict(input, current);

        if (currentPrediction > brakeLimit + HYSTERESIS_BLOCKS_PER_SECOND) {
            if (current == Notch.B7) {
                return new NotchSelection(Notch.B7, true, currentPrediction, brakeLimit);
            }
            Notch stronger = strongerBrake(current);
            return selection(input, stronger, brakeLimit);
        }

        Notch weaker = weakerBrakeOrNeutral(current);
        double weakerPrediction = predict(input, weaker);
        if (weakerPrediction <= brakeLimit + HYSTERESIS_BLOCKS_PER_SECOND) {
            return new NotchSelection(weaker, false, weakerPrediction, brakeLimit);
        }
        return new NotchSelection(current, false, currentPrediction, brakeLimit);
    }

    /**
     * 仕様書に独立した関数契約がないため、入力条件から{@code selection}が示す候補を選択します。
     * @param input 計算または判定に使用する入力値。
     * @param notch 仕様書に個別説明がないため、{@code notch}が示すノッチ状態またはノッチ候補。
     * @param limit 仕様書に個別説明がないため、{@code limit}が示す上限値。
     * @return 処理によって得られた結果。
     */
    private NotchSelection selection(Input input, Notch notch, double limit) {
        return new NotchSelection(notch, false, predict(input, notch), limit);
    }

    /**
     * 仕様書に独立した関数契約がないため、入力値から{@code predict}が示す予測結果を計算します。
     * @param input 計算または判定に使用する入力値。
     * @param candidate 仕様書に個別説明がないため、{@code candidate}が示す条件の有効・無効を表す値。
     * @return 処理または計算によって得られた数値。
     */
    private double predict(Input input, Notch candidate) {
        double target = profile.targetAcceleration(
                candidate,
                input.currentSpeedBlocksPerSecond(),
                input.baseAccelerationBlocksPerSecondSquared());
        double speed = input.currentSpeedBlocksPerSecond();
        for (int tick = 0; tick < PREDICTION_TICKS; tick++) {
            double acceleration = NotchResponseModel.effectiveAccelerationAt(
                    input.currentEffectiveAccelerationBlocksPerSecondSquared(),
                    target,
                    tick);
            speed = Math.max(0.0, speed + acceleration / TICKS_PER_SECOND);
        }
        return speed;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code strongerPower}としてまとめられている処理を実行します。
     * @param notch 仕様書に個別説明がないため、{@code notch}が示すノッチ状態またはノッチ候補。
     * @return 処理によって得られた結果。
     */
    private static Notch strongerPower(Notch notch) {
        return switch (notch) {
            case P1 -> Notch.P2;
            case P2 -> Notch.P3;
            case P3 -> Notch.P4;
            case P4, P5 -> Notch.P5;
            default -> throw new IllegalArgumentException("Not a power notch: " + notch);
        };
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code weakerPowerOrNeutral}としてまとめられている処理を実行します。
     * @param notch 仕様書に個別説明がないため、{@code notch}が示すノッチ状態またはノッチ候補。
     * @return 処理によって得られた結果。
     */
    private static Notch weakerPowerOrNeutral(Notch notch) {
        return switch (notch) {
            case P5 -> Notch.P4;
            case P4 -> Notch.P3;
            case P3 -> Notch.P2;
            case P2 -> Notch.P1;
            case P1 -> Notch.N;
            default -> throw new IllegalArgumentException("Not a power notch: " + notch);
        };
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code strongerBrake}としてまとめられている処理を実行します。
     * @param notch 仕様書に個別説明がないため、{@code notch}が示すノッチ状態またはノッチ候補。
     * @return 処理によって得られた結果。
     */
    private static Notch strongerBrake(Notch notch) {
        return switch (notch) {
            case B4 -> Notch.B5;
            case B5 -> Notch.B6;
            case B6, B7 -> Notch.B7;
            default -> throw new IllegalArgumentException("Phase 6 automatic brake must be B4 through B7");
        };
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code weakerBrakeOrNeutral}としてまとめられている処理を実行します。
     * @param notch 仕様書に個別説明がないため、{@code notch}が示すノッチ状態またはノッチ候補。
     * @return 処理によって得られた結果。
     */
    private static Notch weakerBrakeOrNeutral(Notch notch) {
        return switch (notch) {
            case B7 -> Notch.B6;
            case B6 -> Notch.B5;
            case B5 -> Notch.B4;
            case B4 -> Notch.N;
            default -> throw new IllegalArgumentException("Phase 6 automatic brake must be B4 through B7");
        };
    }

    /**
     * 入力値が処理可能な条件を満たすか検証します。
     * @param input 計算または判定に使用する入力値。
     */
    private static void validate(Input input) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(input.currentRequestedNotch(), "currentRequestedNotch");
        Objects.requireNonNull(input.brakingCurveLimitBlocksPerSecond(), "brakingCurveLimitBlocksPerSecond");
        requireFiniteNonNegative(input.currentSpeedBlocksPerSecond(), "currentSpeedBlocksPerSecond");
        requireFiniteNonNegative(input.resolvedSpeedLimitBlocksPerSecond(), "resolvedSpeedLimitBlocksPerSecond");
        if (!Double.isFinite(input.currentEffectiveAccelerationBlocksPerSecondSquared())) {
            throw new IllegalArgumentException("currentEffectiveAccelerationBlocksPerSecondSquared must be finite");
        }
        if (!Double.isFinite(input.baseAccelerationBlocksPerSecondSquared())
                || input.baseAccelerationBlocksPerSecondSquared() <= 0.0) {
            throw new IllegalArgumentException("baseAccelerationBlocksPerSecondSquared must be finite and positive");
        }
        if (input.brakingCurveLimitBlocksPerSecond().isPresent()) {
            requireFiniteNonNegative(input.brakingCurveLimitBlocksPerSecond().getAsDouble(),
                    "brakingCurveLimitBlocksPerSecond");
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code requireFiniteNonNegative}が示す入力条件を検証します。
     * @param value 処理対象の値。
     * @param name 対象を識別する名前。
     */
    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param currentSpeedBlocksPerSecond 現在速度の大きさ。単位はblocks/s。
     * @param resolvedSpeedLimitBlocksPerSecond 仕様書に個別説明がないため、{@code resolvedSpeedLimitBlocksPerSecond}が示す速度。単位はblocks/s。
     * @param brakingCurveLimitBlocksPerSecond 仕様書に個別説明がないため、{@code brakingCurveLimitBlocksPerSecond}が示す速度。単位はblocks/s。
     * @param currentRequestedNotch 仕様書に個別説明がないため、{@code currentRequestedNotch}が示すノッチ状態またはノッチ候補。
     * @param currentEffectiveAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code currentEffectiveAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
     * @param baseAccelerationBlocksPerSecondSquared Create基本加速度の大きさ。単位はblocks/s^2。
     */
    public record Input(
            double currentSpeedBlocksPerSecond,
            double resolvedSpeedLimitBlocksPerSecond,
            OptionalDouble brakingCurveLimitBlocksPerSecond,
            Optional<Notch> currentRequestedNotch,
            double currentEffectiveAccelerationBlocksPerSecondSquared,
            double baseAccelerationBlocksPerSecondSquared) {
    }
}

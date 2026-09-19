package dev.edudio.createadvancedtrains.control.notch;

import java.util.Objects;

/**
 * Ten-tick linear response from the current effective acceleration to a notch target.
 */
public final class NotchResponseModel {

    public static final int TRANSITION_TICKS = 10;

    private Notch commandedNotch = Notch.N;
    private Notch appliedNotch;
    private double transitionStartAcceleration;
    private double effectiveAcceleration;
    private int nextTransitionElapsedTicks = TRANSITION_TICKS;

    /**
     * このクラスのインスタンスを初期化します。
     */
    public NotchResponseModel() {
        this(Notch.N);
    }

    /**
     * このクラスのインスタンスを初期化します。
     * @param lastCompletedNotch 仕様書に個別説明がないため、{@code lastCompletedNotch}が示すノッチ状態またはノッチ候補。
     */
    public NotchResponseModel(Notch lastCompletedNotch) {
        appliedNotch = Objects.requireNonNull(lastCompletedNotch, "lastCompletedNotch");
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code step}としてまとめられている処理を実行します。
     * @param requestedNotch 仕様書に個別説明がないため、{@code requestedNotch}が示すノッチ状態またはノッチ候補。
     * @param targetAcceleration 仕様書に個別説明がないため、{@code targetAcceleration}が示す加速度または加速度倍率。単位は呼出元の境界定義に従います。
     * @return 処理によって得られた結果。
     */
    public Response step(Notch requestedNotch, double targetAcceleration) {
        Objects.requireNonNull(requestedNotch, "requestedNotch");
        if (!Double.isFinite(targetAcceleration)) {
            throw new IllegalArgumentException("targetAcceleration must be finite");
        }

        if (requestedNotch != commandedNotch) {
            transitionStartAcceleration = effectiveAcceleration;
            commandedNotch = requestedNotch;
            nextTransitionElapsedTicks = 0;
        }

        int evaluatedElapsedTicks = Math.min(nextTransitionElapsedTicks, TRANSITION_TICKS);
        double progress = transitionProgress(evaluatedElapsedTicks);
        effectiveAcceleration = effectiveAccelerationAt(
                transitionStartAcceleration,
                targetAcceleration,
                evaluatedElapsedTicks);

        if (evaluatedElapsedTicks >= TRANSITION_TICKS) {
            appliedNotch = commandedNotch;
        }

        Response response = new Response(
                commandedNotch,
                appliedNotch,
                evaluatedElapsedTicks,
                progress,
                transitionStartAcceleration,
                targetAcceleration,
                effectiveAcceleration);

        if (nextTransitionElapsedTicks < TRANSITION_TICKS) {
            nextTransitionElapsedTicks++;
        }

        return response;
    }

    /**
     * Discards every pre-suspension command and restarts the next transition
     * from zero effective acceleration.
     */
    public void reset() {
        commandedNotch = Notch.N;
        appliedNotch = Notch.N;
        transitionStartAcceleration = 0.0;
        effectiveAcceleration = 0.0;
        nextTransitionElapsedTicks = TRANSITION_TICKS;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code effectiveAcceleration}としてまとめられている処理を実行します。
     * @return 処理または計算によって得られた数値。
     */
    public double effectiveAcceleration() {
        return effectiveAcceleration;
    }

    /**
     * Stateless form used by response-aware prediction. Tick 0 returns aStart;
     * tick 10 and later return the current target.
     * @param startAcceleration 仕様書に個別説明がないため、{@code startAcceleration}が示す加速度または加速度倍率。単位は呼出元の境界定義に従います。
     * @param targetAcceleration 仕様書に個別説明がないため、{@code targetAcceleration}が示す加速度または加速度倍率。単位は呼出元の境界定義に従います。
     * @param transitionElapsedTicks 仕様書に個別説明がないため、{@code transitionElapsedTicks}が示すtick数またはserver tick値。
     * @return 処理または計算によって得られた数値。
     */
    public static double effectiveAccelerationAt(
            double startAcceleration,
            double targetAcceleration,
            int transitionElapsedTicks) {
        if (!Double.isFinite(startAcceleration) || !Double.isFinite(targetAcceleration)) {
            throw new IllegalArgumentException("Acceleration values must be finite");
        }
        return startAcceleration
                + (targetAcceleration - startAcceleration)
                        * transitionProgress(transitionElapsedTicks);
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code transitionProgress}としてまとめられている処理を実行します。
     * @param transitionElapsedTicks 仕様書に個別説明がないため、{@code transitionElapsedTicks}が示すtick数またはserver tick値。
     * @return 処理または計算によって得られた数値。
     */
    public static double transitionProgress(int transitionElapsedTicks) {
        if (transitionElapsedTicks < 0) {
            throw new IllegalArgumentException("transitionElapsedTicks must not be negative");
        }
        return Math.min(transitionElapsedTicks / (double) TRANSITION_TICKS, 1.0);
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code neutralResponse}としてまとめられている処理を実行します。
     * @return 処理によって得られた結果。
     */
    public static Response neutralResponse() {
        return new Response(
                Notch.N,
                Notch.N,
                0,
                0.0,
                0.0,
                0.0,
                0.0);
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param commandedNotch 仕様書に個別説明がないため、{@code commandedNotch}が示すノッチ状態またはノッチ候補。
     * @param appliedNotch 仕様書に個別説明がないため、{@code appliedNotch}が示すノッチ状態またはノッチ候補。
     * @param transitionElapsedTicks 仕様書に個別説明がないため、{@code transitionElapsedTicks}が示すtick数またはserver tick値。
     * @param transitionProgress 仕様書に個別説明がないため、現在の処理内容から推定した、{@code transitionProgress}として使用される入力値。
     * @param transitionStartAcceleration 仕様書に個別説明がないため、{@code transitionStartAcceleration}が示す加速度または加速度倍率。単位は呼出元の境界定義に従います。
     * @param targetAcceleration 仕様書に個別説明がないため、{@code targetAcceleration}が示す加速度または加速度倍率。単位は呼出元の境界定義に従います。
     * @param effectiveAcceleration 仕様書に個別説明がないため、{@code effectiveAcceleration}が示す加速度または加速度倍率。単位は呼出元の境界定義に従います。
     */
    public record Response(
            Notch commandedNotch,
            Notch appliedNotch,
            int transitionElapsedTicks,
            double transitionProgress,
            double transitionStartAcceleration,
            double targetAcceleration,
            double effectiveAcceleration) {
    }
}

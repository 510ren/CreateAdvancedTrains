package dev.edudio.createadvancedtrains.control.notch;

import java.util.Objects;

/**
 * Ten-tick linear response from the current effective acceleration to a notch target.
 */
public final class NotchResponseModel {

    public static final int TRANSITION_TICKS = 10;

    private Notch commandedNotch = Notch.COAST;
    private Notch appliedNotch;
    private double transitionStartAcceleration;
    private double effectiveAcceleration;
    private int nextTransitionElapsedTicks = TRANSITION_TICKS;

    public NotchResponseModel() {
        this(Notch.COAST);
    }

    public NotchResponseModel(Notch lastCompletedNotch) {
        appliedNotch = Objects.requireNonNull(lastCompletedNotch, "lastCompletedNotch");
    }

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
     * Stateless form used by response-aware prediction. Tick 0 returns aStart;
     * tick 10 and later return the current target.
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

    public static double transitionProgress(int transitionElapsedTicks) {
        if (transitionElapsedTicks < 0) {
            throw new IllegalArgumentException("transitionElapsedTicks must not be negative");
        }
        return Math.min(transitionElapsedTicks / (double) TRANSITION_TICKS, 1.0);
    }

    public static Response coastResponse() {
        return new Response(
                Notch.COAST,
                Notch.COAST,
                0,
                0.0,
                0.0,
                0.0,
                0.0);
    }

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

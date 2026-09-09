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
        double progress = evaluatedElapsedTicks / (double) TRANSITION_TICKS;
        effectiveAcceleration = transitionStartAcceleration
                + (targetAcceleration - transitionStartAcceleration) * progress;

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

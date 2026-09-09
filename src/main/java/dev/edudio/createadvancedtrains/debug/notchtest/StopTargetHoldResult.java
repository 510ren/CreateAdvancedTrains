package dev.edudio.createadvancedtrains.debug.notchtest;

public record StopTargetHoldResult(
        State state,
        boolean overrideApplied,
        String triggerReason) {

    private static final StopTargetHoldResult INACTIVE =
            new StopTargetHoldResult(State.INACTIVE, false, null);
    private static final StopTargetHoldResult RELEASED =
            new StopTargetHoldResult(State.RELEASED, false, null);

    static StopTargetHoldResult inactive() {
        return INACTIVE;
    }

    static StopTargetHoldResult latched(String triggerReason) {
        return new StopTargetHoldResult(State.LATCHED, true, triggerReason);
    }

    static StopTargetHoldResult released() {
        return RELEASED;
    }

    public enum State {
        INACTIVE,
        LATCHED,
        RELEASED
    }
}

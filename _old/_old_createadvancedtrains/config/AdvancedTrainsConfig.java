package dev.edudio.createadvancedtrains.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AdvancedTrainsConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue CONTROL_ENABLED;

    public static final ForgeConfigSpec.BooleanValue ATO_ENABLED;
    public static final ForgeConfigSpec.BooleanValue TASC_ENABLED;
    public static final ForgeConfigSpec.BooleanValue BRAKING_ENABLED;
    public static final ForgeConfigSpec.BooleanValue NOTCH_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SPEED_LIMIT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SIGNAL_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ATC_ENABLED;

    public static final ForgeConfigSpec.BooleanValue TRAIN_DATA_DEBUG_ENABLED;
    public static final ForgeConfigSpec.IntValue TRAIN_DATA_DEBUG_SAMPLE_INTERVAL_TICKS;

    public static final ForgeConfigSpec.BooleanValue PHASE5A_NOTCH_TEST_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> PHASE5A_NOTCH_TEST_SELECTION_MODE;
    public static final ForgeConfigSpec.ConfigValue<String> PHASE5A_NOTCH_TEST_FIXED_NOTCH;
    public static final ForgeConfigSpec.BooleanValue PHASE5A_NOTCH_TEST_HOLD_STOP_TARGET_UNTIL_STOP;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment(
                "Create: Advanced Trains configuration").push("control");

        CONTROL_ENABLED = builder
                .comment(
                        "Enable Create: Advanced Trains train control.")
                .define("enabled", true);

        builder.pop();

        builder.comment(
                "Enable or disable individual train control features.").push("features");

        ATO_ENABLED = builder
                .comment(
                        "Enable Automatic Train Operation (ATO).")
                .define("ato", true);

        TASC_ENABLED = builder
                .comment(
                        "Enable Train Automatic Stop Control (TASC).")
                .define("tasc", true);

        BRAKING_ENABLED = builder
                .comment(
                        "Enable braking curve control.")
                .define("braking", true);

        NOTCH_ENABLED = builder
                .comment(
                        "Enable notch control.")
                .define("notch", true);

        SPEED_LIMIT_ENABLED = builder
                .comment(
                        "Enable speed limit control.")
                .define("speed_limit", true);

        SIGNAL_ENABLED = builder
                .comment(
                        "Enable signal-based train control.")
                .define("signal", true);

        ATC_ENABLED = builder
                .comment(
                        "Enable ATC/ATS control.")
                .define("atc", true);

        builder.pop();

        builder.comment(
                "Server-side diagnostic logging.").push("debug");

        builder.comment(
                "Passive Create train data logging.").push("train_data");

        TRAIN_DATA_DEBUG_ENABLED = builder
                .comment(
                        "Write passive train data samples to JSON Lines log files.")
                .define("enabled", false);

        TRAIN_DATA_DEBUG_SAMPLE_INTERVAL_TICKS = builder
                .comment(
                        "Number of server ticks between train data samples.")
                .defineInRange("sample_interval_ticks", 1, 1, Integer.MAX_VALUE);

        builder.pop();

        builder.comment(
                "Phase 5A fixed service-brake notch test. This is not a production control mode.")
                .push("phase5a_notch_test");

        PHASE5A_NOTCH_TEST_ENABLED = builder
                .comment(
                        "Enable the Phase 5A notch test and its separate JSON Lines control log.")
                .define("enabled", false);

        PHASE5A_NOTCH_TEST_SELECTION_MODE = builder
                .comment(
                        "Selection mode. Phase 5A train control supports FIXED_FOR_TEST only.")
                .define("selection_mode", "FIXED_FOR_TEST");

        PHASE5A_NOTCH_TEST_FIXED_NOTCH = builder
                .comment(
                        "Fixed service-brake notch used for the whole enabled test session: B1 through B7.")
                .define("fixed_notch", "B1");

        PHASE5A_NOTCH_TEST_HOLD_STOP_TARGET_UNTIL_STOP = builder
                .comment(
                        "Keep the final target speed at zero after Create Navigation first requests a stop. FIXED_FOR_TEST only.")
                .define("hold_stop_target_until_stop", false);

        builder.pop();
        builder.pop();

        SPEC = builder.build();
    }

    /**
     * このクラスのインスタンスを初期化します。
     */
    private AdvancedTrainsConfig() {
    }
}

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
        builder.pop();

        SPEC = builder.build();
    }

    private AdvancedTrainsConfig() {
    }
}

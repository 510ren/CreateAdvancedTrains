package dev.edudio.createadvancedtrains.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.edudio.createadvancedtrains.speed.SpeedLimitController;
import dev.edudio.createadvancedtrains.speed.SpeedLimitSource;
import dev.edudio.createadvancedtrains.train.TrainControllerManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "create_advanced_trains", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChangeSpeedLimitCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("create_advanced_trains")
                        .then(Commands.literal("speed_limit")
                                .then(Commands.literal("test")
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                        .executes(ChangeSpeedLimitCommands::executeSet))))));
    }

    private static int executeSet(CommandContext<CommandSourceStack> ctx) {
        double value = DoubleArgumentType.getDouble(ctx, "value");

        TrainControllerManager.INSTANCE.setSpeedLimitForAll(
                SpeedLimitSource.TEST,
                value);

        ctx.getSource().sendSuccess(
                () -> net.minecraft.network.chat.Component.literal(
                        "[CAT] TEST速度制限を設定しました: " + value + " blocks/s"),
                true);

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }
}
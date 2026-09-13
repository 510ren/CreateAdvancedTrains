package dev.edudio.createadvancedtrains.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.edudio.createadvancedtrains.CreateAdvancedTrains;
import dev.edudio.createadvancedtrains.speed.SpeedLimitSource;
import dev.edudio.createadvancedtrains.train.TrainControllerManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateAdvancedTrains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChangeSpeedLimitCommands {
    /**
     * RegisterCommandsイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal(CreateAdvancedTrains.MOD_ID)
                        .then(Commands.literal("speed_limit")
                                .then(Commands.literal("test")
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                        .executes(ChangeSpeedLimitCommands::executeSet))))));
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の処理内容から推定すると、{@code executeSet}に対応する処理を実行します。
     * @param ctx 仕様書に個別説明がないため処理内容から推定した、{@code ctx}に対応する入力値。
     * @return 処理または計算によって得られた数値。
     */
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
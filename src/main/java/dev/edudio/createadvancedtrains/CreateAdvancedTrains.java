package dev.edudio.createadvancedtrains;

import dev.edudio.createadvancedtrains.registry.ModBlocks;
import dev.edudio.createadvancedtrains.registry.ModCreativeModeTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;

@Mod(CreateAdvancedTrains.MOD_ID)
public class CreateAdvancedTrains {

    public static final String MOD_ID = "create_advanced_trains";

    /**
     * このクラスのインスタンスを初期化します。
     */
    public CreateAdvancedTrains() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
    }
}

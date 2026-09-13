package dev.edudio.createadvancedtrains;

import dev.edudio.createadvancedtrains.config.AdvancedTrainsConfig;
import dev.edudio.createadvancedtrains.network.ModNetwork;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(CreateAdvancedTrains.MOD_ID)
public class CreateAdvancedTrains {

    public static final String MOD_ID = "create_advanced_trains";

    /**
     * このクラスのインスタンスを初期化します。
     */
    public CreateAdvancedTrains() {
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER,
                AdvancedTrainsConfig.SPEC);

        ModNetwork.register();
    }
}
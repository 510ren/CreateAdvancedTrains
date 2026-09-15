package dev.edudio.createadvancedtrains.registry;

import dev.edudio.createadvancedtrains.CreateAdvancedTrains;
import dev.edudio.createadvancedtrains.content.decoration.RailwayCasingPanelBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CreateAdvancedTrains.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CreateAdvancedTrains.MOD_ID);

    public static final RegistryObject<Block> RAILWAY_CASING_PANEL = BLOCKS.register(
            "railway_casing_panel",
            () -> new RailwayCasingPanelBlock(BlockBehaviour.Properties.copy(Blocks.ANDESITE)
                    .mapColor(MapColor.TERRACOTTA_CYAN)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()));

    public static final RegistryObject<Item> RAILWAY_CASING_PANEL_ITEM = ITEMS.register(
            "railway_casing_panel",
            () -> new BlockItem(RAILWAY_CASING_PANEL.get(), new Item.Properties()));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}

package dev.edudio.createadvancedtrains.client;

import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.model.ModelSwapper;
import dev.edudio.createadvancedtrains.CreateAdvancedTrains;
import dev.edudio.createadvancedtrains.content.decoration.RailwayCasingPanelBlock;
import dev.edudio.createadvancedtrains.registry.ModBlocks;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = CreateAdvancedTrains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RailwayCasingPanelClient {

    private RailwayCasingPanelClient() {
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        PanelConnectedTextureBehaviour behaviour = new PanelConnectedTextureBehaviour();

        ModelSwapper.getAllBlockStateModelLocations(ModBlocks.RAILWAY_CASING_PANEL.get())
                .forEach(location -> event.getModels().computeIfPresent(
                        location,
                        (key, model) -> new CTModel(model, behaviour)));
    }

    private static final class PanelConnectedTextureBehaviour extends ConnectedTextureBehaviour.Base {

        @Override
        @Nullable
        public CTSpriteShiftEntry getShift(
                BlockState state,
                Direction direction,
                @Nullable TextureAtlasSprite sprite) {
            Direction facing = state.getValue(RailwayCasingPanelBlock.FACING);
            return direction.getAxis() == facing.getAxis()
                    ? AllSpriteShifts.RAILWAY_CASING_SIDE
                    : null;
        }

        @Override
        public boolean connectsTo(
                BlockState state,
                BlockState other,
                BlockAndTintGetter reader,
                BlockPos pos,
                BlockPos otherPos,
                Direction face) {
            return super.connectsTo(state, other, reader, pos, otherPos, face)
                    && other.getValue(RailwayCasingPanelBlock.FACING)
                    == state.getValue(RailwayCasingPanelBlock.FACING);
        }
    }
}

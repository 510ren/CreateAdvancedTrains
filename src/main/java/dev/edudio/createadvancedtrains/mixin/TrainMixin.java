package dev.edudio.createadvancedtrains.mixin;

import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.train.TrainController;
import dev.edudio.createadvancedtrains.train.TrainControllerManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Train.class)
public abstract class TrainMixin {

    @Inject(method = "approachTargetSpeed", at = @At("HEAD"), remap = false)
    private void createAdvancedTrains$applyAto(
            float accelerationMod,
            CallbackInfo ci) {
        Train train = (Train) (Object) this;

        TrainController controller = TrainControllerManager.INSTANCE.getOrCreate(train);

        controller.applyAtoTargetSpeed(train, true);
    }
}
package dev.edudio.createadvancedtrains.mixin;

import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.debug.hud.TrainTargetSpeedTracker;
import dev.edudio.createadvancedtrains.debug.notchtest.Phase5ANotchTestManager;
import dev.edudio.createadvancedtrains.debug.notchtest.StopTargetHoldResult;
import dev.edudio.createadvancedtrains.train.TrainController;
import dev.edudio.createadvancedtrains.train.TrainControllerManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Train.class)
public abstract class TrainMixin {

    @ModifyVariable(method = "approachTargetSpeed", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private float createAdvancedTrains$applyTrainControl(float accelerationMod) {
        Train train = (Train) (Object) this;
        double nativeTargetSpeed = train.targetSpeed;

        TrainController controller = TrainControllerManager.INSTANCE.getOrCreate(train);
        controller.applyAtoTargetSpeed(train, true);

        StopTargetHoldResult stopTargetHold = Phase5ANotchTestManager.INSTANCE.applyStopTargetHold(
                train,
                nativeTargetSpeed);
        double finalTargetSpeed = train.targetSpeed;

        TrainTargetSpeedTracker.INSTANCE.observe(
                train.id,
                nativeTargetSpeed,
                finalTargetSpeed);

        return Phase5ANotchTestManager.INSTANCE.modifyAccelerationMod(
                train,
                nativeTargetSpeed,
                finalTargetSpeed,
                accelerationMod,
                stopTargetHold);
    }
}

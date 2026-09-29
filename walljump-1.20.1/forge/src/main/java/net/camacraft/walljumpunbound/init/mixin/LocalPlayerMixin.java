package net.camacraft.walljumpunbound.init.mixin;

import net.camacraft.walljumpunbound.WallJumpClient;
import net.camacraft.walljumpunbound.logic.DoubleJumpLogic;
import net.camacraft.walljumpunbound.logic.SpeedBoostLogic;
import net.camacraft.walljumpunbound.logic.StepAssistLogic;
import net.camacraft.walljumpunbound.logic.WallJumpLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void aiStep(CallbackInfo ci) {
        LocalPlayer pl = Minecraft.getInstance().player;

        WallJumpClient.playFallingSound(pl);
        WallJumpLogic.doWallJump(pl);
        WallJumpLogic.updateClingPose(pl);
        DoubleJumpLogic.doDoubleJump(pl);
        SpeedBoostLogic.doSpeedBoost(pl);
        StepAssistLogic.doStepAssist(pl);
        WallJumpLogic.updateMotionClaim(pl);
    }

    /**
     * Vanilla's sprint check is the only read of horizontalCollision in aiStep,
     * before the move: the step assist lends it the value its sprint rules want
     * here, and the next inject hands the real one back before the move.
     */
    @Inject(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;horizontalCollision:Z", opcode = Opcodes.GETFIELD))
    private void walljumpunbound$beforeSprintCheck(CallbackInfo ci) {
        StepAssistLogic.beforeSprintCheck((LocalPlayer) (Object) this);
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;aiStep()V"))
    private void walljumpunbound$afterSprintCheck(CallbackInfo ci) {
        StepAssistLogic.afterSprintCheck((LocalPlayer) (Object) this);
    }
}

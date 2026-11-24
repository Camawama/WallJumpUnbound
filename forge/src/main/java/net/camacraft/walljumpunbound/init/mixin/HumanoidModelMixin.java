package net.camacraft.walljumpunbound.init.mixin;

import net.camacraft.walljumpunbound.logic.WallClingPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The cling pose for the models other mods draw over the first-person arm. Vanilla draws that arm from the player
 * model after {@code setupAnim(player, 0, 0, 0, 0, 0)}, which {@link PlayerModelMixin} poses; a glove or a shield
 * on the arm (the Aether's, Deep Aether's) is a model of its mod's own, a plain {@code HumanoidModel} given the
 * same call under the same transform, and without this it stayed where an unposed arm would be while the arm
 * moved off from under it. Only while the first-person hands are drawn ({@link WallClingPose#inFirstPersonHands}),
 * so nothing changes for models in the world, and never for a player model, which its own mixin poses after its
 * sleeves are copied (this method runs inside {@code PlayerModel.setupAnim} too, through its super call).
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {

    @Shadow
    @Final
    public ModelPart head;
    @Shadow
    @Final
    public ModelPart hat;
    @Shadow
    @Final
    public ModelPart body;
    @Shadow
    @Final
    public ModelPart rightArm;
    @Shadow
    @Final
    public ModelPart leftArm;
    @Shadow
    @Final
    public ModelPart rightLeg;
    @Shadow
    @Final
    public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void walljumpunbound$firstPersonOverlayPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!WallClingPose.inFirstPersonHands() || (Object) this instanceof PlayerModel) return;
        float weight = WallClingPose.apply(entity, ageInTicks, this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg);
        if (weight > 0.0F) this.hat.copyFrom(this.head);
    }
}

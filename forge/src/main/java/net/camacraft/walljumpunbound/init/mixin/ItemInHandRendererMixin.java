package net.camacraft.walljumpunbound.init.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.camacraft.walljumpunbound.logic.WallClingPose;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the first-person hands being drawn, for {@link HumanoidModelMixin}. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V", at = @At("HEAD"))
    private void walljumpunbound$firstPersonHandsBegin(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffer, LocalPlayer player, int light, CallbackInfo ci) {
        WallClingPose.setFirstPersonHands(true);
    }

    @Inject(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V", at = @At("RETURN"))
    private void walljumpunbound$firstPersonHandsEnd(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffer, LocalPlayer player, int light, CallbackInfo ci) {
        WallClingPose.setFirstPersonHands(false);
    }
}

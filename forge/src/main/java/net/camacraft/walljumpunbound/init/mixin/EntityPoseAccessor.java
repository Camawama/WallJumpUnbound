package net.camacraft.walljumpunbound.init.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's own "does this pose fit here" test, which a gravity mod turns with the player's frame. */
@Mixin(Entity.class)
public interface EntityPoseAccessor {

    @Invoker("canEnterPose")
    boolean walljumpunbound$canEnterPose(Pose pose);
}

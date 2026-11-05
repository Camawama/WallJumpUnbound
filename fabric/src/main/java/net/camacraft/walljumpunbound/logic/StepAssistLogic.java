package net.camacraft.walljumpunbound.logic;

import net.camacraft.walljumpunbound.init.ServerConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

@Environment(EnvType.CLIENT)
public class StepAssistLogic {
    private static boolean collidesWithBlock(Level level, AABB box) {
        return !level.noCollision(box);
    }

    /**
     * A cling forces {@code horizontalCollision} on to keep hold of the wall and
     * pins the player's fall to a standstill, which is exactly the shape of
     * "walked into a one-block step" this reads: so every tick of a hold ended
     * with the player set onGround in mid-air. They stood on nothing until they
     * let go — jumping off thin air, taking no fall, and reporting the ground to
     * the server, which under Gravity Unbound is also what tells its surface
     * machinery the wall is a floor. A cling is never a step.
     */
    public static void doStepAssist(LocalPlayer pl) {
        if (WallJumpLogic.ticksWallClinged > 0) return;

        if (pl.horizontalCollision && ServerConfig.stepAssist && pl.getDeltaMovement().y > -0.2 && pl.getDeltaMovement().y < 0.01) {
            if (!collidesWithBlock(pl.level(), pl.getBoundingBox().inflate(0.01, -pl.maxUpStep() + 0.02, 0.01))) {
                pl.setOnGround(true);
            }
        }

        if (pl.isSprinting() && pl.getDeltaMovement().length() > 0.08) pl.horizontalCollision = false;
    }
}

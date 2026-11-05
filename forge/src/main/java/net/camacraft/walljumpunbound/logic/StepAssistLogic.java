package net.camacraft.walljumpunbound.logic;

import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.camafy.physics.Collisions;
import net.camacraft.walljumpunbound.init.ServerConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class StepAssistLogic {
    // Decided after this tick's move, spent on vanilla's sprint check at the
    // start of the next one (see beforeSprintCheck).
    private static boolean keepSprint;
    private static boolean sprintCheckSwapped;
    private static boolean realCollision;

    /**
     * A cling pins the player's fall to a standstill against a wall, which is
     * exactly the shape of "walked into a one-block step" this reads: so every
     * tick of a hold ended with the player set onGround in mid-air. They stood
     * on nothing until they let go — jumping off thin air, taking no fall, and
     * reporting the ground to the server, which under Gravity Unbound is also
     * what tells its surface machinery the wall is a floor. A cling is never a
     * step.
     *
     * A wall within the mod's reach counts as walked into here, but only here:
     * the mod no longer writes that into vanilla's horizontalCollision, which
     * other mods read as a real collision.
     */
    public static void doStepAssist(LocalPlayer pl) {
        keepSprint = false;
        if (WallJumpLogic.ticksWallClinged > 0) return;

        // The probe and the fall speed below are in world axes, so none of it
        // under a rotated gravity frame; nor while another mod owns the
        // player's motion (a grapple rope reads onGround).
        // The fall-speed window is the part of a jump spent near its top, which low gravity
        // stretches and zero gravity makes endless (floating by any wall read as standing): it
        // shrinks with the local gravity, and there is no step assist without any.
        // Nor for a body gliding on an elytra: vanilla ends a glide on any tick the body is on the ground, so a level
        // glide into a wall (a slime wall it bounces off, a ship's side) stopped the elytra here (2026-10-12).
        double strength = Math.min(1.0, Gravity.strength(pl));
        if (Gravity.frame(pl).isDefault() && strength >= Gravity.WEIGHTLESS_BELOW && !WallJumpLogic.motionOwnedElsewhere(pl)
                && !pl.isFallFlying()
                && (pl.horizontalCollision || WallJumpLogic.wallInReach())
                && ServerConfig.stepAssist && pl.getDeltaMovement().y > -0.2 * strength && pl.getDeltaMovement().y < 0.01 * strength) {
            if (!Collisions.blocked(pl.level(), pl.getBoundingBox().inflate(0.01, -pl.maxUpStep() + 0.02, 0.01))) {
                pl.setOnGround(true);
            }
        }

        keepSprint = pl.isSprinting() && pl.getDeltaMovement().length() > 0.08;
    }

    /**
     * Run just before vanilla's sprint check reads horizontalCollision, early
     * in the next aiStep (LocalPlayerMixin). Brushing a wall at speed keeps the
     * sprint, and a wall in the mod's reach counts as one walked into, as they
     * always have here; only now the field is lent to that one check and handed
     * back before the move, instead of being overwritten for everyone who reads
     * it after the tick.
     */
    public static void beforeSprintCheck(LocalPlayer pl) {
        boolean seen = !keepSprint && (pl.horizontalCollision || WallJumpLogic.wallInReach());
        keepSprint = false;
        if (seen != pl.horizontalCollision) {
            realCollision = pl.horizontalCollision;
            pl.horizontalCollision = seen;
            sprintCheckSwapped = true;
        }
    }

    /** Hands the real value back once the sprint check is done (still before the move). */
    public static void afterSprintCheck(LocalPlayer pl) {
        if (sprintCheckSwapped) {
            pl.horizontalCollision = realCollision;
            sprintCheckSwapped = false;
        }
    }
}

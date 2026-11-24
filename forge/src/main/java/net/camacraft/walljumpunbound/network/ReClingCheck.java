package net.camacraft.walljumpunbound.network;

import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.walljumpunbound.WallJumpMod;
import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.logic.ReClings;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The server's own count of clings on the wall just jumped from, per player, reset when the player stands on the ground.
 * A cling is made by the player's client from the rules the server sent it, so an honest client never grips past its
 * allowance ({@link ReClings}, read here from the boots the server sees and the server's own file); a grip reported past
 * it is not honoured: the pose is not shown to others, the catch is not charged, and the wall jump off it does not reset
 * the fall or cost hunger. A grip is the same wall as the last wall jump's and no lower than a block below that jump,
 * with no other wall beside the player (the client counts a grip only when every wall in reach is one jumped from, so
 * the server never counts one the client did not). Grips on a ship's hull and under turned gravity are left to the
 * client: the server's own collision sees neither the hull nor the frame the client's walls are named in.
 */
@Mod.EventBusSubscriber(modid = WallJumpMod.MODID)
public final class ReClingCheck {

    private static final class State {
        Direction gripWall;
        boolean gripHonoured = true;
        Direction jumpWall;
        double jumpHeight = Double.NaN;
        int used;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private ReClingCheck() {
    }

    /** A fresh grip on {@code wall}: whether it is honoured. */
    public static boolean grip(ServerPlayer player, Direction wall, boolean ship) {
        State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        s.gripWall = wall;
        s.gripHonoured = true;
        if (ModConfig.allowReClinging || wall == null || ship || s.jumpWall != wall || Double.isNaN(s.jumpHeight)) return true;
        if (!Gravity.frame(player).isDefault() || player.getY() < s.jumpHeight - 1.0 || otherWallBeside(player, wall)) return true;
        int allowance = ReClings.allowance(player, false, ModConfig.reClingEnchantment, ModConfig.maxReClingLevel);
        if (s.used < allowance) {
            s.used++;
            return true;
        }
        s.gripHonoured = false;
        return false;
    }

    /** A wall jump reported: whether it is honoured (not off a grip that was not). */
    public static boolean wallJumped(ServerPlayer player) {
        State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        if (!s.gripHonoured) return false;
        s.jumpWall = s.gripWall;
        s.jumpHeight = height(player);
        return true;
    }

    private static double height(ServerPlayer player) {
        return player.getY();
    }

    /** Whether a solid face is within 0.1 of the player's box on a side other than {@code wall}'s. */
    private static boolean otherWallBeside(ServerPlayer player, Direction wall) {
        AABB body = player.getBoundingBox().deflate(0.0, 0.1, 0.0);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d == wall) continue;
            if (!player.level().noCollision(player, body.move(d.getStepX() * 0.1, 0.0, d.getStepZ() * 0.1))) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient() || !event.player.onGround()) return;
        State s = STATES.get(event.player.getUUID());
        if (s == null) return;
        s.used = 0;
        s.jumpWall = null;
        s.jumpHeight = Double.NaN;
        s.gripHonoured = true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}

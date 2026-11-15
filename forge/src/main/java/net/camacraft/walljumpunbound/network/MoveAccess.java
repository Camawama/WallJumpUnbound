package net.camacraft.walljumpunbound.network;

import net.camacraft.camafy.minigame.MatchPolicies;
import net.camacraft.walljumpunbound.WallJumpMod;
import net.camacraft.walljumpunbound.network.message.MessageServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A host's rules may switch this mod's moves off for a player, or give a player the double jump (a minigame without
 * wall jumps, one with a double jump for everyone), asked through Camafy's match policies:
 * <ul>
 *   <li>{@code walljumpunbound:wall_jump} ({@code MatchPolicies.mayUse}): all the moves, wall jumps, clings, ledge
 *       grabs, double jumps and the enchantments that give them;</li>
 *   <li>{@code walljumpunbound:double_jump} ({@code MatchPolicies.mayUse}): the double jump alone, the Double Jump
 *       enchantment's included;</li>
 *   <li>{@code walljumpunbound:double_jumps} ({@code MatchPolicies.tuning}): how many jumps in the air a player has at
 *       least, without the Double Jump enchantment or the config's double jump (a host's grant; 0 or none leaves the
 *       player's own).</li>
 * </ul>
 * The moves are made by the client from the rules the server sent it, so the server tells that one client: twice a
 * second each online player is asked for, and a player whose answers changed is sent the server's rules again, as they
 * hold for them. A wall jump a refused client reports anyway is not honoured, nor a fall reset from a client that has
 * neither the moves nor a grant. With no host that answers, nobody is refused or granted anything and nothing is sent.
 */
@Mod.EventBusSubscriber(modid = WallJumpMod.MODID)
public final class MoveAccess {
    /** The mod's moves as Camafy's match policies know them ({@code MatchPolicies.mayUse}). */
    public static final ResourceLocation MECHANIC = new ResourceLocation(WallJumpMod.MODID, "wall_jump");
    /** The double jump alone. */
    public static final ResourceLocation DOUBLE_JUMP = new ResourceLocation(WallJumpMod.MODID, "double_jump");
    /** A host's grant of jumps in the air ({@code MatchPolicies.tuning}). */
    public static final ResourceLocation DOUBLE_JUMPS = new ResourceLocation(WallJumpMod.MODID, "double_jumps");
    /** The most jumps in the air a grant gives. */
    private static final int MAX_GRANT = 8;

    /** What a player's client was last told, when it differs from the server's own rules. */
    record Access(boolean moves, boolean doubleJump, int granted) {
        static final Access FREE = new Access(true, true, 0);
    }

    private static final Map<UUID, Access> TOLD = new HashMap<>();

    private MoveAccess() {}

    /** Whether the server has the moves switched off for this player right now. */
    public static boolean refused(ServerPlayer player) {
        Access a = TOLD.isEmpty() ? null : TOLD.get(player.getUUID());
        return a != null && !a.moves;
    }

    /** How many jumps in the air a host grants this player right now (0: none). */
    public static int granted(ServerPlayer player) {
        Access a = TOLD.isEmpty() ? null : TOLD.get(player.getUUID());
        return a == null ? 0 : a.granted;
    }

    /** The rules were sent to everyone anew (a config reload): the refused and the granted are told again at the next look. */
    public static void resync() {
        TOLD.clear();
    }

    private static Access ask(ServerPlayer player) {
        boolean moves = MatchPolicies.mayUse(player, MECHANIC);
        boolean doubleJump = moves && MatchPolicies.mayUse(player, DOUBLE_JUMP);
        double grant = doubleJump ? MatchPolicies.tuning(player, DOUBLE_JUMPS) : Double.NaN;
        int granted = Double.isFinite(grant) ? (int) Math.max(0, Math.min(MAX_GRANT, Math.floor(grant))) : 0;
        return new Access(moves, doubleJump, granted);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 10 != 0) return;
        if (TOLD.isEmpty() && !MatchPolicies.any()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Access now = ask(player);
            Access before = TOLD.getOrDefault(player.getUUID(), Access.FREE);
            if (now.equals(before)) continue;
            if (now.equals(Access.FREE)) TOLD.remove(player.getUUID());
            else TOLD.put(player.getUUID(), now);
            PacketHandler.sendToPlayer(player, MessageServerConfig.fromLocalConfig(now.moves, now.doubleJump, now.granted));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TOLD.remove(event.getEntity().getUUID());
    }
}

package net.camacraft.walljumpunbound.network;

import net.camacraft.camafy.network.CamafyChannel;
import net.camacraft.walljumpunbound.network.message.MessageFallDistance;
import net.camacraft.walljumpunbound.network.message.MessageServerConfig;
import net.camacraft.walljumpunbound.network.message.MessageWallCling;
import net.camacraft.walljumpunbound.network.message.MessageWallClingSync;
import net.camacraft.walljumpunbound.network.message.MessageWallJump;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import static net.camacraft.walljumpunbound.WallJumpMod.MODID;

public final class PacketHandler {
    private static final String PROTOCOL_VERSION = "3";
    public static final CamafyChannel CHANNEL = CamafyChannel.create(new ResourceLocation(MODID, "network"), PROTOCOL_VERSION);

    // Every message is pinned to the side that may send it, so a client can't
    // push MessageServerConfig at the server and overwrite its rules. The
    // registration order sets the message ids: keep it.
    public static void init() {
        CHANNEL.serverbound(MessageFallDistance.class, MessageFallDistance::encode, MessageFallDistance::decode, MessageFallDistance::handle)
                .serverbound(MessageWallJump.class, MessageWallJump::encode, MessageWallJump::decode, MessageWallJump::handle)
                .clientbound(MessageServerConfig.class, MessageServerConfig::encode, MessageServerConfig::decode, MessageServerConfig::handle)
                .serverbound(MessageWallCling.class, MessageWallCling::encode, MessageWallCling::decode, MessageWallCling::handle)
                .clientbound(MessageWallClingSync.class, MessageWallClingSync::encode, MessageWallClingSync::decode, MessageWallClingSync::handle);
    }

    public static <T> void sendToAll(T message) {
        CHANNEL.toAll(message);
    }

    public static <T> void sendToServer(T message) {
        CHANNEL.toServer(message);
    }

    public static <T> void sendToPlayer(ServerPlayer player, T message) {
        CHANNEL.toPlayer(player, message);
    }

    public static <T> void sendToTracking(Entity entity, T message) {
        CHANNEL.toTracking(entity, message);
    }
}

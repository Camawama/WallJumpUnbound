package net.camacraft.walljumpunbound.network.message;

import com.google.common.collect.Lists;
import net.camacraft.camafy.network.Packets;
import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.init.ModConfig.BlockListMode;
import net.camacraft.walljumpunbound.init.ServerConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record MessageServerConfig(byte[] config) {
    public static void encode(MessageServerConfig message, FriendlyByteBuf buffer) {
        buffer.writeByteArray(message.config);
    }

    public static MessageServerConfig decode(FriendlyByteBuf buffer) {
        return new MessageServerConfig(buffer.readByteArray());
    }

    /** This server's rules, as sent on join and again whenever the config file is reloaded. */
    public static MessageServerConfig fromLocalConfig() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        buffer.writeBoolean(ModConfig.allowReClinging);
        buffer.writeBoolean(ModConfig.onFallDoubleJump);
        buffer.writeBoolean(ModConfig.onFallWallCling);
        buffer.writeDouble(ModConfig.exhaustionWallJump);
        buffer.writeDouble(ModConfig.minFallDistance);
        buffer.writeDouble(ModConfig.elytraSpeedBoost);
        buffer.writeDouble(ModConfig.sprintSpeedBoost);
        buffer.writeBoolean(ModConfig.stepAssist);
        buffer.writeBoolean(ModConfig.doubleJumpEnabled);
        buffer.writeBoolean(ModConfig.wallJumpEnabled);
        buffer.writeDouble(ModConfig.wallJumpHeight);
        buffer.writeInt(ModConfig.wallSlideDelay);
        buffer.writeInt(ModConfig.stopWallSlideDelay);
        buffer.writeInt(ModConfig.maxWallJumps);
        writeList(buffer, ModConfig.blockList);
        buffer.writeEnum(ModConfig.blockListMode);
        buffer.writeBoolean(ModConfig.enableEnchantments);
        buffer.writeBoolean(ModConfig.wallJumpEnchantment);
        buffer.writeBoolean(ModConfig.doubleJumpEnchantment);
        buffer.writeBoolean(ModConfig.speedBoostEnchantment);
        buffer.writeDouble(ModConfig.speedBoostMultiplier);
        buffer.writeBoolean(ModConfig.ledgeGrab);

        // Send only the written bytes, not the buffer's whole backing array.
        byte[] data = new byte[buffer.writerIndex()];
        buffer.getBytes(0, data);
        return new MessageServerConfig(data);
    }

    // Registered PLAY_TO_CLIENT only; applied on the main thread so the game
    // never reads a half-written set of rules.
    public static void handle(MessageServerConfig message, Supplier<NetworkEvent.Context> supplier) {
        Packets.handleClient(supplier, () -> () -> apply(message));
    }

    private static void apply(MessageServerConfig message) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(message.config));

        ServerConfig.allowReClinging = buffer.readBoolean();
        ServerConfig.onFallDoubleJump = buffer.readBoolean();
        ServerConfig.onFallWallCling = buffer.readBoolean();
        ServerConfig.exhaustionWallJump = buffer.readDouble();
        ServerConfig.minFallDistance = buffer.readDouble();
        ServerConfig.elytraSpeedBoost = buffer.readDouble();
        ServerConfig.sprintSpeedBoost = buffer.readDouble();
        ServerConfig.stepAssist = buffer.readBoolean();
        ServerConfig.doubleJumpEnabled = buffer.readBoolean();
        ServerConfig.wallJumpEnabled = buffer.readBoolean();
        ServerConfig.wallJumpHeight = buffer.readDouble();
        ServerConfig.wallSlideDelay = buffer.readInt();
        ServerConfig.stopWallSlideDelay = buffer.readInt();
        ServerConfig.maxWallJumps = buffer.readInt();
        ServerConfig.blockList = readList(buffer);
        ServerConfig.blockListMode = buffer.readEnum(BlockListMode.class);
        ServerConfig.enableEnchantments = buffer.readBoolean();
        ServerConfig.wallJumpEnchantment = buffer.readBoolean();
        ServerConfig.doubleJumpEnchantment = buffer.readBoolean();
        ServerConfig.speedBoostEnchantment = buffer.readBoolean();
        ServerConfig.speedBoostMultiplier = buffer.readDouble();
        ServerConfig.ledgeGrab = buffer.readBoolean();
    }

    private static void writeList(FriendlyByteBuf buffer, List<String> list) {
        buffer.writeInt(list.size());
        for (String string : list) buffer.writeUtf(string);
    }

    private static List<String> readList(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        List<String> list = Lists.newArrayListWithCapacity(size);
        for (int i = 0; i < size; i++) list.add(buffer.readUtf());
        return list;
    }
}

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
        return fromLocalConfig(true, true, 0);
    }

    /**
     * This server's rules for one player, as a host's rules hold for them ({@code network.MoveAccess}). With
     * {@code moves} false the wall jump, the cling with its ledge grab, the double jump and the enchantments that give
     * them are off, whatever the config says; with {@code doubleJump} false the double jump alone is off, the Double Jump
     * enchantment's included; {@code granted} is how many jumps in the air the player has at least, enchantment or not
     * (written last, so a client from before it reads the rest and leaves it).
     */
    public static MessageServerConfig fromLocalConfig(boolean moves, boolean doubleJump, int granted) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        buffer.writeBoolean(ModConfig.allowReClinging);
        buffer.writeBoolean(ModConfig.onFallDoubleJump);
        buffer.writeBoolean(ModConfig.onFallWallCling);
        buffer.writeDouble(ModConfig.exhaustionWallJump);
        buffer.writeDouble(ModConfig.minFallDistance);
        buffer.writeDouble(ModConfig.elytraSpeedBoost);
        buffer.writeDouble(ModConfig.sprintSpeedBoost);
        buffer.writeBoolean(ModConfig.stepAssist);
        buffer.writeBoolean(moves && doubleJump && ModConfig.doubleJumpEnabled);
        buffer.writeBoolean(moves && ModConfig.wallJumpEnabled);
        buffer.writeDouble(ModConfig.wallJumpHeight);
        buffer.writeInt(ModConfig.wallSlideDelay);
        buffer.writeInt(ModConfig.stopWallSlideDelay);
        buffer.writeInt(ModConfig.maxWallJumps);
        writeList(buffer, ModConfig.blockList);
        buffer.writeEnum(ModConfig.blockListMode);
        buffer.writeBoolean(moves && ModConfig.enableEnchantments);
        buffer.writeBoolean(ModConfig.wallJumpEnchantment);
        buffer.writeBoolean(doubleJump && ModConfig.doubleJumpEnchantment);
        buffer.writeBoolean(ModConfig.speedBoostEnchantment);
        buffer.writeDouble(ModConfig.speedBoostMultiplier);
        buffer.writeBoolean(moves && ModConfig.ledgeGrab);
        buffer.writeVarInt(moves && doubleJump ? Math.max(0, granted) : 0);

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
        // a server from before grants wrote nothing more: nothing is granted
        ServerConfig.grantedDoubleJumps = buffer.isReadable() ? Math.max(0, Math.min(8, buffer.readVarInt())) : 0;
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

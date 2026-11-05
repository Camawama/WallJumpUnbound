package net.camacraft.walljumpunbound.network.message;

import net.camacraft.camafy.network.Packets;
import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.init.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MessageWallJump(boolean didWallJump) {
    public static void encode(MessageWallJump message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.didWallJump);
    }

    public static MessageWallJump decode(FriendlyByteBuf buffer) {
        return new MessageWallJump(buffer.readBoolean());
    }


    public static void handle(MessageWallJump message, Supplier<NetworkEvent.Context> supplier) {
        Packets.handleServer(supplier, player -> {
            boolean wallJumpEnabled = ModConfig.wallJumpEnabled || (ModConfig.enableEnchantments && ModConfig.wallJumpEnchantment);
            if (message.didWallJump && wallJumpEnabled) {
                player.resetFallDistance();
                player.causeFoodExhaustion((float) ServerConfig.exhaustionWallJump);
            }
        });
    }
}

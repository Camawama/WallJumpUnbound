package net.camacraft.walljumpunbound.init.mixin;

import net.camacraft.walljumpunbound.network.PacketHandler;
import net.camacraft.walljumpunbound.network.message.MessageServerConfig;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    public void placeNewPlayer(Connection connection, ServerPlayer player, CallbackInfo ci) {
        PacketHandler.sendToPlayer(player, MessageServerConfig.fromLocalConfig());
    }
}

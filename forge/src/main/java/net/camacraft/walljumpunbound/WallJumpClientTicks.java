package net.camacraft.walljumpunbound;

import net.camacraft.walljumpunbound.logic.WallJumpLogic;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The end of each client tick, after every entity and block entity has moved: where a hold on a moving Create
 * contraption takes the part of the contraption's step that came after the player's own tick.
 */
@Mod.EventBusSubscriber(modid = WallJumpMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WallJumpClientTicks {

    private WallJumpClientTicks() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        WallJumpLogic.afterEntitiesTicked(mc.player);
    }
}

package net.camacraft.walljumpunbound.logic;

import net.camacraft.walljumpunbound.init.ModEnchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * How many clings on the wall just jumped from a player has before touching the ground again: all of them while
 * {@code allowReClinging} is on, else the level of Re-cling on their boots, up to {@code maxReClingLevel}, while
 * {@code reClingEnchantment} is on (its own switch alone, not {@code enableEnchantments}). Each side asks with its own copy
 * of the rules: the client ({@code ServerConfig}, the server's rules as sent) where the cling is made, the server
 * ({@code ModConfig}) where it is checked ({@code network.ReClingCheck}).
 */
public final class ReClings {

    private ReClings() {
    }

    /** {@link Integer#MAX_VALUE} when re-clinging is on for everyone. */
    public static int allowance(LivingEntity player, boolean allowReClinging, boolean enchantments, int maxLevel) {
        if (allowReClinging) return Integer.MAX_VALUE;
        if (!enchantments || maxLevel <= 0) return 0;
        int level = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.RE_CLING.get(), player.getItemBySlot(EquipmentSlot.FEET));
        return Math.max(0, Math.min(level, maxLevel));
    }
}

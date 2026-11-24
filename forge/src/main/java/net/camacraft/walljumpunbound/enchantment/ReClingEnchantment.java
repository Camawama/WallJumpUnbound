package net.camacraft.walljumpunbound.enchantment;

import net.camacraft.walljumpunbound.init.ServerConfig;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Re-cling I to III on boots: each level is one more cling on the wall just jumped from before the wearer touches the
 * ground again, where {@code allowReClinging} is off (see {@code logic.ReClings}). Fits with the mod's other three, but
 * follows its own switch ({@code reClingEnchantment}) alone, not {@code enableEnchantments}.
 */
public class ReClingEnchantment extends Enchantment {

    public ReClingEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.ARMOR_FEET, new EquipmentSlot[]{EquipmentSlot.FEET});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return level * 15;
    }

    @Override
    public int getMaxCost(int level) {
        return level * 60;
    }

    // The enchantment is always registered; config only controls availability,
    // so existing enchanted items survive config changes. Its own switch alone: enableEnchantments gates the
    // three that stand in for moves a server can give everyone, and a pack that gives those keeps it off.
    @Override
    public boolean isDiscoverable() {
        return ServerConfig.reClingEnchantment;
    }

    @Override
    public boolean isTradeable() {
        return ServerConfig.reClingEnchantment;
    }
}

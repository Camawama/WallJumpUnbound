package net.camacraft.walljumpunbound.enchantment;

import net.camacraft.walljumpunbound.init.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import java.util.Set;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;

public class DoubleJumpEnchantment extends Enchantment {
    // Grapple Unbound's double jump: the same move, on the same boots, off the same
    // key. Only one of the two goes on a pair (matched by id, so neither mod
    // needs the other installed).
    private static final Set<ResourceLocation> GRAPPLE_DOUBLE_JUMP = Set.of(
            new ResourceLocation("grappleunbound", "doublejumpenchantment"),
            new ResourceLocation("grapplemod", "doublejumpenchantment"));

    public DoubleJumpEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.ARMOR_FEET, new EquipmentSlot[]{EquipmentSlot.FEET});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
    public int getMinCost(int level) {
        return level * 20;
    }

    @Override
    public int getMaxCost(int level) {
        return level * 60;
    }

    @Override
    protected boolean checkCompatibility(Enchantment enchantment) {
        if (GRAPPLE_DOUBLE_JUMP.contains(BuiltInRegistries.ENCHANTMENT.getKey(enchantment)))
            return false;
        if (enchantment instanceof ProtectionEnchantment protection)
            return protection.type != ProtectionEnchantment.Type.FALL;
        return this != enchantment;
    }

    // The enchantment is always registered; config only controls availability,
    // so existing enchanted items survive config changes.
    @Override
    public boolean isDiscoverable() {
        return ServerConfig.enableEnchantments && ServerConfig.doubleJumpEnchantment;
    }

    @Override
    public boolean isTradeable() {
        return ServerConfig.enableEnchantments && ServerConfig.doubleJumpEnchantment;
    }
}

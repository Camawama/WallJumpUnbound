package net.camacraft.walljumpunbound.enchantment;

import net.camacraft.walljumpunbound.init.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import java.util.Set;

public class WallJumpEnchantment extends Enchantment {
    // Grapple Unbound's wall-run: both take the player over at a wall, on the
    // same boots, and would fight for the same moment. Only one of the two goes
    // on a pair (matched by id, so neither mod needs the other installed; the
    // old grapplemod id covers GrappleVS and the upstream Reforged mod).
    private static final Set<ResourceLocation> GRAPPLE_WALL_RUN = Set.of(
            new ResourceLocation("grappleunbound", "wallrunenchantment"),
            new ResourceLocation("grapplemod", "wallrunenchantment"));

    public WallJumpEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.ARMOR_FEET, new EquipmentSlot[]{EquipmentSlot.FEET});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 20;
    }

    @Override
    public int getMaxCost(int level) {
        return 60;
    }

    @Override
    protected boolean checkCompatibility(Enchantment enchantment) {
        return super.checkCompatibility(enchantment) && !GRAPPLE_WALL_RUN.contains(BuiltInRegistries.ENCHANTMENT.getKey(enchantment));
    }

    // The enchantment is always registered; config only controls availability,
    // so existing enchanted items survive config changes.
    @Override
    public boolean isDiscoverable() {
        return ServerConfig.enableEnchantments && ServerConfig.wallJumpEnchantment;
    }

    @Override
    public boolean isTradeable() {
        return ServerConfig.enableEnchantments && ServerConfig.wallJumpEnchantment;
    }
}

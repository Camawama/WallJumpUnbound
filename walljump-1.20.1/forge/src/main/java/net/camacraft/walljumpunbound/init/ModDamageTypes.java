package net.camacraft.walljumpunbound.init;

import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.camafy.physics.VanillaFall;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

import static net.camacraft.walljumpunbound.WallJumpMod.MODID;

/**
 * The mod's damage types. They are data: the type itself and the tags that
 * make it behave like a fall (armour does not stop it, Feather Falling does)
 * live under {@code data/} in the common resources.
 */
public final class ModDamageTypes {

    /** Catching a wall after a long fall. */
    public static final ResourceKey<DamageType> WALL_CLING =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(MODID, "wall_cling"));

    private ModDamageTypes() {
    }

    /** The wall-cling damage source, or plain fall damage if the data for it is missing. */
    public static DamageSource wallCling(Entity entity) {
        return entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(WALL_CLING)
                .<DamageSource>map(DamageSource::new)
                .orElseGet(() -> entity.damageSources().fall());
    }

    /**
     * Hurts a player who has just caught a wall, if they fell far enough for
     * it: a share of the damage the ground would have done, from the server's
     * own record of the fall so the client cannot talk it down. Runs on the
     * server only.
     */
    public static void hurtForCatchingWall(ServerPlayer player) {
        if (!ModConfig.clingFallDamage) return;
        // A fall is only a fall under gravity: in zero gravity the distance drifted "down" costs
        // nothing, and in low gravity it lands like the shorter drop that reaches the same speed.
        double strength = Math.min(1.0, Gravity.strength(player));
        if (strength < Gravity.WEIGHTLESS_BELOW) return;
        float fall = (float) (player.fallDistance * strength);
        // A fall the ground itself would forgive is forgiven here too.
        double threshold = Math.max(ModConfig.clingFallDamageMinDistance, ModConfig.minFallDistance);
        if (!(fall >= threshold)) return;

        // The ground's own sum (LivingEntity.calculateFallDamage), scaled by the fraction.
        int damage = VanillaFall.fallDamage(player, fall, (float) ModConfig.clingFallDamageFraction);
        if (damage > 0) player.hurt(wallCling(player), damage);
    }
}

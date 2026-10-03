package net.camacraft.walljumpunbound.compat;

import net.minecraft.world.phys.Vec3;

/** Where a ray met a Create contraption's block, and that face's outward normal, both in the world. */
public record ContraptionHit(Vec3 location, Vec3 normal) {
}

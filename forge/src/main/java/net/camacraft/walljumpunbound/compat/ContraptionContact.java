package net.camacraft.walljumpunbound.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where a probe meets a block of a Create contraption: the contraption's entity, the block's cell in the
 * contraption's own frame and its state, the face of it met (in that frame) and that face's outward normal in the
 * world as the contraption stands this tick, and how far the probe reaches past the face. Names no Create type, so
 * the rest of the mod can hold one without Create installed.
 */
public record ContraptionContact(Entity contraption, BlockPos local, BlockState state, Direction face, Vec3 normal, double depth) {
}

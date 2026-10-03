package net.camacraft.walljumpunbound.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import net.camacraft.walljumpunbound.compat.ContraptionContact;
import net.camacraft.walljumpunbound.compat.ContraptionHit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The walls of Create contraptions, in Create's own pose: everything here that names a Create type. Only ever
 * called through {@code ContraptionCompat}, and only with Create loaded. Every query takes the contraption as Create
 * stands it this tick ({@code toLocalVector}/{@code toGlobalVector} at the tick's end, the transform its collider and
 * renderer use): a probe goes into the contraption's frame as an oriented box and is tested against each block's
 * collision shape there, exactly, at any rotation.
 */
public final class CreateWalls {

    /** At most this many cells of one contraption are read for one probe (a probe is a few blocks at most). */
    private static final int MAX_CELLS = 512;
    /** Contraption entities are looked for this far around a probe (their entity box is Create's bounds). */
    private static final double SEARCH_MARGIN = 0.5;

    private CreateWalls() {
    }

    /** Every face of every contraption block the world box overlaps, one per block (see {@link ContraptionContact}). */
    public static void contacts(Level level, AABB worldBox, Consumer<ContraptionContact> out) {
        for (AbstractContraptionEntity c : near(level, worldBox)) scan(c, worldBox, out, false);
    }

    /** Whether any contraption block's collision shape overlaps the world box. */
    public static boolean touches(Level level, AABB worldBox) {
        for (AbstractContraptionEntity c : near(level, worldBox)) {
            if (scan(c, worldBox, null, true)) return true;
        }
        return false;
    }

    /** The first contraption block face the segment {@code from -> to} meets, or null. */
    @Nullable
    public static ContraptionHit clip(Level level, Vec3 from, Vec3 to) {
        ContraptionHit best = null;
        double bestSq = Double.POSITIVE_INFINITY;
        for (AbstractContraptionEntity c : near(level, new AABB(from, to))) {
            Contraption contraption = c.getContraption();
            Map<BlockPos, StructureBlockInfo> blocks = contraption.getBlocks();
            Vec3 a = c.toLocalVector(from, 1.0f);
            Vec3 b = c.toLocalVector(to, 1.0f);
            int x0 = floor(Math.min(a.x, b.x)), x1 = floor(Math.max(a.x, b.x));
            int y0 = floor(Math.min(a.y, b.y)) - 1, y1 = floor(Math.max(a.y, b.y));
            int z0 = floor(Math.min(a.z, b.z)), z1 = floor(Math.max(a.z, b.z));
            if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > MAX_CELLS) continue;
            BlockGetter world = contraption.getContraptionWorld();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        cursor.set(x, y, z);
                        StructureBlockInfo info = blocks.get(cursor);
                        if (info == null || info.state().isAir()) continue;
                        VoxelShape shape = info.state().getCollisionShape(world, cursor, CollisionContext.empty());
                        if (shape.isEmpty()) continue;
                        BlockPos pos = cursor.immutable();
                        BlockHitResult hit = shape.clip(a, b, pos);
                        if (hit == null || hit.getType() != BlockHitResult.Type.BLOCK) continue;
                        double d = hit.getLocation().distanceToSqr(a);
                        if (d >= bestSq) continue;
                        bestSq = d;
                        Vec3 at = c.toGlobalVector(hit.getLocation(), 1.0f);
                        best = new ContraptionHit(at, worldNormal(c, hit.getLocation(), hit.getDirection()));
                    }
                }
            }
        }
        return best;
    }

    /** Whether the entity is a live contraption with blocks. */
    public static boolean alive(@Nullable Entity entity) {
        return entity instanceof AbstractContraptionEntity c && c.isAlive() && c.getContraption() != null;
    }

    /** A world point in the contraption's frame, as it stands at the end of this tick. */
    public static Vec3 toLocal(Entity contraption, Vec3 world) {
        return ((AbstractContraptionEntity) contraption).toLocalVector(world, 1.0f);
    }

    /** A point of the contraption's frame in the world, as it stands at the end of this tick. */
    public static Vec3 toWorld(Entity contraption, Vec3 local) {
        return ((AbstractContraptionEntity) contraption).toGlobalVector(local, 1.0f);
    }

    /** A point of the contraption's frame in the world, as it stood at the start of this tick. */
    public static Vec3 toWorldBefore(Entity contraption, Vec3 local) {
        return ((AbstractContraptionEntity) contraption).toGlobalVector(local, 0.0f, true);
    }

    // ---- the scan -------------------------------------------------------------------------------------

    /**
     * The live contraptions whose box reaches the probe, from Create's own list of the level's loaded contraptions.
     * Not an entity search: that only looks in the entity sections within two blocks of the box, and a contraption's
     * entity sits at its anchor (a bearing's block), so a player carried round on a long arm left its section's reach
     * and the arm's own wall vanished from under the grip.
     */
    private static List<AbstractContraptionEntity> near(Level level, AABB box) {
        Map<Integer, WeakReference<AbstractContraptionEntity>> loaded = ContraptionHandler.loadedContraptions.get(level);
        if (loaded == null || loaded.isEmpty()) return List.of();
        AABB reach = box.inflate(SEARCH_MARGIN);
        List<AbstractContraptionEntity> out = new ArrayList<>(2);
        for (WeakReference<AbstractContraptionEntity> ref : loaded.values()) {
            AbstractContraptionEntity c = ref.get();
            if (c == null || !c.isAlive() || c.getContraption() == null) continue;
            if (c.getBoundingBox().intersects(reach)) out.add(c);
        }
        return out;
    }

    /**
     * The world box against one contraption's blocks: the box goes into the contraption's frame as an oriented box
     * (its centre and its three half-edges taken through Create's transform), and each block's collision boxes there
     * are tested against it by separating axes. Returns whether anything overlapped.
     */
    private static boolean scan(AbstractContraptionEntity c, AABB worldBox, @Nullable Consumer<ContraptionContact> out, boolean firstOnly) {
        Contraption contraption = c.getContraption();
        Map<BlockPos, StructureBlockInfo> blocks = contraption.getBlocks();
        if (blocks.isEmpty()) return false;
        Vec3 worldCenter = worldBox.getCenter();
        Vec3 center = c.toLocalVector(worldCenter, 1.0f);
        Vec3[] half = {
                c.toLocalVector(worldCenter.add(worldBox.getXsize() * 0.5, 0.0, 0.0), 1.0f).subtract(center),
                c.toLocalVector(worldCenter.add(0.0, worldBox.getYsize() * 0.5, 0.0), 1.0f).subtract(center),
                c.toLocalVector(worldCenter.add(0.0, 0.0, worldBox.getZsize() * 0.5), 1.0f).subtract(center)};
        double extX = Math.abs(half[0].x) + Math.abs(half[1].x) + Math.abs(half[2].x);
        double extY = Math.abs(half[0].y) + Math.abs(half[1].y) + Math.abs(half[2].y);
        double extZ = Math.abs(half[0].z) + Math.abs(half[1].z) + Math.abs(half[2].z);
        if (!Double.isFinite(extX + extY + extZ + center.x + center.y + center.z)) return false;
        // one cell lower too, for blocks whose collision rises above their cell (fences, walls)
        int x0 = floor(center.x - extX), x1 = floor(center.x + extX);
        int y0 = floor(center.y - extY) - 1, y1 = floor(center.y + extY);
        int z0 = floor(center.z - extZ), z1 = floor(center.z + extZ);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > MAX_CELLS) return false;

        Vec3[] axes = satAxes(half);
        BlockGetter world = contraption.getContraptionWorld();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean found = false;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    cursor.set(x, y, z);
                    StructureBlockInfo info = blocks.get(cursor);
                    if (info == null) continue;
                    BlockState state = info.state();
                    if (state.isAir()) continue;
                    VoxelShape shape = state.getCollisionShape(world, cursor, CollisionContext.empty());
                    if (shape.isEmpty()) continue;
                    for (AABB box : shape.toAabbs()) {
                        double[] hit = obbContact(center, half, axes, box.move(cursor));
                        if (hit == null) continue;
                        found = true;
                        if (firstOnly) return true;
                        if (out != null) {
                            int axis = (int) hit[0];
                            Direction face = Direction.fromAxisAndDirection(Direction.Axis.values()[axis],
                                    hit[1] > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
                            BlockPos pos = cursor.immutable();
                            out.accept(new ContraptionContact(c, pos, state, face, worldNormal(c, Vec3.atCenterOf(pos), face), hit[2]));
                        }
                        break;
                    }
                }
            }
        }
        return found;
    }

    /** The world direction of a face of the contraption's frame at a point of it, this tick. */
    private static Vec3 worldNormal(AbstractContraptionEntity c, Vec3 localPoint, Direction face) {
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 world = c.toGlobalVector(localPoint.add(n), 1.0f).subtract(c.toGlobalVector(localPoint, 1.0f));
        double length = world.length();
        return length > 1.0e-9 ? world.scale(1.0 / length) : n;
    }

    /**
     * The 15 separating-axis candidates, as unit vectors: the block's 3 axes, the probe's 3 edges and their 9 cross
     * products (as Camafy's ship contact scan does; degenerate ones left null).
     */
    private static Vec3[] satAxes(Vec3[] half) {
        Vec3[] axes = new Vec3[15];
        axes[0] = new Vec3(1.0, 0.0, 0.0);
        axes[1] = new Vec3(0.0, 1.0, 0.0);
        axes[2] = new Vec3(0.0, 0.0, 1.0);
        for (int j = 0; j < 3; j++) {
            double lengthSq = half[j].lengthSqr();
            axes[3 + j] = lengthSq > 1.0e-24 ? half[j].scale(1.0 / Math.sqrt(lengthSq)) : null;
        }
        int n = 6;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Vec3 edge = axes[3 + j];
                Vec3 cross = edge == null ? null : axes[i].cross(edge);
                axes[n++] = cross != null && cross.lengthSqr() > 1.0e-12 ? cross.normalize() : null;
            }
        }
        return axes;
    }

    /**
     * Separating-axis test between the probe's oriented box and an axis-aligned block box (both in the contraption's
     * frame). Null when they don't overlap (touching doesn't count); otherwise {axis, sign, depth} of the block face
     * the probe goes least far past.
     */
    @Nullable
    private static double[] obbContact(Vec3 center, Vec3[] half, Vec3[] axes, AABB box) {
        Vec3 t = center.subtract(box.getCenter());
        double ax = box.getXsize() * 0.5, ay = box.getYsize() * 0.5, az = box.getZsize() * 0.5;
        int faceAxis = 0;
        double faceSign = 1.0;
        double faceDepth = Double.MAX_VALUE;
        for (int i = 0; i < axes.length; i++) {
            Vec3 axis = axes[i];
            if (axis == null) continue;
            double ra = ax * Math.abs(axis.x) + ay * Math.abs(axis.y) + az * Math.abs(axis.z);
            double rb = Math.abs(half[0].dot(axis)) + Math.abs(half[1].dot(axis)) + Math.abs(half[2].dot(axis));
            double along = t.dot(axis);
            double overlap = ra + rb - Math.abs(along);
            if (overlap <= 1.0e-7) return null;
            if (i < 3 && overlap < faceDepth) {
                faceAxis = i;
                faceDepth = overlap;
                faceSign = along < 0.0 ? -1.0 : 1.0;
            }
        }
        return new double[]{faceAxis, faceSign, faceDepth};
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}

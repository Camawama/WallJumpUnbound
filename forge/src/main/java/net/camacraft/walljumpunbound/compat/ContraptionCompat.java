package net.camacraft.walljumpunbound.compat;

import net.camacraft.walljumpunbound.compat.create.CreateWalls;
import net.camacraft.walljumpunbound.init.ModConfig;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The mod's Create state: which contraption walls the probes found, and the contraption a cling is held to. A
 * contraption is an entity whose blocks vanilla's collision never sees (Create collides with them itself), so they
 * are probed here, in the contraption's own pose, the way ship hulls are probed through Camafy. Names no Create type:
 * everything that does is in {@code compat.create.CreateWalls}, reached only while Create is loaded, so this class is
 * safe to load in any pack.
 */
public final class ContraptionCompat {

    /** Whether Create is installed. */
    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("create");

    /** A face standing within 60 degrees of vertical is a wall; flatter is floor or ceiling. */
    private static final double WALL_TILT_COS = 0.5;
    /** A face lying within 60 degrees of level can be stood on. */
    private static final double FLOOR_COS = 0.5;

    private static final Map<Direction, ContraptionContact> WALLS = new EnumMap<>(Direction.class);
    // The held contraption and the hold in its own frame. The frame is the contraption's block grid, so the hold
    // turns and slides with it wherever Create moves it.
    private static Entity clingContraption;
    private static Vec3 clingLocal;

    private ContraptionCompat() {
    }

    /** Create installed and the compat on in the config. */
    public static boolean enabled() {
        return LOADED && ModConfig.enableCreateCompat;
    }

    // ---- walls found this tick ---------------------------------------------------------------------------

    public static void clearWalls() {
        WALLS.clear();
    }

    public static void recordWall(Direction direction, ContraptionContact contact) {
        WALLS.put(direction, contact);
    }

    public static boolean isWall(Direction direction) {
        return WALLS.containsKey(direction);
    }

    /** The contraption wall tracked that way, or null. */
    @Nullable
    public static ContraptionContact getWall(Direction direction) {
        return WALLS.get(direction);
    }

    /** Outward normal of the tracked contraption wall in world space, null when none is tracked there. */
    @Nullable
    public static Vec3 getWallNormal(Direction direction) {
        ContraptionContact wall = WALLS.get(direction);
        return wall == null ? null : wall.normal();
    }

    /** How far the last probe reached past the tracked wall's face, 0 when none is tracked there. */
    public static double getWallDepth(Direction direction) {
        ContraptionContact wall = WALLS.get(direction);
        return wall == null ? 0.0 : wall.depth();
    }

    /**
     * The contraption wall the probe is touching, if any: every contraption block the probe overlaps is classified
     * by the face it meets, faces that are floor or ceiling to {@code up} are left out, and of the walls left the
     * deepest contact wins, with {@code toward} (the way the player presses, may be null) settling near-ties, as for
     * ship hulls. Nothing is recorded; see {@link #recordWall}.
     */
    @Nullable
    public static ContraptionContact findWall(Level level, AABB probe, Vec3 up, @Nullable Vec3 toward) {
        if (!enabled()) return null;
        ContraptionContact[] best = {null};
        double[] bestScore = {Double.NEGATIVE_INFINITY};
        CreateWalls.contacts(level, probe, contact -> {
            if (Math.abs(contact.normal().dot(up)) > WALL_TILT_COS) return;
            double score = contact.depth() + (toward == null ? 0.0 : 0.02 * -contact.normal().dot(toward));
            if (score > bestScore[0]) {
                bestScore[0] = score;
                best[0] = contact;
            }
        });
        return best[0];
    }

    /** Whether any contraption block's collision shape overlaps the world box. */
    public static boolean intersects(Level level, AABB box) {
        return enabled() && CreateWalls.touches(level, box);
    }

    /** Whether the box overlaps a contraption block on a face that can be stood on (a deck under it). */
    public static boolean floorWithin(Level level, AABB box, Vec3 up) {
        if (!enabled()) return false;
        boolean[] found = {false};
        CreateWalls.contacts(level, box, contact -> {
            if (contact.normal().dot(up) > FLOOR_COS) found[0] = true;
        });
        return found[0];
    }

    /** The first contraption block face the segment meets, or null (and null without Create). */
    @Nullable
    public static ContraptionHit clip(Level level, Vec3 from, Vec3 to) {
        return enabled() ? CreateWalls.clip(level, from, to) : null;
    }

    // ---- the hold ----------------------------------------------------------------------------------------

    /** Remembers the cling position in the held contraption's frame, when the wall that way is a contraption's. */
    public static void captureClingAnchor(Entity player, Direction clingDirection) {
        clingContraption = null;
        clingLocal = null;
        ContraptionContact wall = WALLS.get(clingDirection);
        if (wall == null || !CreateWalls.alive(wall.contraption())) return;
        clingContraption = wall.contraption();
        clingLocal = CreateWalls.toLocal(clingContraption, player.position());
    }

    /** Forgets the held contraption. */
    public static void clearClingAnchor() {
        clingContraption = null;
        clingLocal = null;
    }

    /** Whether a contraption is held (and still there). */
    public static boolean holding() {
        return clingContraption != null && CreateWalls.alive(clingContraption);
    }

    /**
     * Where the hold is in the world as the contraption stands at the end of this tick, following its movement and
     * turning. Null when no contraption is held, or it has gone (disassembled, unloaded).
     */
    @Nullable
    public static Vec3 getClingWorldPos() {
        if (!holding()) return null;
        return CreateWalls.toWorld(clingContraption, clingLocal);
    }

    /**
     * How far the held contraption turned the hold about the world's up this tick, in degrees (the player's yaw
     * turns with it, as Valkyrien Skies turns a rider's): measured on whichever of the contraption's own axes lies
     * flattest, so a contraption tipped on its side still reads. 0 when none is held.
     */
    public static float clingYawStep() {
        if (!holding()) return 0.0F;
        Vec3[] axes = {new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 1.0, 0.0)};
        Vec3 bestNow = null, bestBefore = null;
        double flattest = -1.0;
        Vec3 at = CreateWalls.toWorld(clingContraption, clingLocal);
        Vec3 before = CreateWalls.toWorldBefore(clingContraption, clingLocal);
        for (Vec3 axis : axes) {
            Vec3 now = CreateWalls.toWorld(clingContraption, clingLocal.add(axis)).subtract(at);
            Vec3 then = CreateWalls.toWorldBefore(clingContraption, clingLocal.add(axis)).subtract(before);
            double flat = Math.min(now.horizontalDistanceSqr(), then.horizontalDistanceSqr());
            if (flat > flattest) {
                flattest = flat;
                bestNow = now;
                bestBefore = then;
            }
        }
        if (bestNow == null || flattest < 0.25) return 0.0F;
        float yawNow = (float) Math.toDegrees(Math.atan2(-bestNow.x, bestNow.z));
        float yawBefore = (float) Math.toDegrees(Math.atan2(-bestBefore.x, bestBefore.z));
        return Mth.wrapDegrees(yawNow - yawBefore);
    }

    /**
     * Velocity (blocks per tick, world) of the tracked contraption at a world point: the held one, else the one of
     * any tracked wall; zero when none. Its whole motion there, the turn included.
     */
    public static Vec3 pointVelocity(Vec3 worldPoint) {
        Entity c = holding() ? clingContraption : null;
        if (c == null) {
            for (ContraptionContact wall : WALLS.values()) {
                if (CreateWalls.alive(wall.contraption())) {
                    c = wall.contraption();
                    break;
                }
            }
        }
        if (c == null) return Vec3.ZERO;
        Vec3 local = CreateWalls.toLocal(c, worldPoint);
        return CreateWalls.toWorld(c, local).subtract(CreateWalls.toWorldBefore(c, local));
    }

    /** One line per contraption contact the probe makes, for the debug log. */
    public static String describe(Level level, AABB probe, Vec3 up) {
        if (!enabled()) return "Create compat off";
        StringBuilder out = new StringBuilder();
        CreateWalls.contacts(level, probe, contact -> {
            if (out.length() > 0) out.append(", ");
            double tilt = contact.normal().dot(up);
            BlockState state = contact.state();
            out.append("contraption ").append(contact.contraption().getId()).append(' ').append(contact.local().toShortString())
                    .append(' ').append(state.getBlock().getName().getString())
                    .append(Math.abs(tilt) > WALL_TILT_COS ? (tilt > 0 ? " floor" : " ceiling") : " wall")
                    .append(String.format(Locale.ROOT, " n=%.2f,%.2f,%.2f d=%.3f",
                            contact.normal().x, contact.normal().y, contact.normal().z, contact.depth()));
        });
        return out.length() == 0 ? "no contraption block touched" : out.toString();
    }
}

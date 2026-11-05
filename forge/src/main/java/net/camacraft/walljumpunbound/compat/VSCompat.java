package net.camacraft.walljumpunbound.compat;

import net.camacraft.camafy.compat.vs.ShipContact;
import net.camacraft.camafy.compat.vs.ShipEntities;
import net.camacraft.camafy.compat.vs.ShipRef;
import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.camafy.util.math.Units;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The mod's Valkyrien Skies state: which ship walls the probes found, and the
 * ship a cling is anchored to. Every ship query goes through Camafy's ship
 * utilities, which name no VS classes and answer as if there were no ships
 * when VS is absent, so this class is safe to load in any pack.
 */
public final class VSCompat {

    /** A face standing within 60 degrees of vertical is a wall; flatter is floor or ceiling. */
    private static final double WALL_TILT_COS = 0.5;

    private static final Map<Direction, ShipContact> SHIP_WALLS = new EnumMap<>(Direction.class);
    // The clung ship and the hold on it in ship space. A ShipRef only keeps the
    // id, so it never answers from a ship that has since unloaded.
    private static ShipRef clingShip;
    private static Vec3 clingAnchor;
    // whether the hold has made the player a rider, so letting go can end that
    private static boolean riding;

    private VSCompat() {
    }

    public static void clearShipWalls() {
        SHIP_WALLS.clear();
    }

    public static void forgetShipWall(Direction direction) {
        SHIP_WALLS.remove(direction);
    }

    public static boolean isShipWall(Direction direction) {
        return SHIP_WALLS.containsKey(direction);
    }

    public static BlockPos getShipWallPos(Direction direction) {
        ShipContact wall = SHIP_WALLS.get(direction);
        return wall == null ? null : wall.pos();
    }

    /** Outward normal of the tracked ship wall in world space, null when none is tracked there. */
    public static Vec3 getShipWallNormal(Direction direction) {
        ShipContact wall = SHIP_WALLS.get(direction);
        return wall == null ? null : wall.normal();
    }

    /** How far the last probe reached past the tracked wall's face, 0 when none is tracked there. */
    public static double getShipWallDepth(Direction direction) {
        ShipContact wall = SHIP_WALLS.get(direction);
        return wall == null ? 0.0 : wall.depth();
    }

    public static void recordShipWall(Direction direction, ShipContact contact) {
        SHIP_WALLS.put(direction, contact);
    }

    /**
     * The ship wall the probe is touching, if any. Every ship block the probe
     * overlaps is classified by the face it meets; faces that are floor or
     * ceiling to the given up are ignored, so a deck under the feet or a hull
     * leaning out below them never reads as a wall. Of the walls left, the
     * deepest contact wins, with {@code toward} (the way the player is pressing,
     * may be null) breaking near-ties so a corner is resolved to the face they
     * are actually against. Nothing is recorded; see {@link #recordShipWall}.
     */
    public static ShipContact findShipWall(Level level, AABB probe, Vec3 up, Vec3 toward) {
        ShipContact best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (ShipRef ship : Ships.intersecting(level, probe)) {
            for (ShipContact contact : ship.contacts(probe)) {
                if (Math.abs(contact.normal().dot(up)) > WALL_TILT_COS) continue;
                // Depth decides; the press direction only settles contacts within a whisker of each other.
                double score = contact.depth() + (toward == null ? 0.0 : 0.02 * -contact.normal().dot(toward));
                if (score > bestScore) {
                    bestScore = score;
                    best = contact;
                }
            }
        }
        return best;
    }

    /**
     * Records a wall found by a raycast (Valkyrien Skies raycasts through
     * ships natively, returning shipyard block positions) when the block
     * belongs to a ship, so the cling anchor can follow that ship.
     */
    public static void registerShipWallAt(Level level, Direction direction, BlockHitResult hit) {
        ShipRef ship = Ships.managing(level, hit.getBlockPos());
        Vec3 normal = ship == null ? null : ship.faceNormal(hit.getDirection());
        if (normal != null) {
            SHIP_WALLS.put(direction, new ShipContact(ship, hit.getBlockPos().immutable(), hit.getDirection(), normal, 0.0));
        }
    }

    /**
     * World-space normal of the face a raycast hit, when the block hit belongs
     * to a ship; null for a world block. The hit's direction is in shipyard
     * space, so it is turned by the ship's rotation.
     */
    public static Vec3 shipHitNormal(Level level, BlockHitResult hit) {
        return Ships.shipHitNormal(level, hit);
    }

    /**
     * World-space velocity (per tick) of the tracked ship at the player's
     * position: the clung ship, else the ship of any tracked wall, or zero
     * when no ship is involved.
     */
    public static Vec3 getShipPointVelocity(LocalPlayer pl) {
        ShipRef ship = trackedShip();
        if (ship == null) return Vec3.ZERO;
        return pointVelocityPerTick(ship, pl.position());
    }

    /**
     * True when any ship block's collision shape overlaps the world-space box.
     */
    public static boolean intersectsShipBlock(Level level, AABB box) {
        return Ships.touchesShipBlock(level, box);
    }

    /** One line per ship near the box, for the debug log: which ships, and what the probe touched. */
    public static String describeShips(Level level, AABB probe, Vec3 up) {
        StringBuilder out = new StringBuilder();
        for (ShipRef ship : Ships.intersecting(level, probe)) {
            if (out.length() > 0) out.append("; ");
            out.append("ship ").append(ship.id()).append(" [");
            boolean first = true;
            for (ShipContact contact : ship.contacts(probe)) {
                if (!first) out.append(", ");
                first = false;
                double tilt = contact.normal().dot(up);
                out.append(contact.pos().toShortString()).append(' ')
                        .append(Math.abs(tilt) > WALL_TILT_COS ? (tilt > 0 ? "floor" : "ceiling") : "wall")
                        .append(String.format(Locale.ROOT, " n=%.2f,%.2f,%.2f d=%.3f",
                                contact.normal().x, contact.normal().y, contact.normal().z, contact.depth()));
            }
            if (first) out.append("no block touched");
            out.append(']');
        }
        return out.length() == 0 ? "no ships intersect the probe" : out.toString();
    }

    /**
     * Remembers the cling position in shipyard space when the cling wall belongs
     * to a ship, so the anchor can follow the ship as it moves.
     */
    public static void captureClingAnchor(LocalPlayer pl, Direction clingDirection) {
        ShipContact wall = SHIP_WALLS.get(clingDirection);
        clingShip = null;
        clingAnchor = null;
        if (wall == null) return;
        Vec3 anchor = wall.ship().toShip(pl.position());
        if (anchor == null) return;
        clingShip = wall.ship();
        clingAnchor = anchor;
    }

    /**
     * The clung ship's heading in degrees, read off its transform so it matches
     * the rotation the player is actually being swung round by.
     */
    public static Double getClingShipYaw() {
        if (clingShip == null) return null;
        double heading = clingShip.headingDegrees();
        return Double.isNaN(heading) ? null : heading;
    }

    /**
     * Forgets the clung ship. A hold that made the player a rider (see
     * {@link #keepDragged}) is ended too; the registration itself is left to
     * lapse on its own, which is what hands the player the ship's motion.
     */
    public static void clearClingAnchor(LocalPlayer pl) {
        clingShip = null;
        clingAnchor = null;
        if (riding) {
            riding = false;
            ShipEntities.releaseRiding(pl);
        }
    }

    /**
     * Makes the player a rider of the clung ship for another while, the way
     * Valkyrien Skies treats anyone who has just stood on its deck: at the end
     * of every tick it moves them (and turns their yaw) with the ship, and it
     * gives them the ship's motion when the registration lapses, a second
     * after the last refresh. Called every tick of a hold on a ship. The
     * boarding impulse the registration would otherwise fire is cleared, and
     * the world-collision wipe of the registration is waived, as the hold
     * itself moves the player into and out of things; letting go
     * ({@link #clearClingAnchor}) disarms that waiver again.
     */
    public static void keepDragged(LocalPlayer pl) {
        if (clingShip == null) return;
        if (ShipEntities.keepRiding(pl, clingShip)) riding = true;
    }

    /** Whether Valkyrien Skies is currently carrying the player along with a ship as a rider. */
    public static boolean isDraggedByShip(LocalPlayer pl) {
        return ShipEntities.isCarried(pl);
    }

    /**
     * Current world-space position of the cling anchor, following the ship's
     * movement and rotation. Null when the player is not clinging to a ship
     * (or it can no longer be found).
     */
    public static Vec3 getClingWorldPos() {
        if (clingShip == null) return null;
        return clingShip.toWorld(clingAnchor);
    }

    /**
     * World-space velocity (per tick) of the clung ship at the anchor point, or
     * zero when not anchored. Includes the ship's spin, so the player can ride
     * the wall of a turning ship.
     */
    public static Vec3 getClingPointVelocity() {
        Vec3 world = getClingWorldPos();
        if (world == null) return Vec3.ZERO;
        return pointVelocityPerTick(clingShip, world);
    }

    /**
     * Player velocity relative to the ship whose wall is being used (per tick).
     * Falls back to plain world velocity when no ship wall is tracked.
     */
    public static Vec3 getRelativeVelocity(LocalPlayer pl) {
        ShipRef ship = trackedShip();
        Vec3 velocity = pl.getDeltaMovement();
        if (ship == null) return velocity;
        return velocity.subtract(pointVelocityPerTick(ship, pl.position()));
    }

    /** The clung ship, else the ship of any tracked wall, else null. */
    private static ShipRef trackedShip() {
        if (clingShip != null) return clingShip;
        for (ShipContact wall : SHIP_WALLS.values()) {
            return wall.ship();
        }
        return null;
    }

    /**
     * Velocity of the ship at a world-space point: linear plus rotational part.
     * Camafy reports metres (blocks) per second; the caller wants blocks per
     * tick. Zero when the ship can no longer be found.
     */
    private static Vec3 pointVelocityPerTick(ShipRef ship, Vec3 worldPoint) {
        Vec3 mps = ship.velocityAt(worldPoint);
        if (mps == null) return Vec3.ZERO;
        return new Vec3(Units.mpsToBpt(mps.x), Units.mpsToBpt(mps.y), Units.mpsToBpt(mps.z));
    }
}

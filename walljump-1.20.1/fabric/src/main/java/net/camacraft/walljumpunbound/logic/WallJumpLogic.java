package net.camacraft.walljumpunbound.logic;

import net.camacraft.walljumpunbound.WallJumpClient;
import net.camacraft.walljumpunbound.compat.GravityCompat;
import net.camacraft.walljumpunbound.compat.VSCompat;
import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.init.ModConfig.BlockListMode;
import net.camacraft.walljumpunbound.init.ModDamageTypes;
import net.camacraft.walljumpunbound.init.ModEnchantments;
import net.camacraft.walljumpunbound.init.ServerConfig;
import net.camacraft.walljumpunbound.network.message.MessageFallDistance;
import net.camacraft.walljumpunbound.network.message.MessageWallCling;
import net.camacraft.walljumpunbound.network.message.MessageWallJump;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joml.Quaternionf;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class WallJumpLogic {

    private static final Logger LOGGER = LoggerFactory.getLogger("walljumpunbound");

    // VS classes are only touched through VSCompat, and only when the mod is present.
    private static final boolean VS_LOADED = FabricLoader.getInstance().isModLoaded("valkyrienskies");

    /** How often a held cling is repeated to the server, in ticks. */
    private static final int CLING_SYNC_INTERVAL = 20;
    /** Height of the gripping hand above the feet, in blocks. */
    private static final double HAND_HEIGHT = 1.9;
    /** How far the gripping hand sits to its own side of the body. */
    private static final double HAND_REACH = 0.3;
    /**
     * Shoulder height above the feet, and the distance from that shoulder to the
     * hand: the reach envelope. The arm cube is 12px tall but hangs from y=-2 to
     * y=10 about its pivot, so only 10px of it is below the shoulder — using the
     * cube's full length here left the hands short of a ledge overhead.
     */
    public static final double SHOULDER_HEIGHT = 1.375;
    public static final double ARM_LENGTH = 0.625;
    /** A ledge is grabbable while it sits within one arm of the shoulder. */
    public static final double LEDGE_RISE_MIN = SHOULDER_HEIGHT - ARM_LENGTH;
    public static final double LEDGE_RISE_MAX = SHOULDER_HEIGHT + ARM_LENGTH;
    /** How far past the wall face the ledge probe sits, so it is inside the wall. */
    private static final double LEDGE_PROBE_OUT = 0.15;
    /**
     * The same for a ship hull, which is rarely flat against the player: a hull
     * that is tilted leans away from the feet higher up, and one turned off
     * the world axes meets the box at a corner, so the probe reaches further.
     */
    private static final double LEDGE_PROBE_OUT_SHIP = 0.35;
    /** Side of the cube used to ask whether the wall is still there at a height. */
    private static final double LEDGE_PROBE_SIZE = 0.15;
    /** Bisection steps; six over the reach envelope lands inside a quarter pixel. */
    private static final int LEDGE_PROBE_STEPS = 6;
    /** How far the player may face from a held ledge before letting go, in degrees. */
    private static final float LEDGE_RELEASE_YAW = 90.0F;
    /**
     * How much further away a player who took hold already facing past that
     * may still turn before letting go: room for the hands to give, and for a
     * wobble of the mouse, without the grip being read as given up.
     */
    private static final float LEDGE_RELEASE_SLACK = 15.0F;
    /** A face lying within 60 degrees of level can be stood on; steeper is a wall. */
    private static final double FLOOR_COS = 0.5;
    /** How far below the feet ground has to be for a cling to be worth starting. */
    private static final double GROUND_CLEARANCE = 0.8;
    /** Blocks of slide between scrapes of the wall's own sound. */
    private static final double SLIDE_SCUFF_STEP = 0.35;

    public static int ticksWallClinged;
    public static int ticksWallSlid;
    public static boolean stopSlid = false;
    public static int wallJumpCount;
    private static int ticksKeyDown;
    private static int lastHurtTime;
    private static Vec3 lastPos;
    private static double clingFall;
    private static Vec3 lastShipHold;
    private static Vec3 heldPos;
    private static Vec3 shipCarry = Vec3.ZERO;
    private static Vec3 drift = Vec3.ZERO;
    private static boolean clingSent;
    private static Direction clingWallSent;
    private static float clingYawSent;
    private static boolean clingShipSent;
    private static boolean clingLedgeSent;
    private static int ticksSinceClingSent;
    private static double clingX, clingZ;
    private static double lastJumpHeight = Double.MAX_VALUE;
    private static double slideScuff;
    private static EnumSet<Direction> walls = EnumSet.noneOf(Direction.class);
    private static EnumSet<Direction> staleWalls = EnumSet.noneOf(Direction.class);

    // The wall being held, kept as long as it is still there so an inside
    // corner cannot swap the grip from one wall to the other mid-cling, with
    // the way to it: its true bearing (a ship hull is rarely on a cardinal),
    // its outward normal in world space, and whether it is a ship.
    private static Direction clingDirection;
    private static float clingWallYaw;
    private static Vec3 clingWallNormal;
    private static boolean clingOnShip;
    // whether the hold is a ledge grab; the server charges hunger for those
    private static boolean clingAtLedge;
    // the closest the player has faced the held wall during this hold, in
    // degrees off its bearing: the ledge release measures turning AWAY from it
    private static float clingFacingOff;

    // GRAVITY UNBOUND: while the player's gravity frame is rotated, every
    // direction below is expressed in that frame — "horizontal" is the
    // frame's tangent plane, "up" its up axis, and the player's velocity
    // and yaw are frame-local already. Null under plain gravity.
    private static Quaternionf gravityFrame = null;
    private static Vec3 gravityUp = new Vec3(0.0, 1.0, 0.0);
    // the point the cling holds (frame mode pins the tangential position to it)
    private static Vec3 clingAnchor = null;
    // the wall block each probed direction found (frame mode)
    private static final Map<Direction, BlockPos> FRAME_WALLS = new EnumMap<>(Direction.class);

    /** Whether the player's box would be inside a world block at this position. */
    private static boolean blockedByWorld(LocalPlayer pl, Vec3 at) {
        // Narrowed off the sides so the wall being clung to is not itself the obstruction.
        AABB box = pl.getBoundingBox().move(at.subtract(pl.position())).deflate(0.05, 0.0, 0.05);
        return collidesWithBlock(pl.level(), box);
    }

    private static String fmt(Vec3 v) {
        return String.format("%.3f,%.3f,%.3f", v.x, v.y, v.z);
    }

    private static String fmt(double d) {
        return String.format("%.3f", d);
    }

    private static boolean collidesWithBlock(Level level, AABB box) {
        return !level.noCollision(box);
    }

    // The compat can be switched off in the config even when VS is installed.
    private static boolean vsEnabled() {
        return VS_LOADED && ModConfig.enableVSCompat;
    }

    /** How far past the player's box a ship hull is felt for. */
    private static double shipReach() {
        return ModConfig.shipWallDetectionRange + (ticksWallClinged > 0 ? 0.04 : 0.0);
    }

    /**
     * The box a ship hull is felt for with: the body, widened by that reach. A
     * rotated hull stops the player at a CORNER of their body, out of reach of
     * any thin probe down the middle, so ships are always asked with the whole
     * body. Under a gravity frame the body's own plane is not world X/Z, so it
     * is taken as the axis from the feet to the head grown on every side
     * instead; the faces that box touches are then sorted into walls and
     * floors against the frame's up, which is what keeps the deck underfoot
     * from reading as a wall.
     */
    private static AABB shipProbe(LocalPlayer pl) {
        double reach = shipReach();
        if (gravityFrame == null) return pl.getBoundingBox().inflate(reach, 0.0, reach);

        Vec3 feet = pl.position();
        return new AABB(feet, feet.add(gravityUp.scale(pl.getBbHeight()))).inflate(pl.getBbWidth() / 2 + reach);
    }

    /** The player's height along its frame's up (plain Y under vanilla gravity). */
    private static double height(LocalPlayer pl) {
        return gravityFrame != null ? pl.position().dot(gravityUp) : pl.getY();
    }

    public static void doWallJump(LocalPlayer pl) {
        if (!canWallJump(pl))
            return;

        gravityFrame = GravityCompat.isActive(pl) ? GravityCompat.frame(pl) : null;
        gravityUp = gravityFrame != null ? GravityCompat.up(gravityFrame) : new Vec3(0.0, 1.0, 0.0);

        // Both tracked before any early return below. A hit knocks the player off
        // the wall, and the rise of hurtTime is the only sign of it the client
        // gets — bar the mod's own charge for catching the wall, which arrives
        // from the server a few ticks into the cling and is its price, not a
        // blow; the ground the player covered this tick sizes the ship probe.
        boolean justHurt = pl.hurtTime > lastHurtTime && !hurtByCatchingWall(pl);
        lastHurtTime = pl.hurtTime;
        drift = lastPos == null ? Vec3.ZERO : pl.position().subtract(lastPos);
        lastPos = pl.position();

        if (onGround(pl) || pl.getAbilities().flying || !pl.level().getFluidState(pl.blockPosition()).isEmpty() || pl.isHandsBusy()) {
            ticksWallClinged = 0;
            ticksWallSlid = 0;
            stopSlid = false;
            clingX = Double.NaN;
            clingZ = Double.NaN;
            clingAnchor = null;
            clingFall = 0.0;
            endShipHold(pl);
            lastJumpHeight = Double.MAX_VALUE;
            slideScuff = 0.0;
            clingDirection = null;
            clingAtLedge = false;
            staleWalls.clear();
            wallJumpCount = 0;
            FRAME_WALLS.clear();
            if (VS_LOADED) VSCompat.clearShipWalls();

            return;
        }

        // In a wall-jump sequence — clinging, or airborne between wall jumps —
        // the planet-walk surface the sequence started from stays snapped
        // (see GravityCompat.sustainHeldSurface); the sequence ends on the
        // ground, where the reset above runs.
        if (gravityFrame != null && (ticksWallClinged > 0 || wallJumpCount > 0)) {
            GravityCompat.sustainHeldSurface(pl);
        }

        if (stopSlid) return;

        updateWalls(pl);
        trackClingWall(pl);
        ticksKeyDown = WallJumpClient.KEY_WALL_JUMP.isDown() ? ticksKeyDown + 1 : 0;

        if (ticksWallClinged < 1) {
            boolean pressed = ticksKeyDown > 0 && ticksKeyDown < 4;
            String refusal = !pressed ? null : walls.isEmpty() ? "no wall in reach" : clingRefusal(pl);
            if (pressed && refusal == null) {
                // A rotated ship's wall normal is rarely axis-aligned, so snapping
                // the camera to a cardinal direction looks wrong there.
                if (ModConfig.autoRotation && !clingOnShip) {
                    pl.setYRot(clingDirection.getOpposite().toYRot());
                    pl.yRotO = pl.getYRot();
                }

                ticksWallClinged = 1;
                // A grip arrests the player, so a ship cling starts held rather
                // than shedding an arrival speed. Seeding this from a measurement
                // meant any error in the ship's reported motion was paid off as a
                // slide, which on a falling ship ran the whole wall.
                clingFall = 0.0;
                lastShipHold = null;
                slideScuff = 0.0;
                clingFacingOff = facingOff(pl);
                clingX = pl.getX();
                clingZ = pl.getZ();
                clingAnchor = pl.position();
                if (vsEnabled()) VSCompat.captureClingAnchor(pl, clingDirection);
                // Where the hold starts, and where the ship had it at that moment:
                // from here the hold is carried by the ship's steps, never moved to
                // wherever the anchor claims to be.
                heldPos = pl.position();
                lastShipHold = vsEnabled() ? VSCompat.getClingWorldPos() : null;
                if (lastShipHold != null) {
                    // The server prices the catch from its own count of the fall,
                    // and on a ship that count is the ship's descent as much as
                    // the player's: a rider is carried down with it. The client's
                    // count leaves the carry out, so the server is handed that one
                    // first (the cling itself is reported right after this).
                    sendFallDistance(pl.fallDistance);
                    // and the hold is a ride from its first tick, see the hold below
                    VSCompat.keepDragged(pl);
                }

                playHitSound(pl, getWallPos(pl));
                spawnWallParticle(pl, getWallPos(pl));
            } else if (refusal != null && ticksKeyDown == 1 && ModConfig.debugShipCling) {
                LOGGER.info("[cling] refused: {} (walls={}, frame={}, ships: {})", refusal, walls,
                        gravityFrame == null ? "none" : "up " + fmt(gravityUp),
                        vsEnabled() ? VSCompat.describeShips(pl.level(), shipProbe(pl), gravityUp) : "compat off");
            }

            return;
        }

        // Worked out once here: the release below needs it, and so does the slide.
        boolean atLedge = ServerConfig.ledgeGrab && !walls.isEmpty()
                && !Double.isNaN(ledgeRise(pl, clingWallYaw, clingOnShip));
        clingAtLedge = atLedge;

        // Turning towards the wall is always allowed, so the closest the player
        // has faced it is what the ledge release measures turning away from.
        // (The bearing itself turns with a ship, as Valkyrien Skies turns a
        // rider's yaw, so a ship swinging round under a grip changes nothing.)
        clingFacingOff = Math.min(clingFacingOff, facingOff(pl));

        boolean grounded = onGround(pl);
        if (!WallJumpClient.KEY_WALL_JUMP.isDown() || grounded || !pl.level().getFluidState(pl.blockPosition()).isEmpty() || walls.isEmpty() || pl.getFoodData().getFoodLevel() < 1
                || justHurt || (atLedge && turnedFromCling(pl))) {
            if (ModConfig.debugShipCling && ticksWallClinged > 0) {
                LOGGER.info("[cling] release: key={} ground={} fluid={} noWalls={} food={} hurt={} turned={}",
                        !WallJumpClient.KEY_WALL_JUMP.isDown(), grounded,
                        !pl.level().getFluidState(pl.blockPosition()).isEmpty(), walls.isEmpty(),
                        pl.getFoodData().getFoodLevel() < 1, justHurt, atLedge && turnedFromCling(pl));
            }
            ticksWallClinged = 0;
            endShipHold(pl);

            if ((pl.input.forwardImpulse != 0 || pl.input.leftImpulse != 0) && !grounded && !walls.isEmpty()) {
                if (wallJumpCount >= ServerConfig.maxWallJumps) return;
                pl.resetFallDistance();
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                buffer.writeBoolean(true);
                ClientPlayNetworking.send(MessageWallJump.ID, buffer);

                wallJump(pl, (float) ServerConfig.wallJumpHeight);
                staleWalls = EnumSet.copyOf(walls);
            }

            return;
        }

        Vec3 anchor = clingAnchor;
        Vec3 shipAnchor = null;
        if (vsEnabled() && ModConfig.stickToMovingShips) {
            shipAnchor = VSCompat.getClingWorldPos();
            if (shipAnchor != null) {
                clingX = shipAnchor.x;
                clingZ = shipAnchor.z;
                anchor = shipAnchor;
            }
        }
        // How far the ship carried the hold since last tick, taken from where its
        // transform actually put that hold. The ship's own reported velocity is
        // deliberately not used: it comes from separate physics fields, and on a
        // moving or turning ship any disagreement between them and the transform
        // was charged to the player as a fall they then had to slide off.
        Vec3 shipStep = shipAnchor != null && lastShipHold != null
                ? shipAnchor.subtract(lastShipHold) : Vec3.ZERO;
        lastShipHold = shipAnchor;
        // A ship cling is held by position at the end instead, all three axes of
        // it, so nothing is pinned here.
        if (shipAnchor == null) {
            if (gravityFrame != null && anchor != null) {
                // hold the position in the frame's tangent plane; only motion
                // along the frame's up is free (the slide below)
                Vec3 pos = pl.position();
                double along = pos.subtract(anchor).dot(gravityUp);
                Vec3 pinned = anchor.add(gravityUp.scale(along));
                pl.setPos(pinned.x, pinned.y, pinned.z);
            } else {
                pl.setPos(clingX, pl.getY(), clingZ);
            }
        }
        // Slide states are judged relative to the ship so its motion neither
        // triggers nor cancels them.
        double motionY;
        if (shipAnchor != null) {
            // Carried forward as state, not measured. The hold below is a
            // position, so measuring the player here only reads back the motion
            // that hold imposed: the slide feeds itself and runs away, which on a
            // fast ship is a cling that grabs for a frame and drops like a stone.
            motionY = clingFall;
        } else {
            motionY = pl.getDeltaMovement().y;
        }
        if (motionY > 0.0) {
            motionY = 0.0;
        } else if (motionY < -0.6) {
            motionY = motionY + 0.2;
            spawnWallParticle(pl, getWallPos(pl));
        } else if (atLedge) {
            // Hands over the top of the wall: a grip on the ledge does not slip,
            // so neither the slide nor the give-up timer below ever starts.
            motionY = 0.0;
        } else if (ticksWallClinged++ > ServerConfig.wallSlideDelay) {
            if (ticksWallSlid++ > ServerConfig.stopWallSlideDelay) stopSlid = true;
            motionY = -0.1;
            spawnWallParticle(pl, getWallPos(pl));
        } else {
            motionY = 0.0;
        }
        if (motionY < 0.0) scuffWall(pl, -motionY);

        if (pl.fallDistance > 2) {
            pl.resetFallDistance();
            sendFallDistance((float) (motionY * motionY * 8));
        }

        if (ModConfig.debugShipCling && shipAnchor != null && pl.tickCount % 4 == 0) {
            Vec3 p = pl.position();
            LOGGER.info("[cling] pos={} anchorMinusPos={} shipStep={} shipVel={} fall={} motionY={} slid={}",
                    fmt(p), fmt(shipAnchor.subtract(p)), fmt(shipStep),
                    fmt(VSCompat.getShipPointVelocity(pl)), fmt(clingFall), fmt(motionY), ticksWallSlid);
        }

        if (shipAnchor != null) {
            clingFall = motionY;
            // Dead reckoned: carry the hold by how far the ship moved it, rather
            // than teleporting to where the anchor says it is. A transform that
            // disagrees with the player by some offset cancels out of a
            // difference but yanks in an absolute, which is the pull down the
            // wall and off to one side. Starting from the held position rather
            // than the player's also drops the gravity they picked up this tick,
            // so nothing accumulates.
            Vec3 base = heldPos != null ? heldPos : pl.position();
            // Then eased back out of the hull wherever this tick's contact found
            // the box inside it. A hold by position skips the collision that
            // would keep the two apart, and a hull that is tilted, or rolling
            // under the grip, is never flat against a world-aligned box.
            Vec3 carried = base.add(shipStep).add(hullPushOut(pl));
            Vec3 hold = carried.add(gravityUp.scale(motionY));
            if (motionY < 0.0) {
                // A wall slide ends on the ground through onGround, which a
                // position hold never sets: it moves through nothing. So the
                // slide looks for itself, and steps off onto whatever it reaches.
                double drop = -motionY + 0.02;
                double floor = floorBelow(pl, carried, drop);
                if (floor <= drop) {
                    landCling(pl, carried.subtract(gravityUp.scale(Math.max(0.0, floor - 0.001))));
                    return;
                }
                if (blockedByWorld(pl, hold) && !blockedByWorld(pl, carried)) {
                    landCling(pl, carried);
                    return;
                }
            }
            heldPos = hold;
            pl.setPos(hold.x, hold.y, hold.z);
            // Kept for the way out, should nothing else carry the player then.
            // Frame-local under a gravity frame, as the player's velocity is.
            shipCarry = gravityFrame != null ? GravityCompat.toLocal(shipStep, gravityFrame) : shipStep;
            // Ridden, not merely held. Valkyrien Skies moves a rider with the
            // ship at the end of every tick, so between one hold and the next
            // the hull stays exactly where the player left it. A hold alone was
            // a step behind: the ship advanced after each hold, and the player's
            // own movement next tick met a hull that had moved into them, whose
            // collision reported them as landed (which dropped the cling and
            // left them hanging at rest while the ship fell away) or shoved
            // them off the wall — and on a spinning ship the wall itself drew
            // away between holds. The rider registration lapses on its own a
            // second after it is last refreshed, as it does for anyone who
            // steps off a deck, and hands the ship's motion over when it does.
            VSCompat.keepDragged(pl);
            if (stopSlid) {
                // The grip has given out: the slide carries on as a fall,
                // relative to the ship like everything else here.
                pl.setDeltaMovement(0.0, motionY, 0.0);
                endShipHold(pl);
            } else {
                // Left at rest rather than carrying the ship's speed. Commanding
                // it meant move() drove the player that far through the ship's
                // own geometry before this code ran next tick. The position hold
                // above and the ride carry them.
                pl.setDeltaMovement(Vec3.ZERO);
            }
        } else {
            pl.setDeltaMovement(0.0, motionY, 0.0);
        }
    }

    /**
     * Ends a ship cling on whatever the slide has reached, leaving the player
     * standing there with the ship's motion so a moving deck carries them on.
     */
    private static void landCling(LocalPlayer pl, Vec3 at) {
        if (ModConfig.debugShipCling) LOGGER.info("[cling] slid onto ground at {}", fmt(at));
        ticksWallClinged = 0;
        ticksWallSlid = 0;
        pl.setPos(at.x, at.y, at.z);
        pl.setDeltaMovement(Vec3.ZERO);
        endShipHold(pl);
        pl.setOnGround(true);
    }

    /**
     * Ends the hold on a ship, wherever the cling ends: let go, slid onto the
     * deck, grounded, or the grip giving out. The hold carried the player by
     * position, so the ship's motion is theirs to keep on the way out —
     * unless Valkyrien Skies is still carrying them as a rider, which it goes
     * on doing for a while after anyone leaves a ship and then hands them the
     * ship's motion itself; giving it here as well would double it.
     */
    private static void endShipHold(LocalPlayer pl) {
        if (VS_LOADED) VSCompat.clearClingAnchor();
        heldPos = null;
        lastShipHold = null;
        if (shipCarry.lengthSqr() > 0.0) {
            if (!(VS_LOADED && VSCompat.isDraggedByShip(pl))) pl.setDeltaMovement(pl.getDeltaMovement().add(shipCarry));
            shipCarry = Vec3.ZERO;
        }
    }

    /**
     * Whether the player is standing on something. During a hold on a ship the
     * vanilla flag is not taken at its word: the hold moves the player by
     * position, and the collision Valkyrien Skies runs on a body that a
     * turning hull has moved into resolves along whichever axis it likes,
     * which read as landing in mid-air. There it has to be borne out by a
     * floor right under the feet.
     */
    private static boolean onGround(LocalPlayer pl) {
        if (!pl.onGround()) return false;
        if (lastShipHold == null) return true;
        return floorBelow(pl, pl.position(), 0.1) != Double.POSITIVE_INFINITY;
    }

    /** How far the player faces from straight at the held wall, in degrees. */
    private static float facingOff(LocalPlayer pl) {
        return Math.abs(Mth.wrapDegrees(pl.getYRot() - clingWallYaw));
    }

    /**
     * Whether the latest hit was the mod's own charge for catching a wall after
     * a long fall: the price of the cling, not a blow that knocks the player
     * off it.
     */
    private static boolean hurtByCatchingWall(LocalPlayer pl) {
        DamageSource source = pl.getLastDamageSource();
        return source != null && source.is(ModDamageTypes.WALL_CLING);
    }

    private static void sendFallDistance(float fallDistance) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeFloat(fallDistance);
        ClientPlayNetworking.send(MessageFallDistance.ID, buffer);
    }

    /**
     * How far this tick's contact found the box inside the held hull, as a
     * shove back out along the hull's face. The probe is the box widened by
     * the detection range, so that much of the contact's depth is expected;
     * anything beyond it is the box in the hull.
     */
    private static Vec3 hullPushOut(LocalPlayer pl) {
        if (!clingOnShip || clingDirection == null || clingWallNormal == null) return Vec3.ZERO;
        Vec3 normal = clingWallNormal;
        double reach = shipReach();
        double expected;
        if (gravityFrame != null) {
            // The frame's probe is a box around the body's axis grown on EVERY
            // side, so besides the detection range its own corner reach along
            // the face is expected depth too.
            double radius = pl.getBbWidth() / 2;
            expected = (radius + reach) * (Math.abs(normal.x) + Math.abs(normal.y) + Math.abs(normal.z)) - radius;
        } else {
            expected = reach * (Math.abs(normal.x) + Math.abs(normal.z));
        }
        double inside = VSCompat.getShipWallDepth(clingDirection) - expected;
        return inside > 0.005 ? normal.scale(inside) : Vec3.ZERO;
    }

    /** True while the player is held to a wall; a forced slide stop is not a cling. */
    public static boolean isClinging() {
        return ticksWallClinged > 0 && !stopSlid;
    }

    /**
     * Publishes the cling for the pose. The local player is posed straight from
     * this flag; the server hears about it whenever it flips, so that everyone
     * tracking the player sees the pose too. The repeat covers a player who
     * only comes into view partway through someone else's cling, and a ship
     * turning under a held grip moves the bearing without flipping anything.
     */
    public static void updateClingPose(LocalPlayer pl) {
        boolean clinging = isClinging();
        Direction wall = clinging && !walls.isEmpty() ? getClingDirection() : null;
        float yaw = wall == null ? 0.0F : clingWallYaw;
        boolean ship = wall != null && clingOnShip;
        boolean ledge = clinging && clingAtLedge;
        if (pl instanceof WallClingHolder holder) holder.walljumpunbound$setWallCling(clinging, wall, yaw, ship);
        if (pl instanceof WallClingPosture posture) posture.walljumpunbound$setWallClingPosture(clinging, ledge);

        boolean turned = clinging && Math.abs(Mth.wrapDegrees(yaw - clingYawSent)) > 2.0F;
        if (clinging != clingSent || wall != clingWallSent || ship != clingShipSent || ledge != clingLedgeSent
                || turned || (clinging && ++ticksSinceClingSent >= CLING_SYNC_INTERVAL)) {
            clingSent = clinging;
            clingWallSent = wall;
            clingYawSent = yaw;
            clingShipSent = ship;
            clingLedgeSent = ledge;
            ticksSinceClingSent = 0;
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            buffer.writeBoolean(clinging);
            MessageWallCling.writeWall(buffer, wall);
            buffer.writeFloat(yaw);
            buffer.writeBoolean(ship);
            buffer.writeBoolean(ledge);
            ClientPlayNetworking.send(MessageWallCling.ID, buffer);
        }
    }

    /**
     * True once the player has turned too far from the held wall. Hands on a
     * ledge cannot follow the body round a full turn, so a player facing more
     * than a right angle away from the wall has let go rather than swivelled.
     * A player who took hold already facing further away than that keeps
     * whatever they had, and a little slack: they may face about as far from
     * the wall as they have at any point since they grabbed it, so turning
     * towards a ledge never lets go of it — only turning away does.
     */
    private static boolean turnedFromCling(LocalPlayer pl) {
        return facingOff(pl) > Math.max(LEDGE_RELEASE_YAW, clingFacingOff + LEDGE_RELEASE_SLACK);
    }

    private static boolean canWallJump(LocalPlayer pl) {
        if (ServerConfig.wallJumpEnabled) return true;
        if (!ServerConfig.enableEnchantments || !ServerConfig.wallJumpEnchantment)
            return false;
        ItemStack stack = pl.getItemBySlot(EquipmentSlot.FEET);
        if (!stack.isEmpty()) {
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            return enchantments.containsKey(ModEnchantments.WALL_JUMP);
        }

        return false;
    }

    /**
     * Why a cling cannot start right now, or null when it can. A wall is
     * assumed to be in reach; only the player's own state is judged here.
     */
    private static String clingRefusal(LocalPlayer pl) {
        if (pl.onClimbable()) return "on a ladder";
        if (pl.getFoodData().getFoodLevel() < 1) return "too hungry";
        Vec3 velocity = clingVelocity(pl);
        if (velocity.y > 0.1) return "still rising at " + fmt(velocity.y);
        if (gravityFrame != null) {
            // ground within reach below the feet, down the FRAME (Valkyrien
            // Skies raycasts through ships natively, so this covers ships)
            if (floorBelow(pl, pl.position(), GROUND_CLEARANCE * bodyScale(pl)) != Double.POSITIVE_INFINITY) return "ground within reach below";
        } else {
            AABB below = pl.getBoundingBox().move(0, -GROUND_CLEARANCE, 0);
            if (collidesWithBlock(pl.level(), below)) return "ground within reach below";
            // Ships by rays and the face they hit, not by a box: a hull that
            // leans out beneath the feet is a wall to slide down, not a deck.
            if (vsEnabled() && floorBelow(pl, pl.position(), GROUND_CLEARANCE) != Double.POSITIVE_INFINITY) return "ship deck within reach below";
        }
        if (!ServerConfig.onFallWallCling && velocity.y < -0.8) return "falling too fast";
        if (ServerConfig.allowReClinging || height(pl) < lastJumpHeight - 1) return null;
        return staleWalls.containsAll(walls) ? "the wall just jumped from" : null;
    }

    private static double bodyScale(LocalPlayer pl) {
        return Math.max(0.05, Math.min(1.0, pl.getBbHeight() / 1.8));
    }

    /**
     * How far above the feet the wall being clung to ends, or NaN when it has no
     * top within arm's reach — either it carries on past the hands, or there is
     * nothing up there to hold. The wall is given by its bearing, the yaw a
     * player square to it would have, so a ship hull is probed straight into
     * its face wherever that points.
     *
     * This asks "is the wall still solid at this height" the same way the rest of
     * the mod does — a small box tested against the world and, separately, against
     * ship collision shapes — because that is what sees a Valkyrien Skies hull at
     * any rotation. The top is then bisected between a solid height and a clear
     * one, which needs the wall to actually stop inside the envelope: a wall that
     * is still solid at full reach has no grabbable top and returns NaN.
     */
    public static double ledgeRise(LivingEntity entity, float wallYaw, boolean ship) {
        Quaternionf frame = GravityCompat.isActive(entity) ? GravityCompat.frame(entity) : null;
        Vec3 up = frame != null ? GravityCompat.up(frame) : new Vec3(0.0, 1.0, 0.0);
        float rad = wallYaw * Mth.DEG_TO_RAD;
        Vec3 into = new Vec3(-Mth.sin(rad), 0.0, Mth.cos(rad));
        if (frame != null) into = GravityCompat.toWorld(into, frame);
        double out = ship ? LEDGE_PROBE_OUT_SHIP : LEDGE_PROBE_OUT;

        if (wallAt(entity, into, up, LEDGE_RISE_MAX, out)) return Double.NaN;
        if (!wallAt(entity, into, up, LEDGE_RISE_MIN, out)) return Double.NaN;

        double solid = LEDGE_RISE_MIN, clear = LEDGE_RISE_MAX;
        for (int i = 0; i < LEDGE_PROBE_STEPS; i++) {
            double mid = (solid + clear) / 2;
            if (wallAt(entity, into, up, mid, out)) solid = mid;
            else clear = mid;
        }
        // The probe clears the wall once its underside passes the top, so the top
        // itself is half a probe lower than the lowest clear height found.
        return clear - LEDGE_PROBE_SIZE / 2;
    }

    /**
     * Whether the clung wall is still solid this far above the feet. The probe is
     * a thin slice of the player's own footprint reaching towards the wall, which
     * is how updateWalls finds a ship wall: a hull turned off the world axes meets
     * the player at a corner of their box, well off a probe aimed down the middle.
     */
    private static boolean wallAt(LivingEntity entity, Vec3 into, Vec3 up, double rise, double out) {
        Vec3 at = entity.position().add(up.scale(rise));
        double half = entity.getBbWidth() / 2, thick = LEDGE_PROBE_SIZE / 2;
        AABB slice = new AABB(at.x - half, at.y - thick, at.z - half, at.x + half, at.y + thick, at.z + half)
                .expandTowards(into.x * out, into.y * out, into.z * out);
        if (collidesWithBlock(entity.level(), slice)) return true;
        return vsEnabled() && VSCompat.intersectsShipBlock(entity.level(), slice);
    }

    /**
     * Distance down to the nearest thing beneath the feet that could be stood
     * on, within the depth given; infinity when there is none. Rays are cast
     * down the frame's up from the middle and corners of the footprint, and
     * only a face lying within 60 degrees of level counts. The rays see
     * Valkyrien Skies ships, and reading the face they hit is what tells a
     * deck from a hull leaning out beneath the feet: a wall, not a floor.
     */
    private static double floorBelow(LocalPlayer pl, Vec3 at, double depth) {
        double half = pl.getBbWidth() / 2 - 0.05;
        double[][] spots = {{0.0, 0.0}, {half, half}, {half, -half}, {-half, half}, {-half, -half}};
        double nearest = Double.POSITIVE_INFINITY;
        for (double[] spot : spots) {
            Vec3 offset = new Vec3(spot[0], 0.0, spot[1]);
            if (gravityFrame != null) offset = GravityCompat.toWorld(offset, gravityFrame);
            Vec3 from = at.add(offset).add(gravityUp.scale(0.05));
            BlockHitResult hit = pl.level().clip(new ClipContext(
                    from, from.subtract(gravityUp.scale(0.05 + depth)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pl));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            if (hitNormal(pl.level(), hit).dot(gravityUp) < FLOOR_COS) continue;
            double distance = from.subtract(hit.getLocation()).dot(gravityUp) - 0.05;
            nearest = Math.min(nearest, Math.max(0.0, distance));
        }
        return nearest;
    }

    /** Outward normal of the face a raycast hit, in world space: turned by the ship's rotation for a ship block. */
    private static Vec3 hitNormal(Level level, BlockHitResult hit) {
        if (VS_LOADED) {
            Vec3 shipNormal = VSCompat.shipHitNormal(level, hit);
            if (shipNormal != null) return shipNormal;
        }
        return Vec3.atLowerCornerOf(hit.getDirection().getNormal());
    }

    /**
     * Velocity used for cling checks: relative to the ship when the candidate
     * wall belongs to one, so cling behaves the same on a moving ship.
     */
    private static Vec3 clingVelocity(LocalPlayer pl) {
        if (vsEnabled() && hasShipWall()) {
            Vec3 shipVelocity = VSCompat.getShipPointVelocity(pl);
            // A ship holding still is left exactly as it was.
            if (shipVelocity.lengthSqr() > 1.0E-6) {
                // Measured rather than assumed: the ground the player actually
                // covered this tick, less the ground the ship covered under them.
                // Subtracting the ship out of getDeltaMovement only holds if the
                // ship's motion was ever put in there, and a rider carried by
                // position rather than by velocity reads as flying upwards at the
                // ship's falling speed — which refused every cling on a ship on
                // its way down. A displacement is the same either way.
                Vec3 relative = drift.subtract(shipVelocity);
                return gravityFrame != null ? GravityCompat.toLocal(relative, gravityFrame) : relative;
            }
        }
        return pl.getDeltaMovement();
    }

    private static boolean hasShipWall() {
        for (Direction direction : walls) {
            if (VSCompat.isShipWall(direction)) return true;
        }
        return false;
    }

    private static void updateWalls(LocalPlayer pl) {
        if (gravityFrame != null) {
            updateWallsInFrame(pl);
            return;
        }

        Vec3 pos = pl.position();
        AABB box = new AABB(pos.x - 0.001, pos.y, pos.z - 0.001, pos.x + 0.001, pos.y + pl.getEyeHeight(), pos.z + 0.001);

        double dist = (pl.getBbWidth() / 2) + (ticksWallClinged > 0 ? 0.1 : 0.06);
        AABB[] axes = {box.expandTowards(0, 0, dist), box.expandTowards(-dist, 0, 0), box.expandTowards(0, 0, -dist), box.expandTowards(dist, 0, 0)};

        walls = EnumSet.noneOf(Direction.class);
        FRAME_WALLS.clear();
        if (VS_LOADED) VSCompat.clearShipWalls();

        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.from2DDataValue(i);
            if (collidesWithBlock(pl.level(), axes[i])
                    && wallAllowed(pl, pl.blockPosition().relative(direction), pl.blockPosition().above().relative(direction))) {
                walls.add(direction);
                pl.horizontalCollision = true;
            }
        }

        // A rotated ship stops the player at a corner of their bounding box, out
        // of reach of the thin centred probes above, so ships are probed with
        // the whole box widened all round: it already touches the hull at any
        // rotation. Which way the wall lies is then read off the face touched,
        // not off which probe found it, so a hull turned off the world axes or
        // leaning over does not answer to every probe at once.
        if (vsEnabled()) {
            VSCompat.ShipContact contact = VSCompat.findShipWall(pl.level(), shipProbe(pl), gravityUp, pressDirection(pl));
            if (contact != null && contact.normal().horizontalDistanceSqr() > 1.0E-6
                    && wallAllowed(pl, contact.blockPos(), contact.blockPos().above())) {
                Vec3 toward = contact.normal().scale(-1.0);
                Direction direction = Direction.getNearest(toward.x, 0.0, toward.z);
                VSCompat.recordShipWall(direction, contact);
                walls.add(direction);
                pl.horizontalCollision = true;
            }
        }
    }

    /**
     * Wall detection under a rotated gravity frame. The world-axis box
     * probes cannot express the frame's tangent plane (and, standing on a
     * plated wall, found the very floor under the player as a "wall" beside
     * it — the cling-to-air). Cast short rays from the body's axis along
     * each frame-horizontal direction instead, at three heights along the
     * frame's up; Valkyrien Skies raycasts ships natively, so a ship wall
     * is found the same way and registered for the moving-ship anchor.
     */
    private static void updateWallsInFrame(LocalPlayer pl) {
        walls = EnumSet.noneOf(Direction.class);
        FRAME_WALLS.clear();
        if (VS_LOADED) VSCompat.clearShipWalls();

        double reach = (pl.getBbWidth() / 2) + (ticksWallClinged > 0 ? 0.1 : 0.06) * bodyScale(pl);
        double h = pl.getBbHeight();
        double[] heights = {0.15 * h, 0.5 * h, 0.85 * h};
        Direction upDir = Direction.getNearest(gravityUp.x, gravityUp.y, gravityUp.z);

        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.from2DDataValue(i);
            Vec3 dirWorld = GravityCompat.toWorld(Vec3.atLowerCornerOf(direction.getNormal()), gravityFrame).normalize();

            BlockHitResult wallHit = null;
            for (double hh : heights) {
                Vec3 from = pl.position().add(gravityUp.scale(hh));
                BlockHitResult hit = pl.level().clip(new ClipContext(
                        from, from.add(dirWorld.scale(reach)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pl));
                // A face lying flat to the frame is floor or ceiling, not a wall
                // to grip: the deck a tilted frame's sloping rays reach down to.
                if (hit.getType() == HitResult.Type.BLOCK
                        && Math.abs(hitNormal(pl.level(), hit).dot(gravityUp)) < FLOOR_COS) {
                    wallHit = hit;
                    break;
                }
            }
            if (wallHit == null) continue;

            BlockPos wallPos = wallHit.getBlockPos().immutable();
            if (!wallAllowed(pl, wallPos, wallPos.relative(upDir))) continue;

            walls.add(direction);
            FRAME_WALLS.put(direction, wallPos);
            pl.horizontalCollision = true;
            if (vsEnabled()) VSCompat.registerShipWallAt(pl.level(), direction, wallHit);
        }

        // A hull is rarely square to the player's frame, so it stops them at a
        // CORNER of the body, well off the thin rays above — the very miss the
        // world-frame path probes ships with a whole box to avoid. Under a
        // gravity field that box was missing, so a ship's own wall could not be
        // clung to at all while a gravity core held the player. Probe the body's
        // axis with a box grown on every side (the frame's tangent plane is not
        // world X/Z), and read the wall off the face touched, so the deck
        // underfoot and the ceiling overhead are not walls.
        if (vsEnabled()) {
            VSCompat.ShipContact contact = VSCompat.findShipWall(pl.level(), shipProbe(pl), gravityUp, pressDirection(pl));
            if (contact != null) {
                // Which way the wall lies is asked in the player's own frame:
                // "north" means north to them, not to the world.
                Vec3 toward = GravityCompat.toLocal(contact.normal().scale(-1.0), gravityFrame);
                Direction direction = Direction.getNearest(toward.x, 0.0, toward.z);
                // A wall a ray already found keeps that ray's block, unless it
                // belongs to a ship: then this contact is the better record of
                // it, carrying the true face normal and the depth the push-out
                // needs. Either way it is the same wall, not a second one.
                if (toward.horizontalDistanceSqr() > 1.0E-6
                        && (!walls.contains(direction) || VSCompat.isShipWall(direction))
                        && wallAllowed(pl, contact.blockPos(), contact.blockPos().relative(upDir))) {
                    VSCompat.recordShipWall(direction, contact);
                    walls.add(direction);
                    pl.horizontalCollision = true;
                }
            }
        }
    }

    /**
     * Keeps track of the wall being held. While clinging the same wall is kept
     * as long as it is still there, so an inside corner cannot swap the grip
     * between its two walls; otherwise the wall most squarely in front is
     * taken, which is the one a player pressing into a corner means.
     */
    private static void trackClingWall(LocalPlayer pl) {
        if (walls.isEmpty()) {
            clingDirection = null;
            clingWallNormal = null;
            clingOnShip = false;
            return;
        }
        if (ticksWallClinged < 1 || clingDirection == null || !walls.contains(clingDirection)) {
            clingDirection = facingWall(pl);
        }
        clingOnShip = vsEnabled() && VSCompat.isShipWall(clingDirection);
        Vec3 shipNormal = clingOnShip ? VSCompat.getShipWallNormal(clingDirection) : null;
        clingWallNormal = shipNormal != null ? shipNormal : Vec3.atLowerCornerOf(clingDirection.getOpposite().getNormal());
        clingWallYaw = wallYaw(clingDirection, shipNormal);
    }

    /** Of the walls in reach, the one most squarely in front of the player. */
    private static Direction facingWall(LocalPlayer pl) {
        float rad = pl.getYRot() * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(rad), 0.0, Mth.cos(rad));
        Direction best = null;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Direction direction : walls) {
            double dot = facing.dot(Vec3.atLowerCornerOf(direction.getNormal()));
            if (dot > bestDot) {
                bestDot = dot;
                best = direction;
            }
        }
        return best;
    }

    /**
     * The way the player is pressing, in world space: into the wall they hold
     * while clinging, else the way they face. Used to settle which of two
     * touching ship faces is the wall.
     */
    private static Vec3 pressDirection(LocalPlayer pl) {
        if (ticksWallClinged > 0 && clingWallNormal != null) return clingWallNormal.scale(-1.0);
        float rad = pl.getYRot() * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(rad), 0.0, Mth.cos(rad));
        // Yaw is frame-local under a gravity frame; the face normals this is
        // weighed against are world-space, so it has to leave the frame first.
        return gravityFrame != null ? GravityCompat.toWorld(facing, gravityFrame) : facing;
    }

    /**
     * Yaw of the way to a wall, in the player's own terms (frame-local under a
     * gravity frame, as their yaw is): the hull's true bearing when a ship's
     * normal is given, the cardinal's own yaw otherwise.
     */
    private static float wallYaw(Direction direction, Vec3 shipNormal) {
        if (shipNormal != null) {
            Vec3 toward = shipNormal.scale(-1.0);
            if (gravityFrame != null) toward = GravityCompat.toLocal(toward, gravityFrame);
            if (toward.horizontalDistanceSqr() > 1.0E-6) {
                return (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
            }
        }
        return direction.toYRot();
    }

    /**
     * Whether the block list lets the wall be clung to: any of the given
     * positions that holds a solid block and passes the list will do.
     */
    private static boolean wallAllowed(LocalPlayer pl, BlockPos... positions) {
        if (ServerConfig.blockListMode == BlockListMode.DISABLED || ServerConfig.blockList.isEmpty()) return true;
        for (BlockPos pos : positions) {
            BlockState state = pl.level().getBlockState(pos);
            if (!state.isSolid()) continue;
            boolean listed = BlockListMatcher.matches(ServerConfig.blockList, state);
            if (ServerConfig.blockListMode == BlockListMode.BLACKLIST ? !listed : listed) return true;
        }
        return false;
    }

    private static Direction getClingDirection() {
        if (walls.isEmpty()) return Direction.UP;
        return clingDirection != null && walls.contains(clingDirection) ? clingDirection : walls.iterator().next();
    }

    private static BlockPos getWallPos(LocalPlayer player) {
        Direction direction = getClingDirection();
        if (gravityFrame != null) {
            BlockPos frameWall = FRAME_WALLS.get(direction);
            if (frameWall != null) return frameWall;
        }
        if (VS_LOADED) {
            BlockPos shipWall = VSCompat.getShipWallPos(direction);
            if (shipWall != null) return shipWall;
        }
        BlockPos blockPos = player.getOnPos().relative(direction);
        return player.level().getBlockState(blockPos).isSolid() ? blockPos : blockPos.relative(Direction.UP);
    }

    private static void wallJump(LocalPlayer pl, float up) {
        float strafe = Math.signum(pl.input.leftImpulse) * up * up;
        float forward = Math.signum(pl.input.forwardImpulse) * up * up;

        float f = (float) (1.0F / Math.sqrt(strafe * strafe + up * up + forward * forward));
        strafe = strafe * f;
        forward = forward * f;

        float f1 = (float) (Math.sin(pl.getYRot() * 0.017453292F) * 0.45F);
        float f2 = (float) (Math.cos(pl.getYRot() * 0.017453292F) * 0.45F);

        int jumpBoostLevel = 0;
        MobEffectInstance jumpBoostEffect = pl.getEffect(MobEffects.JUMP);
        if (jumpBoostEffect != null) jumpBoostLevel = jumpBoostEffect.getAmplifier() + 1;

        // (yaw and velocity are frame-local under a gravity frame, so this
        // composes the jump in the right frame as it is)
        Vec3 motion = pl.getDeltaMovement();
        pl.setDeltaMovement(motion.x + (strafe * f2 - forward * f1), up + (jumpBoostLevel * 0.125), motion.z + (forward * f2 + strafe * f1));

        lastJumpHeight = height(pl);
        playBreakSound(pl, getWallPos(pl));
        spawnWallParticle(pl, getWallPos(pl));
        wallJumpCount++;
    }

    private static void playHitSound(Entity entity, BlockPos blockPos) {
        BlockState state = entity.level().getBlockState(blockPos);
        SoundType soundtype = state.getBlock().getSoundType(state);
        entity.playSound(soundtype.getHitSound(), soundtype.getVolume() * 0.25F, soundtype.getPitch());
    }

    private static void playBreakSound(Entity entity, BlockPos blockPos) {
        BlockState state = entity.level().getBlockState(blockPos);
        SoundType soundtype = state.getBlock().getSoundType(state);
        entity.playSound(soundtype.getFallSound(), soundtype.getVolume() * 0.5F, soundtype.getPitch());
    }

    /**
     * One scrape of the wall's own sound, quieter than the grip that started
     * the cling and a little off its pitch each time, so a long slide is a
     * scuffing rather than a drum roll.
     */
    private static void playSlideSound(Entity entity, BlockPos blockPos) {
        BlockState state = entity.level().getBlockState(blockPos);
        SoundType soundtype = state.getBlock().getSoundType(state);
        float pitch = soundtype.getPitch() * (0.85F + entity.level().getRandom().nextFloat() * 0.3F);
        entity.playSound(soundtype.getHitSound(), soundtype.getVolume() * 0.2F, pitch);
    }

    /**
     * Counts the slide and scrapes the wall every so many blocks of it, so the
     * sound keeps pace with the fall: a fast slide scuffs more often.
     */
    private static void scuffWall(LocalPlayer pl, double dropped) {
        slideScuff += dropped;
        if (slideScuff < SLIDE_SCUFF_STEP) return;
        slideScuff -= SLIDE_SCUFF_STEP;
        if (ModConfig.playSlideSound) playSlideSound(pl, getWallPos(pl));
    }

    /**
     * Where the cling scuffs the wall: the wall face beside the gripping hand,
     * so the scrape comes off the hand the pose has on the wall rather than off
     * the feet. Falls back to the entity's own position when there is no pose to
     * match it to — pushing off in a wall jump, or the pose switched off.
     */
    private static Vec3 clingContactPos(Entity entity) {
        if (!ModConfig.wallClingPose || !isClinging() || walls.isEmpty()
                || !(entity instanceof WallClingHolder holder)) return entity.position();

        // Yaw, the wall bearing and the offset are all frame-local under a
        // gravity frame, so the whole offset is converted in one go at the end.
        float yaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
        float rad = yaw * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(rad), 0.0, -Mth.sin(rad));
        float wallRad = clingWallYaw * Mth.DEG_TO_RAD;
        Vec3 into = new Vec3(-Mth.sin(wallRad), 0.0, Mth.cos(wallRad));

        Vec3 offset = right.scale(holder.walljumpunbound$wallClingGripRight() ? HAND_REACH : -HAND_REACH)
                .add(0.0, HAND_HEIGHT, 0.0)
                .add(into.scale(entity.getBbWidth() / 2));
        if (gravityFrame != null) offset = GravityCompat.toWorld(offset, gravityFrame);

        return entity.position().add(offset);
    }

    private static void spawnWallParticle(Entity entity, BlockPos blockPos) {
        BlockState state = entity.level().getBlockState(blockPos);
        if (state.getRenderShape() != RenderShape.INVISIBLE) {
            Vec3 pos = clingContactPos(entity);
            // Flung off the wall, down its true face rather than the nearest cardinal's.
            float wallRad = clingWallYaw * Mth.DEG_TO_RAD;
            Vec3 velocity = new Vec3(Mth.sin(wallRad), -1.0D, -Mth.cos(wallRad));
            if (gravityFrame != null) velocity = GravityCompat.toWorld(velocity, gravityFrame);

            entity.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z,
                    velocity.x, velocity.y, velocity.z);
        }
    }
}

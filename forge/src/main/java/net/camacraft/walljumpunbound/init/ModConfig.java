package net.camacraft.walljumpunbound.init;

import net.camacraft.camafy.config.ConfigMigrator;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.EnumValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod's config: {@code config/walljumpunbound.toml}, written and read by
 * Forge's config system (Forge Config API Port on Fabric), which puts every
 * option's comment right above its value in the file and re-reads the file
 * when it changes on disk.
 *
 * <p>The values themselves are mirrored into the static fields below, which
 * is what the rest of the mod reads: they hold the defaults until the file has
 * loaded, then whatever it says. {@link ServerConfig} copies them by name, so
 * the field names, the TOML keys and the ServerConfig fields all match.
 *
 * <p>Options are either <b>server</b> (the server's value is sent to every
 * joining client and sets the rules) or <b>client</b> (purely local); the
 * comment on each says which.
 */
public final class ModConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("walljumpunbound");

    public enum BlockListMode {DISABLED, BLACKLIST, WHITELIST}

    /** Bumped when a shipped default changes; {@link #registerMigrations} moves older files forward. */
    public static final int CURRENT_CONFIG_VERSION = 1;
    /** How long a cling holds before the slide starts, by default (it was 15 before 1.1.3). */
    public static final int DEFAULT_WALL_SLIDE_DELAY = 4;

    // ---------------------------------------------------------------- wall jump
    public static boolean wallJumpEnabled;
    public static double wallJumpHeight;
    public static int maxWallJumps;
    public static double exhaustionWallJump;
    public static double exhaustionLedgeGrab;
    public static boolean allowReClinging;
    public static boolean onFallWallCling;
    public static boolean autoRotation;
    public static int wallSlideDelay;
    public static int stopWallSlideDelay;
    public static boolean ledgeGrab;
    public static double minFallDistance;
    public static boolean clingFallDamage;
    public static double clingFallDamageMinDistance;
    public static double clingFallDamageFraction;
    public static boolean wallClingPose;
    public static boolean playFallingSound;
    public static boolean playSlideSound;
    // ------------------------------------------------------------ other movement
    public static boolean doubleJumpEnabled;
    public static boolean onFallDoubleJump;
    public static double sprintSpeedBoost;
    public static double elytraSpeedBoost;
    public static boolean stepAssist;
    // ---------------------------------------------------------------- block list
    public static BlockListMode blockListMode;
    public static List<String> blockList = new ArrayList<>();
    // ----------------------------------------------------------- valkyrien skies
    public static boolean enableVSCompat;
    public static double shipWallDetectionRange;
    public static boolean stickToMovingShips;
    public static boolean debugShipCling;
    // -------------------------------------------------------------------- create
    public static boolean enableCreateCompat;
    public static double contraptionWallDetectionRange;
    public static boolean stickToMovingContraptions;
    // -------------------------------------------------------------- enchantments
    public static boolean enableEnchantments;
    public static boolean wallJumpEnchantment;
    public static boolean doubleJumpEnchantment;
    public static boolean speedBoostEnchantment;
    public static double speedBoostMultiplier;
    public static boolean reClingEnchantment;
    public static int maxReClingLevel;

    private static final Values VALUES;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<Values, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Values::new);
        VALUES = pair.getLeft();
        SPEC = pair.getRight();
        apply(false);
    }

    private ModConfig() {
    }

    /**
     * Moves a file still holding an old default to the new one when it loads (a value someone changed never
     * matches the old default and is left alone). Registered before the mod's own loading listener, so
     * {@link #load} already reads the moved values; Forge saves the file right after the loading event.
     */
    public static void registerMigrations(IEventBus modBus) {
        ConfigMigrator.register(modBus, SPEC, VALUES.configVersion, CURRENT_CONFIG_VERSION, from -> {
            if (from < 1) {
                // 1.1.3: no second cling on the wall just jumped from, and the slide starts a moment after the grip
                ConfigMigrator.move(VALUES.allowReClinging, true, false);
                ConfigMigrator.move(VALUES.wallSlideDelay, 15, DEFAULT_WALL_SLIDE_DELAY);
            }
        });
    }

    /** Copies the file's values into the static fields. Called once the file has loaded, and again whenever it changes. */
    public static void load() {
        apply(true);
        // Nothing ever syncs ServerConfig on a dedicated server, so there it
        // follows the file directly (enchantment gating and exhaustion read it).
        if (FMLEnvironment.dist.isDedicatedServer()) ServerConfig.reset();
        LOGGER.info("Config loaded: walljumpunbound.toml");
    }

    private static <T> T value(ConfigValue<T> option, boolean loaded) {
        return loaded ? option.get() : option.getDefault();
    }

    private static void apply(boolean loaded) {
        wallJumpEnabled = value(VALUES.wallJumpEnabled, loaded);
        wallJumpHeight = value(VALUES.wallJumpHeight, loaded);
        maxWallJumps = value(VALUES.maxWallJumps, loaded);
        exhaustionWallJump = value(VALUES.exhaustionWallJump, loaded);
        exhaustionLedgeGrab = value(VALUES.exhaustionLedgeGrab, loaded);
        allowReClinging = value(VALUES.allowReClinging, loaded);
        onFallWallCling = value(VALUES.onFallWallCling, loaded);
        autoRotation = value(VALUES.autoRotation, loaded);
        wallSlideDelay = value(VALUES.wallSlideDelay, loaded);
        stopWallSlideDelay = value(VALUES.stopWallSlideDelay, loaded);
        ledgeGrab = value(VALUES.ledgeGrab, loaded);
        minFallDistance = value(VALUES.minFallDistance, loaded);
        clingFallDamage = value(VALUES.clingFallDamage, loaded);
        clingFallDamageMinDistance = value(VALUES.clingFallDamageMinDistance, loaded);
        clingFallDamageFraction = value(VALUES.clingFallDamageFraction, loaded);
        wallClingPose = value(VALUES.wallClingPose, loaded);
        playFallingSound = value(VALUES.playFallingSound, loaded);
        playSlideSound = value(VALUES.playSlideSound, loaded);

        doubleJumpEnabled = value(VALUES.doubleJumpEnabled, loaded);
        onFallDoubleJump = value(VALUES.onFallDoubleJump, loaded);
        sprintSpeedBoost = value(VALUES.sprintSpeedBoost, loaded);
        elytraSpeedBoost = value(VALUES.elytraSpeedBoost, loaded);
        stepAssist = value(VALUES.stepAssist, loaded);

        blockListMode = value(VALUES.blockListMode, loaded);
        blockList = new ArrayList<>(value(VALUES.blockList, loaded));

        enableVSCompat = value(VALUES.enableVSCompat, loaded);
        shipWallDetectionRange = value(VALUES.shipWallDetectionRange, loaded);
        stickToMovingShips = value(VALUES.stickToMovingShips, loaded);
        debugShipCling = value(VALUES.debugShipCling, loaded);

        enableCreateCompat = value(VALUES.enableCreateCompat, loaded);
        contraptionWallDetectionRange = value(VALUES.contraptionWallDetectionRange, loaded);
        stickToMovingContraptions = value(VALUES.stickToMovingContraptions, loaded);

        enableEnchantments = value(VALUES.enableEnchantments, loaded);
        wallJumpEnchantment = value(VALUES.wallJumpEnchantment, loaded);
        doubleJumpEnchantment = value(VALUES.doubleJumpEnchantment, loaded);
        speedBoostEnchantment = value(VALUES.speedBoostEnchantment, loaded);
        speedBoostMultiplier = value(VALUES.speedBoostMultiplier, loaded);
        reClingEnchantment = value(VALUES.reClingEnchantment, loaded);
        maxReClingLevel = value(VALUES.maxReClingLevel, loaded);
    }

    /** The spec: every option with its comment, default and range, in the order the file shows them. */
    private static final class Values {
        final BooleanValue wallJumpEnabled;
        final DoubleValue wallJumpHeight;
        final IntValue maxWallJumps;
        final DoubleValue exhaustionWallJump;
        final DoubleValue exhaustionLedgeGrab;
        final BooleanValue allowReClinging;
        final BooleanValue onFallWallCling;
        final BooleanValue autoRotation;
        final IntValue wallSlideDelay;
        final IntValue stopWallSlideDelay;
        final BooleanValue ledgeGrab;
        final DoubleValue minFallDistance;
        final BooleanValue clingFallDamage;
        final DoubleValue clingFallDamageMinDistance;
        final DoubleValue clingFallDamageFraction;
        final BooleanValue wallClingPose;
        final BooleanValue playFallingSound;
        final BooleanValue playSlideSound;

        final BooleanValue doubleJumpEnabled;
        final BooleanValue onFallDoubleJump;
        final DoubleValue sprintSpeedBoost;
        final DoubleValue elytraSpeedBoost;
        final BooleanValue stepAssist;

        final EnumValue<BlockListMode> blockListMode;
        final ConfigValue<List<? extends String>> blockList;

        final BooleanValue enableVSCompat;
        final DoubleValue shipWallDetectionRange;
        final BooleanValue stickToMovingShips;
        final BooleanValue debugShipCling;

        final BooleanValue enableCreateCompat;
        final DoubleValue contraptionWallDetectionRange;
        final BooleanValue stickToMovingContraptions;

        final BooleanValue enableEnchantments;
        final BooleanValue wallJumpEnchantment;
        final BooleanValue doubleJumpEnchantment;
        final BooleanValue speedBoostEnchantment;
        final DoubleValue speedBoostMultiplier;
        final BooleanValue reClingEnchantment;
        final IntValue maxReClingLevel;

        final IntValue configVersion;

        Values(ForgeConfigSpec.Builder b) {
            b.comment("Wall clinging and wall jumping.",
                    "Options marked [server] are the rules of play: on a dedicated server the server's values are sent",
                    "to every joining client and override this file. Options marked [client] only affect your own game.",
                    "Edits to this file are picked up while the game is running.")
                    .push("wallJump");
            wallJumpEnabled = b.comment("[server] Lets every player cling to walls and wall jump.",
                    "When off, only players wearing boots with the Wall Jump enchantment can (see [enchantments]).")
                    .define("wallJumpEnabled", true);
            wallJumpHeight = b.comment("[server] Upward speed of a wall jump. A normal jump is 0.42.")
                    .defineInRange("wallJumpHeight", 0.55, 0.0, 1.0);
            maxWallJumps = b.comment("[server] Wall jumps allowed in a row before touching the ground again. 72000 is as good as unlimited.")
                    .defineInRange("maxWallJumps", 72000, 0, Integer.MAX_VALUE);
            exhaustionWallJump = b.comment("[server] Hunger spent per wall jump, in exhaustion points. Sprinting costs about 0.1 per block.")
                    .defineInRange("exhaustionWallJump", 0.8, 0.0, 5.0);
            exhaustionLedgeGrab = b.comment("[server] Hunger spent per second of hanging from a ledge, in exhaustion points.",
                    "Sprinting costs about 0.6 a second. Run out of food and your grip gives out. 0 is off.")
                    .defineInRange("exhaustionLedgeGrab", 1.0, 0.0, 20.0);
            allowReClinging = b.comment("[server] Cling again to the wall you just jumped off.",
                    "When off (the default), you must first fall a block below your jump or reach a different wall,",
                    "so one wall cannot be climbed by jumping off it and catching it again; boots with Re-cling allow",
                    "one such cling per level before you touch the ground (see [enchantments]).")
                    .define("allowReClinging", false);
            onFallWallCling = b.comment("[server] Catch a wall while falling faster than 0.8 blocks a tick.",
                    "When off, a fast fall cannot be stopped by clinging.")
                    .define("onFallWallCling", true);
            autoRotation = b.comment("[client] Snap your view to face straight away from the wall when you cling.",
                    "Skipped on Valkyrien Skies ships, whose walls are rarely on a compass point.")
                    .define("autoRotation", false);
            wallSlideDelay = b.comment("[server] Ticks a cling holds before you start sliding down (20 ticks = 1 second).",
                    "The default 4 catches you for a moment and then lets you slide; 15 was the default before 1.1.3.",
                    "Hanging from a ledge never slides.")
                    .defineInRange("wallSlideDelay", DEFAULT_WALL_SLIDE_DELAY, 0, Integer.MAX_VALUE);
            stopWallSlideDelay = b.comment("[server] Ticks of sliding before your grip gives out and you drop. 72000 is as good as unlimited.")
                    .defineInRange("stopWallSlideDelay", 72000, 0, Integer.MAX_VALUE);
            ledgeGrab = b.comment("[server] Grab the top of a wall that is within arm's reach.",
                    "A ledge hold never slides, and turning more than 90 degrees away from it lets go.")
                    .define("ledgeGrab", true);
            minFallDistance = b.comment("[server] Falls up to this many blocks do no damage (vanilla: 3).",
                    "Catching a wall never hurts below it either.")
                    .defineInRange("minFallDistance", 3.0, 3.0, 256.0);
            clingFallDamage = b.comment("[server] Catching a wall after a long fall hurts: a share of the damage the ground would have done.",
                    "Armour does not stop it; Feather Falling and Protection reduce it, like real fall damage.")
                    .define("clingFallDamage", true);
            clingFallDamageMinDistance = b.comment("[server] The fall, in blocks, from which catching a wall starts to hurt. Never lower than minFallDistance.")
                    .defineInRange("clingFallDamageMinDistance", 6.0, 3.0, 256.0);
            clingFallDamageFraction = b.comment("[server] Share of the ground's fall damage dealt by catching a wall instead. 0.5 is half.",
                    "With 0.35, a 10-block fall caught on a wall costs 3 health instead of the 7 the ground would take.")
                    .defineInRange("clingFallDamageFraction", 0.35, 0.0, 1.0);
            wallClingPose = b.comment("[client] Draw the climbing pose on clinging players: one arm on the wall, or both hands over a ledge.")
                    .define("wallClingPose", true);
            playFallingSound = b.comment("[client] Rushing wind while falling fast.")
                    .define("playFallingSound", true);
            playSlideSound = b.comment("[client] Scrape the wall's own block sound while sliding down it.")
                    .define("playSlideSound", true);
            b.pop();

            b.comment("Double jump, speed boost and step assist.").push("movement");
            doubleJumpEnabled = b.comment("[server] Lets every player jump once more in mid-air.",
                    "When off, only the Double Jump enchantment grants extra jumps.")
                    .define("doubleJumpEnabled", false);
            onFallDoubleJump = b.comment("[server] Allow the mid-air jump while falling faster than 0.8 blocks a tick.")
                    .define("onFallDoubleJump", true);
            sprintSpeedBoost = b.comment("[server] Extra sprint speed for every player, on top of the Speed Boost enchantment. 0 is off.")
                    .defineInRange("sprintSpeedBoost", 0.0, 0.0, 5.0);
            elytraSpeedBoost = b.comment("[server] Extra speed while holding sprint on an elytra, on top of the Speed Boost enchantment. 0 is off.")
                    .defineInRange("elytraSpeedBoost", 0.0, 0.0, 5.0);
            stepAssist = b.comment("[server] Walk up single-block steps without jumping, even mid-air, and hop over fences.")
                    .define("stepAssist", true);
            b.pop();

            b.comment("Which blocks can be clung to.").push("blockList");
            blockListMode = b.comment("[server] DISABLED: any solid block can be clung to. BLACKLIST: any block except those listed.",
                    "WHITELIST: only the blocks listed.")
                    .defineEnum("blockListMode", BlockListMode.BLACKLIST);
            blockList = b.comment("[server] The blocks, one entry each. An empty list allows every block whatever the mode. Each entry is one of:",
                    "  a block id            \"minecraft:ice\"                 (a bare \"ice\" means minecraft:ice)",
                    "  a block tag           \"#minecraft:logs\"               (every block in the tag, other mods' tags included)",
                    "  a wildcard            \"minecraft:*_glass\"             (* is any run of characters, ? a single one)",
                    "  a regular expression  \"/minecraft:(oak|spruce)_.*/\"   (between slashes, matched against the whole id)",
                    "A wall is checked at the two blocks you face: if either passes the list, it can be clung to.",
                    "Entries that do not parse are logged at startup and skipped.")
                    .defineListAllowEmpty(List.of("blockList"), () -> new ArrayList<String>(), entry -> entry instanceof String);
            b.pop();

            b.comment("Clinging to Valkyrien Skies ship hulls. Nothing here does anything without Valkyrien Skies installed.")
                    .push("valkyrienSkies");
            enableVSCompat = b.comment("[client] Cling to and jump off ship hulls, moving and rotated ones included.")
                    .define("enableVSCompat", true);
            shipWallDetectionRange = b.comment("[client] How far past your hitbox ship walls are felt for, in blocks.",
                    "Raise it if clinging to tilted hulls feels finicky.")
                    .defineInRange("shipWallDetectionRange", 0.06, 0.01, 0.5);
            stickToMovingShips = b.comment("[client] Carry you along with the ship's movement and rotation while clinging.",
                    "When off, the ship can sail out from under your grip.")
                    .define("stickToMovingShips", true);
            debugShipCling = b.comment("[client] Write ship cling and wall detection details to the game log, including why a cling was",
                    "refused and what the hull probe (and, with Create, the contraption probe) touched. Turn it on before reporting a ship bug.")
                    .define("debugShipCling", false);
            b.pop();

            b.comment("Clinging to Create contraptions: the blocks a bearing turns, a piston, pulley or gantry moves, a minecart",
                    "contraption or a train carriage. Nothing here does anything without Create installed.")
                    .push("create");
            enableCreateCompat = b.comment("[client] Cling to and jump off the blocks of Create contraptions, moving and turning ones included.")
                    .define("enableCreateCompat", true);
            contraptionWallDetectionRange = b.comment("[client] How far past your hitbox contraption walls are felt for, in blocks.",
                    "Raise it if clinging to a turning contraption feels finicky.")
                    .defineInRange("contraptionWallDetectionRange", 0.06, 0.01, 0.5);
            stickToMovingContraptions = b.comment("[client] Carry you along with the contraption's movement and turning while clinging,",
                    "and give you its speed when you let go or jump off. When off, it slides out from under your grip.")
                    .define("stickToMovingContraptions", true);
            b.pop();

            b.comment("The mod's enchantments. Wall Jump, Double Jump and Speed Boost stand in for the moves the [wallJump] and [movement]",
                    "switches give everyone, and are gated by enableEnchantments; Re-cling has its own switch alone.")
                    .push("enchantments");
            enableEnchantments = b.comment("[server] Master switch for Wall Jump, Double Jump and Speed Boost. When off, those three turn up in no",
                    "enchanting table or trade, and boots that already have them give nothing. Re-cling does not follow it.")
                    .define("enableEnchantments", false);
            wallJumpEnchantment = b.comment("[server] Boots enchantment granting wall clinging and jumping to its wearer.",
                    "Only matters when wallJumpEnabled is off.")
                    .define("wallJumpEnchantment", true);
            doubleJumpEnchantment = b.comment("[server] Boots enchantment granting one extra mid-air jump per level, up to II.")
                    .define("doubleJumpEnchantment", true);
            speedBoostEnchantment = b.comment("[server] Boots and chestplate enchantment that speeds up sprinting and elytra flight.")
                    .define("speedBoostEnchantment", true);
            speedBoostMultiplier = b.comment("[server] How much speed each level of Speed Boost adds.")
                    .defineInRange("speedBoostMultiplier", 0.5, 0.25, 1.0);
            reClingEnchantment = b.comment("[server] Boots enchantment, I to III: each level is one more cling on the wall you just jumped off",
                    "before you touch the ground again. Only matters while allowReClinging is off. Its own switch alone: on the",
                    "enchanting table, in trades and working whenever this is true, whatever enableEnchantments says.")
                    .define("reClingEnchantment", true);
            maxReClingLevel = b.comment("[server] The highest level of Re-cling that counts: boots with more give this many. 0 turns it off.")
                    .defineInRange("maxReClingLevel", 3, 0, 3);
            b.pop();

            b.push("meta");
            // 0 by default on purpose: a file written before this key existed gains it at 0, which marks it as never
            // migrated. A fresh file migrates as a no-op and is stamped current.
            configVersion = b.comment("Internal: which generation of shipped defaults this file has seen. Settings nobody changed",
                    "follow the mod's new defaults; settings you changed are left alone. Do not edit.")
                    .defineInRange("configVersion", 0, 0, Integer.MAX_VALUE);
            b.pop();
        }
    }
}

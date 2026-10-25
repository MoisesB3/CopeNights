package us.copestudios.copenights.bloodmoon;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import us.copestudios.copecore.config.ConfigFile;
import us.copestudios.copenights.CopeNights;

import java.math.BigDecimal;
import java.util.List;

/**
 * bloodmoon.yml. Every value the in-game menu can change is a {@link Setting}; the lists (worlds, mobs,
 * bosses, commands) have their own getters.
 *
 * <p>The file is loaded through CopeCore's {@link ConfigFile}: new settings from updates are added to it
 * automatically, a broken file is repaired, and comments are kept when the menu saves.</p>
 */
public final class BloodMoonSettings {

    /** What kind of value a setting holds (decides how the menu edits and shows it). */
    public enum Kind { BOOL, INT, DOUBLE, PERCENT }

    /**
     * Everything editable in /coni bloodmoon.
     * <p>name = the button title, description = its first lore line. Both can be changed in messages.yml under
     * {@code menu.settings.<path>.name} / {@code .description} (optional: the English text below is used otherwise).</p>
     */
    public enum Setting {
        ENABLED("enabled", "Blood Moons Enabled", Material.REDSTONE_TORCH, Kind.BOOL, 0, 0, 0,
                "Automatic blood moons on/off"),
        EVERY_DAYS("every-days", "Every X Days", Material.CLOCK, Kind.INT, 1, 100, 1,
                "Minecraft days between blood moons"),
        SPAWN_MULTIPLIER("spawn-multiplier", "Spawn Multiplier", Material.ZOMBIE_HEAD, Kind.DOUBLE, 1, 5, 0.25,
                "2.0 = double hostile spawns"),
        HEALTH_MULTIPLIER("health-multiplier", "Mob Health", Material.GOLDEN_APPLE, Kind.DOUBLE, 1, 10, 0.25,
                "Multiplies blood moon mob max health"),
        DAMAGE_MULTIPLIER("damage-multiplier", "Mob Damage", Material.IRON_SWORD, Kind.DOUBLE, 1, 10, 0.25,
                "Multiplies damage dealt by blood moon mobs"),
        SPEED_MULTIPLIER("speed-multiplier", "Mob Speed", Material.SUGAR, Kind.DOUBLE, 1, 3, 0.05,
                "Multiplies blood moon mob movement speed"),
        GIANT_CHANCE("giant-chance", "Giant Chance", Material.SLIME_BALL, Kind.PERCENT, 0, 1, 0.05,
                "Chance a mob spawns as a giant"),
        GIANT_SCALE("giant-scale", "Giant Size", Material.PISTON, Kind.DOUBLE, 1, 4, 0.1,
                "How big giants are (1.0 = normal)"),
        SPECIAL_CHANCE("special-chance", "Special Mob Chance", Material.WITHER_SKELETON_SKULL, Kind.PERCENT, 0, 1, 0.05,
                "Chance a spawn is swapped for a special mob"),
        MAX_MOBS("max-mobs", "Max Mobs Per World", Material.HOPPER, Kind.INT, 25, 2000, 25,
                "Lag safety cap for blood moon mobs"),
        DROP_MULTIPLIER("drop-multiplier", "Drop Multiplier", Material.CHEST, Kind.DOUBLE, 1, 5, 0.25,
                "Extra loot from blood moon mobs"),
        XP_MULTIPLIER("xp-multiplier", "XP Multiplier", Material.EXPERIENCE_BOTTLE, Kind.DOUBLE, 1, 10, 0.5,
                "Extra XP from blood moon mobs"),
        BLOCK_SLEEP("block-sleep", "Block Sleeping", Material.RED_BED, Kind.BOOL, 0, 0, 0,
                "Players can't skip the night"),
        RED_SKY("red-sky", "Red Screen Tint", Material.RED_STAINED_GLASS, Kind.BOOL, 0, 0, 0,
                "Red vignette over players' screens"),
        BOSS_BAR("boss-bar", "Boss Bar", Material.DRAGON_HEAD, Kind.BOOL, 0, 0, 0,
                "Red bar with time left and kill count"),
        THUNDER("thunder", "Thunderstorm", Material.LIGHTNING_ROD, Kind.BOOL, 0, 0, 0,
                "Storm rolls in during the blood moon"),
        DESPAWN_AT_END("despawn-at-end", "Despawn Mobs At Dawn", Material.ENDER_PEARL, Kind.BOOL, 0, 0, 0,
                "Blood moon mobs vanish when it ends"),
        SURVIVOR_REWARDS("survivor-rewards", "Survivor Rewards", Material.NETHER_STAR, Kind.BOOL, 0, 0, 0,
                "Run reward-commands for players who never died"),
        SCARE_BOOST("scare-boost", "Scare Boost", Material.SCULK_SHRIEKER, Kind.DOUBLE, 1, 5, 0.5,
                "Auto-scare chance multiplier during blood moon"),
        ANNOUNCE("announce", "Announcements", Material.BELL, Kind.BOOL, 0, 0, 0,
                "Titles, warnings and sounds"),
        SCARES("scares", "Blood Moon Scares", Material.JACK_O_LANTERN, Kind.BOOL, 0, 0, 0,
                "Random scares all night (works even with /coni toggle off)"),
        SCARE_INTERVAL("scare-interval-seconds", "Scare Roll Interval", Material.REPEATER, Kind.INT, 10, 300, 10,
                "Seconds between scare rolls for each player"),
        SCARE_CHANCE("scare-chance", "Scare Chance", Material.FERMENTED_SPIDER_EYE, Kind.PERCENT, 0, 1, 0.05,
                "Chance each player gets scared on every roll"),
        DEATH_MESSAGES("death-messages", "Custom Death Messages", Material.SKELETON_SKULL, Kind.BOOL, 0, 0, 0,
                "Coni death messages for horde, mob and player kills"),

        // ---- Horde page (every name starting with HORDE) ----
        HORDES("hordes", "Auto Hordes", Material.GOAT_HORN, Kind.BOOL, 0, 0, 0,
                "Waves of hordes hit random players during blood moons"),
        HORDE_FIRST_DELAY("horde-first-delay-seconds", "First Wave Delay", Material.CLOCK, Kind.INT, 0, 600, 10,
                "Seconds after moonrise before wave 1"),
        HORDE_INTERVAL("horde-interval-seconds", "Time Between Waves", Material.COMPASS, Kind.INT, 15, 600, 15,
                "Seconds between waves"),
        HORDE_SIZE("horde-size", "Starting Horde Size", Material.ROTTEN_FLESH, Kind.INT, 1, 60, 1,
                "Mobs per horde in wave 1 (captain not included)"),
        HORDE_GROWTH("horde-growth", "Growth Per Wave", Material.BONE_MEAL, Kind.INT, 0, 20, 1,
                "Mobs added to each horde every new wave"),
        HORDE_MAX_SIZE("horde-max-size", "Max Horde Size", Material.IRON_BARS, Kind.INT, 5, 100, 5,
                "Hordes never grow past this"),
        HORDE_PLAYERS_PER("horde-players-per-horde", "Players Per Horde", Material.PLAYER_HEAD, Kind.INT, 1, 20, 1,
                "Each wave sends 1 horde per this many players online"),
        HORDE_MAX_PER_WAVE("horde-max-per-wave", "Max Hordes Per Wave", Material.SPAWNER, Kind.INT, 1, 20, 1,
                "Most hordes one wave can send"),
        HORDE_RADIUS("horde-radius", "Horde Distance", Material.TARGET, Kind.INT, 6, 40, 2,
                "How far from the player the horde appears"),
        HORDE_CAPTAIN("horde-captain", "Horde Captain", Material.GOLDEN_HELMET, Kind.BOOL, 0, 0, 0,
                "Each horde is led by a glowing giant captain"),
        HORDE_BOSS_CHANCE("horde-boss-chance", "Boss Chance", Material.DRAGON_EGG, Kind.PERCENT, 0, 1, 0.05,
                "Chance a horde brings a Cope Bosses boss");

        /** Key in bloodmoon.yml. */
        public final String path;
        /** Default English button name. */
        public final String display;
        public final Material icon;
        public final Kind kind;
        public final double min;
        public final double max;
        /** One click = one step (shift-click = 10 steps, Q = type a value). */
        public final double step;
        /** Default English description. */
        public final String description;

        Setting(String path, String display, Material icon, Kind kind,
                double min, double max, double step, String description) {
            this.path = path;
            this.display = display;
            this.icon = icon;
            this.kind = kind;
            this.min = min;
            this.max = max;
            this.step = step;
            this.description = description;
        }

        /** True = shown on the Horde Settings page instead of the main page. */
        public boolean isHorde() {
            return name().startsWith("HORDE");
        }

        /** True = edited with the number controls (anything that isn't on/off). */
        public boolean isNumber() {
            return kind != Kind.BOOL;
        }

        /**
         * The Minecraft version this setting needs, or null if it works everywhere.
         * Giants use the "scale" attribute, which only exists on 1.20.5+.
         */
        public String requiredVersion() {
            return this == GIANT_CHANCE || this == GIANT_SCALE ? "1.20.5" : null;
        }
    }

    private final CopeNights plugin;
    private final ConfigFile file;

    public BloodMoonSettings(CopeNights plugin) {
        this.plugin = plugin;
        this.file = new ConfigFile(plugin, "bloodmoon.yml");
    }

    /** Re-reads bloodmoon.yml from disk. */
    public void reload() {
        file.reload();
    }

    /** The underlying file (used by the upgrade from old versions). */
    public ConfigFile file() {
        return file;
    }

    // =====================================================================
    //  Setting values
    // =====================================================================

    public boolean getBool(Setting setting) {
        return file.get().getBoolean(setting.path);
    }

    public int getInt(Setting setting) {
        return file.get().getInt(setting.path);
    }

    public double getDouble(Setting setting) {
        return file.get().getDouble(setting.path);
    }

    /** Flips an on/off setting and saves. */
    public void toggle(Setting setting) {
        file.get().set(setting.path, !getBool(setting));
        file.save();
    }

    /** Sets a number setting (clamped to its min/max) and saves. */
    public void set(Setting setting, double value) {
        double clamped = Math.max(setting.min, Math.min(setting.max, value));
        if (setting.kind == Kind.INT) {
            file.get().set(setting.path, (int) Math.round(clamped));
        } else {
            file.get().set(setting.path, Math.round(clamped * 100.0) / 100.0);
        }
        file.save();
    }

    /** The current value as a number (on/off settings give 1 or 0). */
    public double numberValue(Setting setting) {
        return switch (setting.kind) {
            case BOOL -> getBool(setting) ? 1 : 0;
            case INT -> getInt(setting);
            case DOUBLE, PERCENT -> getDouble(setting);
        };
    }

    /**
     * The value as MiniMessage text for the menu and /coni bloodmoon status:
     * ON/OFF from messages.yml, "7", "1.5x" or "20%".
     */
    public String display(Setting setting) {
        String value = switch (setting.kind) {
            case BOOL -> null;
            case INT -> String.valueOf(getInt(setting));
            case DOUBLE -> trim(getDouble(setting)) + "x";
            case PERCENT -> Math.round(getDouble(setting) * 100) + "%";
        };
        return value == null ? plugin.onOff(getBool(setting)) : "<#FFE4C7>" + value;
    }

    // =====================================================================
    //  Lists and texts
    // =====================================================================

    /** Worlds that get blood moons. Empty = every overworld-type world. */
    public List<String> worlds() {
        return file.get().getStringList("worlds");
    }

    public List<String> specialMobs() {
        return file.get().getStringList("special-mobs");
    }

    public void setSpecialMobs(List<String> mobs) {
        file.get().set("special-mobs", mobs);
        file.save();
    }

    public List<String> hordeMobs() {
        return file.get().getStringList("horde-mobs");
    }

    /** CopeCore states that keep a player from being picked for automatic hordes. */
    public List<String> hordeSkipStates() {
        return file.get().getStringList("horde-skip-states");
    }

    public List<String> hordeBosses() {
        return file.get().getStringList("horde-bosses");
    }

    public String hordeBossCommand() {
        return file.get().getString("horde-boss-command", "cb spawn <boss>");
    }

    public boolean hordeBossRunAsPlayer() {
        return "player".equalsIgnoreCase(file.get().getString("horde-boss-run-as", "player"));
    }

    public List<String> hordeBossPermissions() {
        return file.get().getStringList("horde-boss-permissions");
    }

    public ConfigurationSection scareWeights() {
        return file.get().getConfigurationSection("scare-weights");
    }

    public String moonriseScare() {
        return file.get().getString("moonrise-scare", "LIGHTS_OUT");
    }

    public List<String> hordeWarningScares() {
        return file.get().getStringList("horde-warning-scares");
    }

    public List<String> rewardCommands() {
        return file.get().getStringList("reward-commands");
    }

    /** 2.0 -> "2", 1.25 -> "1.25" */
    private static String trim(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}

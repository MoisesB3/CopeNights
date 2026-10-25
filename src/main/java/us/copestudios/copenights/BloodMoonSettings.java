package us.copestudios.copenights;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Wraps bloodmoon.yml. Everything the GUI can change is a {@link Setting}. */
public class BloodMoonSettings {

    public enum Kind { BOOL, INT, DOUBLE }

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
        GIANT_CHANCE("giant-chance", "Giant Chance", Material.SLIME_BALL, Kind.DOUBLE, 0, 1, 0.05,
                "Chance a mob spawns as a giant"),
        GIANT_SCALE("giant-scale", "Giant Size", Material.PISTON, Kind.DOUBLE, 1, 4, 0.1,
                "How big giants are (1.0 = normal)"),
        SPECIAL_CHANCE("special-chance", "Special Mob Chance", Material.WITHER_SKELETON_SKULL, Kind.DOUBLE, 0, 1, 0.05,
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
        SCARE_CHANCE("scare-chance", "Scare Chance", Material.FERMENTED_SPIDER_EYE, Kind.DOUBLE, 0, 1, 0.05,
                "Chance each player gets scared on every roll"),
        DEATH_MESSAGES("death-messages", "Custom Death Messages", Material.SKELETON_SKULL, Kind.BOOL, 0, 0, 0,
                "Coni death messages for horde, mob and player kills"),

        // ---- Horde page ----
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
        HORDE_BOSS_CHANCE("horde-boss-chance", "Boss Chance", Material.DRAGON_EGG, Kind.DOUBLE, 0, 1, 0.05,
                "Chance a horde brings a Cope Bosses boss");

        public final String path;
        public final String display;
        public final Material icon;
        public final Kind kind;
        public final double min, max, step;
        public final String description;

        /** true = shown on the Horde Settings page instead of the main page */
        public boolean isHorde() {
            return name().startsWith("HORDE");
        }

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
    }

    private final CopeNights plugin;
    private File file;
    private YamlConfiguration cfg;

    public BloodMoonSettings(CopeNights plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "bloodmoon.yml");
        if (!file.exists()) plugin.saveResource("bloodmoon.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);
        InputStream in = plugin.getResource("bloodmoon.yml");
        if (in != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save bloodmoon.yml: " + e.getMessage());
        }
    }

    // ---------- values ----------

    public boolean getBool(Setting s) {
        return cfg.getBoolean(s.path);
    }

    public int getInt(Setting s) {
        return cfg.getInt(s.path);
    }

    public double getDouble(Setting s) {
        return cfg.getDouble(s.path);
    }

    public String display(Setting s) {
        return switch (s.kind) {
            case BOOL -> getBool(s) ? C.onOff(true) : C.onOff(false);
            case INT -> C.H + getInt(s);
            case DOUBLE -> {
                double v = getDouble(s);
                yield (s == Setting.GIANT_CHANCE || s == Setting.SPECIAL_CHANCE || s == Setting.HORDE_BOSS_CHANCE || s == Setting.SCARE_CHANCE)
                        ? C.H + Math.round(v * 100) + "%"
                        : C.H + trim(v) + "x";
            }
        };
    }

    /** dir = +1 / -1, big = shift-click (5 steps). Booleans just flip. */
    public void adjust(Setting s, int dir, boolean big) {
        switch (s.kind) {
            case BOOL -> cfg.set(s.path, !getBool(s));
            case INT -> {
                int v = getInt(s) + (int) (s.step * dir * (big ? 5 : 1));
                cfg.set(s.path, (int) clamp(v, s.min, s.max));
            }
            case DOUBLE -> {
                double v = getDouble(s) + s.step * dir * (big ? 5 : 1);
                v = Math.round(clamp(v, s.min, s.max) * 100.0) / 100.0;
                cfg.set(s.path, v);
            }
        }
        save();
    }

    public List<String> worlds() {
        return cfg.getStringList("worlds");
    }

    public List<String> specialMobs() {
        return cfg.getStringList("special-mobs");
    }

    public void setSpecialMobs(List<String> mobs) {
        cfg.set("special-mobs", mobs);
        save();
    }

    public List<String> hordeMobs() {
        return cfg.getStringList("horde-mobs");
    }

    public List<String> hordeBosses() {
        return cfg.getStringList("horde-bosses");
    }

    public String hordeBossCommand() {
        return cfg.getString("horde-boss-command", "cb spawn %boss%");
    }

    public boolean hordeBossRunAsPlayer() {
        return "player".equalsIgnoreCase(cfg.getString("horde-boss-run-as", "player"));
    }

    public List<String> hordeBossPermissions() {
        return cfg.getStringList("horde-boss-permissions");
    }

    public List<String> deathMessages(String type) {
        return cfg.getStringList("death-messages-text." + type);
    }

    public org.bukkit.configuration.ConfigurationSection scareWeights() {
        return cfg.getConfigurationSection("scare-weights");
    }

    public String startScare() {
        return cfg.getString("moonrise-scare", "LIGHTS_OUT");
    }

    public List<String> hordeWarningScares() {
        return cfg.getStringList("horde-warning-scares");
    }

    public List<String> rewardCommands() {
        return cfg.getStringList("reward-commands");
    }

    public String msg(String key) {
        return CopeNights.color(cfg.getString("messages." + key, ""));
    }

    /** Fills placeholders first, then colors - so gradients over %player% etc. come out right. */
    public String msg(String key, String... placeholderValuePairs) {
        String raw = cfg.getString("messages." + key, "");
        for (int i = 0; i + 1 < placeholderValuePairs.length; i += 2) {
            raw = raw.replace(placeholderValuePairs[i], placeholderValuePairs[i + 1]);
        }
        return CopeNights.color(raw);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}

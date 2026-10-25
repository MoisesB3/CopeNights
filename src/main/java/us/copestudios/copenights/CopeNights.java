package us.copestudios.copenights;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CopeNights extends JavaPlugin {

    private static CopeNights instance;

    private NamespacedKey scareEntityKey;
    private HerobrineManager herobrine;
    private ScareManager scares;
    private BukkitTask autoTask;
    private BloodMoonSettings bloodSettings;
    private BloodMoonManager bloodMoon;

    // Saved state (data.yml)
    private File dataFile;
    private boolean autoEnabled;
    private final Set<UUID> exempt = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        scareEntityKey = new NamespacedKey(this, "scare_entity");

        loadData();

        herobrine = new HerobrineManager(this);
        herobrine.loadSkin();
        scares = new ScareManager(this, herobrine);

        bloodSettings = new BloodMoonSettings(this);
        bloodSettings.load();
        bloodMoon = new BloodMoonManager(this, bloodSettings);
        bloodMoon.startTask();

        ScareCommand cmd = new ScareCommand(this);
        PluginCommand pc = getCommand("coni");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        getServer().getPluginManager().registerEvents(new ScareListener(this), this);
        getServer().getPluginManager().registerEvents(new BloodMoonListener(this, bloodMoon, bloodSettings), this);
        getServer().getPluginManager().registerEvents(new BloodMoonMenu.Handler(this), this);
        getServer().getPluginManager().registerEvents(new DeathMessageListener(this), this);

        restartAutoTask();
        getLogger().info("CopeNights enabled. Auto scares: " + (autoEnabled ? "ON" : "OFF"));
    }

    @Override
    public void onDisable() {
        if (autoTask != null) autoTask.cancel();
        if (herobrine != null) herobrine.removeAll();
        if (bloodMoon != null) bloodMoon.shutdown();
        saveData();
    }

    public void reload() {
        reloadConfig();
        herobrine.loadSkin();
        bloodSettings.load();
        restartAutoTask();
    }

    public void restartAutoTask() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
        long seconds = Math.max(5, getConfig().getLong("auto.check-interval-seconds", 30));
        autoTask = new AutoScareTask(this).runTaskTimer(this, seconds * 20L, seconds * 20L);
    }

    // ---------- data.yml ----------

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        autoEnabled = data.getBoolean("auto-enabled", false);
        exempt.clear();
        for (String s : data.getStringList("exempt")) {
            try {
                exempt.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void saveData() {
        if (dataFile == null) return;
        YamlConfiguration data = new YamlConfiguration();
        data.set("auto-enabled", autoEnabled);
        data.set("exempt", exempt.stream().map(UUID::toString).toList());
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    // ---------- getters / state ----------

    public static CopeNights get() {
        return instance;
    }

    public NamespacedKey scareEntityKey() {
        return scareEntityKey;
    }

    public ScareManager scares() {
        return scares;
    }

    public BloodMoonManager bloodMoon() {
        return bloodMoon;
    }

    public BloodMoonSettings bloodSettings() {
        return bloodSettings;
    }

    public HerobrineManager herobrine() {
        return herobrine;
    }

    public boolean isAutoEnabled() {
        return autoEnabled;
    }

    public void setAutoEnabled(boolean on) {
        autoEnabled = on;
        saveData();
    }

    public boolean isExempt(UUID id) {
        return exempt.contains(id);
    }

    /** @return true if the player is now exempt */
    public boolean toggleExempt(UUID id) {
        boolean nowExempt;
        if (exempt.remove(id)) {
            nowExempt = false;
        } else {
            exempt.add(id);
            nowExempt = true;
        }
        saveData();
        return nowExempt;
    }

    public String prefix() {
        return color(getConfig().getString("prefix", C.DEFAULT_PREFIX));
    }

    /** &-codes, &#RRGGBB and <gradient:#from:#to>text</gradient>. See C.java. */
    public static String color(String s) {
        return C.colorize(s);
    }

    public List<String> whisperLines() {
        return getConfig().getStringList("whisper.messages");
    }
}

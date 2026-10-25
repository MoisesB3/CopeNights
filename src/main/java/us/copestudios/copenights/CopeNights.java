package us.copestudios.copenights;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import us.copestudios.copecore.CopeCore;
import us.copestudios.copecore.config.ConfigFile;
import us.copestudios.copecore.player.Cooldowns;
import us.copestudios.copecore.scheduler.Tasks;
import us.copestudios.copecore.service.Services;
import us.copestudios.copecore.text.Messages;
import us.copestudios.copenights.api.NightsService;
import us.copestudios.copenights.api.NightsServiceImpl;
import us.copestudios.copenights.bloodmoon.BloodMoonListener;
import us.copestudios.copenights.bloodmoon.BloodMoonManager;
import us.copestudios.copenights.bloodmoon.BloodMoonSettings;
import us.copestudios.copenights.bloodmoon.DeathMessageListener;
import us.copestudios.copenights.command.NightsCommand;
import us.copestudios.copenights.scare.AutoScareTask;
import us.copestudios.copenights.scare.HerobrineManager;
import us.copestudios.copenights.scare.ScareListener;
import us.copestudios.copenights.scare.ScareManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cope Nights: jump scares, Herobrine, Blood Moons and hordes.
 *
 * <h2>What this class does</h2>
 * <p>It starts everything up in the right order and holds the shared pieces (config files, messages, managers)
 * so the other classes can reach them. The actual features live in:</p>
 * <ul>
 *   <li>{@code scare/}     - the 18 scares, Herobrine, and the auto-scare timer</li>
 *   <li>{@code bloodmoon/} - Blood Moon nights, hordes, the settings menu and death messages</li>
 *   <li>{@code command/}   - /coni</li>
 *   <li>{@code api/}       - events other plugins can listen to (Blood Moon start/end, hordes) and
 *                            {@link NightsService} for plugins that want to ask or do things</li>
 * </ul>
 *
 * <h2>What CopeCore provides</h2>
 * <p>Colors and every text ({@link Messages}), config loading with repair ({@link ConfigFile}), the command
 * framework, menus, chat prompts, cooldowns, version-safe particles/attributes, shared player states (so
 * players in a duel or in combat are never scared), and the PlaceholderAPI bridge.</p>
 */
public final class CopeNights extends JavaPlugin {

    /** CopeCore API this version needs (Particles, Attributes and command fallbacks arrived in API 2). */
    private static final int REQUIRED_CORE_API = 2;

    private ConfigFile config;
    private ConfigFile data;
    private Messages messages;

    private NamespacedKey scareEntityKey;
    private HerobrineManager herobrine;
    private ScareManager scares;
    private BloodMoonSettings bloodSettings;
    private BloodMoonManager bloodMoon;
    private BukkitTask autoTask;

    /** Per-player cooldown between automatic scares. */
    private final Cooldowns autoCooldowns = new Cooldowns();

    /** Saved in data.yml: is auto mode on, and who is exempt. */
    private boolean autoEnabled;
    private final Set<UUID> exempt = new HashSet<>();

    // =====================================================================
    //  Startup / shutdown
    // =====================================================================

    @Override
    public void onEnable() {
        if (!CopeCore.requireApi(this, REQUIRED_CORE_API)) {
            return; // CopeCore printed what's wrong and disabled us
        }

        // 1) Files. ConfigFile creates them, adds new settings, and repairs broken ones.
        config = new ConfigFile(this, "config.yml");
        data = new ConfigFile(this, "data.yml", false);
        messages = CopeCore.messages(this);
        bloodSettings = new BloodMoonSettings(this);

        // 2) Upgrading from an older Cope Nights: move texts from config.yml / bloodmoon.yml into messages.yml.
        new LegacyMigration(this, data).run();

        loadData();
        scareEntityKey = new NamespacedKey(this, "scare_entity");

        // 3) Features.
        herobrine = new HerobrineManager(this);
        herobrine.loadSkin();
        scares = new ScareManager(this, herobrine);
        bloodMoon = new BloodMoonManager(this, bloodSettings);
        bloodMoon.startTask();

        // 4) Command, listeners, placeholders.
        new NightsCommand(this).register();
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ScareListener(this), this);
        pluginManager.registerEvents(new BloodMoonListener(this, bloodMoon, bloodSettings), this);
        pluginManager.registerEvents(new DeathMessageListener(this), this);
        NightsPlaceholders.register(this);

        // 5) Let other plugins use Cope Nights: Services.find(NightsService.class)
        Services.provide(this, NightsService.class, new NightsServiceImpl(this));

        restartAutoTask();
        getLogger().info("Cope Nights enabled. Auto scares: " + (autoEnabled ? "ON" : "OFF"));
    }

    @Override
    public void onDisable() {
        if (autoTask != null) {
            autoTask.cancel();
        }
        if (herobrine != null) {
            herobrine.removeAll();
        }
        if (bloodMoon != null) {
            bloodMoon.shutdown();
        }
        if (data != null) {
            saveData();
        }
    }

    /** /coni reload: every file is read again and the timers restart with the new settings. */
    public void reload() {
        config.reload();
        messages.reload();
        bloodSettings.reload();
        herobrine.loadSkin();
        restartAutoTask();
    }

    /** (Re)starts the auto-scare roll timer, using auto.check-interval-seconds (minimum 5). */
    public void restartAutoTask() {
        if (autoTask != null) {
            autoTask.cancel();
        }
        long seconds = Math.max(5, config.get().getLong("auto.check-interval-seconds", 30));
        autoTask = Tasks.timer(this, seconds * 20L, seconds * 20L, new AutoScareTask(this));
    }

    // =====================================================================
    //  data.yml (auto mode + exempt players)
    // =====================================================================

    private void loadData() {
        autoEnabled = data.get().getBoolean("auto-enabled", false);
        exempt.clear();
        for (String id : data.get().getStringList("exempt")) {
            try {
                exempt.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                // a broken line in data.yml: skip it
            }
        }
    }

    public void saveData() {
        data.get().set("auto-enabled", autoEnabled);
        data.get().set("exempt", exempt.stream().map(UUID::toString).toList());
        data.save();
    }

    // =====================================================================
    //  State used by several classes
    // =====================================================================

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
        boolean nowExempt = !exempt.remove(id);
        if (nowExempt) {
            exempt.add(id);
        }
        saveData();
        return nowExempt;
    }

    /**
     * True if the player has one of the CopeCore states listed in config.yml "auto.skip-states"
     * (combat, in-duel, in-arena, boss-fight...). Such players are never scared.
     */
    public boolean isBusy(Player player) {
        List<String> skip = config.get().getStringList("auto.skip-states");
        return !skip.isEmpty() && CopeCore.states().hasAny(player, skip.toArray(new String[0]));
    }

    // =====================================================================
    //  Getters
    // =====================================================================

    /** config.yml */
    public ConfigFile settings() {
        return config;
    }

    /** messages.yml */
    public Messages messages() {
        return messages;
    }

    /** Hidden tag put on every fake entity (Herobrine bodies, fake TNT) so they can be found and cleaned up. */
    public NamespacedKey scareEntityKey() {
        return scareEntityKey;
    }

    public ScareManager scares() {
        return scares;
    }

    public HerobrineManager herobrine() {
        return herobrine;
    }

    public BloodMoonManager bloodMoon() {
        return bloodMoon;
    }

    public BloodMoonSettings bloodSettings() {
        return bloodSettings;
    }

    public Cooldowns autoCooldowns() {
        return autoCooldowns;
    }

    /** "ON"/"OFF" text from messages.yml (common.on / common.off). */
    public String onOff(boolean on) {
        return messages.format(on ? "common.on" : "common.off");
    }
}

package us.copestudios.copenights.bloodmoon;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import us.copestudios.copecore.CopeCore;
import us.copestudios.copecore.scheduler.Tasks;
import us.copestudios.copecore.text.Text;
import us.copestudios.copecore.util.Attributes;
import us.copestudios.copecore.util.Particles;
import us.copestudios.copenights.CopeNights;
import us.copestudios.copenights.ScareType;
import us.copestudios.copenights.api.BloodMoonEndEvent;
import us.copestudios.copenights.api.BloodMoonStartEvent;
import us.copestudios.copenights.api.HordeSpawnEvent;
import us.copestudios.copenights.bloodmoon.BloodMoonSettings.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs Blood Moon nights: when they start and end, mob buffs, hordes, the boss bar, the red tint,
 * Blood Moon scares and survivor rewards.
 *
 * <h2>Timeline of one Blood Moon</h2>
 * <ol>
 *   <li>Every "every-days" Minecraft days, at sunset (time 11000) players get a warning.</li>
 *   <li>At 12500 it starts: {@link BloodMoonStartEvent} (other plugins may cancel it), title, boss bar,
 *       red tint, thunder, and a moonrise scare a few seconds later.</li>
 *   <li>All night: natural hostile spawns are multiplied and buffed ({@link BloodMoonListener}), horde waves come
 *       every "horde-interval-seconds", and random Blood Moon scares roll every "scare-interval-seconds".</li>
 *   <li>At dawn (23000) it ends: mobs vanish, survivors get rewards, {@link BloodMoonEndEvent} fires.</li>
 * </ol>
 *
 * <h2>Version notes</h2>
 * <p>Giants use the "scale" attribute, which only exists on Minecraft 1.20.5+. On older servers CopeCore's
 * {@link Attributes} simply reports it as missing and mobs keep their normal size (everything else works).</p>
 */
public final class BloodMoonManager {

    /** Just after sunset. */
    private static final long NIGHT_START = 12500;
    /** Dawn. */
    private static final long NIGHT_END = 23000;
    /** When the sunset warning is sent. */
    private static final long WARNING_TIME = 11000;

    private final CopeNights plugin;
    private final BloodMoonSettings settings;
    /** Tag on every Blood Moon mob. */
    private final NamespacedKey mobKey;
    /** Extra tag on horde mobs. */
    private final NamespacedKey hordeKey;

    /** world UUID -> the Blood Moon running there */
    private final Map<UUID, ActiveMoon> active = new HashMap<>();
    /** world UUID -> Minecraft day a Blood Moon last started (so one night can't start twice) */
    private final Map<UUID, Long> lastStartedDay = new HashMap<>();
    /** world UUID -> Minecraft day the sunset warning was sent */
    private final Map<UUID, Long> warnedDay = new HashMap<>();
    private BukkitTask task;

    public BloodMoonManager(CopeNights plugin, BloodMoonSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.mobKey = new NamespacedKey(plugin, "bloodmoon_mob");
        this.hordeKey = new NamespacedKey(plugin, "horde_mob");
    }

    /** Starts the once-a-second check. */
    public void startTask() {
        task = Tasks.timer(plugin, 40L, 20L, this::tick);
    }

    /** Ends every Blood Moon quietly (server stop / plugin disable). */
    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (UUID id : new ArrayList<>(active.keySet())) {
            World world = Bukkit.getWorld(id);
            if (world != null) {
                finish(world, false, false);
            }
        }
        active.clear();
    }

    // =====================================================================
    //  Schedule (runs every second)
    // =====================================================================

    private void tick() {
        for (World world : eligibleWorlds()) {
            long time = world.getTime();
            long day = world.getFullTime() / 24000L;
            ActiveMoon moon = active.get(world.getUID());

            if (moon != null) {
                if (time >= NIGHT_END || time < NIGHT_START) {
                    finish(world, true, true); // dawn
                } else {
                    updateBar(moon, time);
                    maybeAutoHorde(world, moon);
                    maybeScares(world, moon);
                }
                continue;
            }

            if (!settings.getBool(Setting.ENABLED) || !isBloodDay(day)) {
                continue;
            }

            // Sunset warning (once per Blood Moon day).
            if (time >= WARNING_TIME && time < NIGHT_START && !Long.valueOf(day).equals(warnedDay.get(world.getUID()))) {
                warnedDay.put(world.getUID(), day);
                if (settings.getBool(Setting.ANNOUNCE)) {
                    for (Player player : world.getPlayers()) {
                        plugin.messages().send(player, "bloodmoon.warning");
                        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 1f, 0.5f);
                    }
                }
            }

            if (time >= NIGHT_START && time < NIGHT_END && !Long.valueOf(day).equals(lastStartedDay.get(world.getUID()))) {
                start(world, false);
            }
        }
    }

    /** True if this Minecraft day is a Blood Moon day (day 7, 14, 21... with every-days 7). */
    public boolean isBloodDay(long day) {
        int every = settings.getInt(Setting.EVERY_DAYS);
        return every > 0 && day > 0 && day % every == 0;
    }

    /** Days until the next Blood Moon night in this world (0 = tonight). */
    public long daysUntilNext(World world) {
        int every = Math.max(1, settings.getInt(Setting.EVERY_DAYS));
        long day = world.getFullTime() / 24000L;
        long remainder = day % every;
        if (day > 0 && remainder == 0) {
            boolean alreadyDone = Long.valueOf(day).equals(lastStartedDay.get(world.getUID())) && !isActive(world);
            return alreadyDone || world.getTime() >= NIGHT_END ? every : 0;
        }
        return every - remainder;
    }

    /** Worlds listed in "worlds:" or, if that's empty, every overworld-type world. */
    public List<World> eligibleWorlds() {
        List<String> names = settings.worlds();
        List<World> out = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            boolean eligible = names.isEmpty()
                    ? world.getEnvironment() == World.Environment.NORMAL
                    : names.contains(world.getName());
            if (eligible) {
                out.add(world);
            }
        }
        return out;
    }

    // =====================================================================
    //  Start / end
    // =====================================================================

    public boolean isActive(World world) {
        return world != null && active.containsKey(world.getUID());
    }

    public Set<World> activeWorlds() {
        Set<World> out = new HashSet<>();
        for (UUID id : active.keySet()) {
            World world = Bukkit.getWorld(id);
            if (world != null) {
                out.add(world);
            }
        }
        return out;
    }

    /**
     * Starts a Blood Moon by hand, skipping to nightfall if it's daytime.
     *
     * @return false if another plugin cancelled it
     */
    public boolean forceStart(World world) {
        if (isActive(world)) {
            return true;
        }
        if (world.getTime() < NIGHT_START || world.getTime() >= NIGHT_END) {
            world.setTime(13000);
        }
        return start(world, true);
    }

    private boolean start(World world, boolean forced) {
        if (isActive(world)) {
            return true;
        }
        // Remember the day even when cancelled, so a cancelled night isn't retried every second.
        lastStartedDay.put(world.getUID(), world.getFullTime() / 24000L);

        BloodMoonStartEvent event = new BloodMoonStartEvent(world, forced);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        ActiveMoon moon = new ActiveMoon();
        active.put(world.getUID(), moon);

        if (settings.getBool(Setting.BOSS_BAR)) {
            moon.bar = Bukkit.createBossBar(plugin.messages().legacy("bloodmoon.bossbar", "kills", "0", "wave", "0"),
                    BarColor.RED, BarStyle.SEGMENTED_10);
        }
        if (settings.getBool(Setting.THUNDER) && !world.isThundering()) {
            moon.changedWeather = true;
            world.setStorm(true);
            world.setThundering(true);
            world.setWeatherDuration(12000);
            world.setThunderDuration(12000);
        }

        for (Player player : world.getPlayers()) {
            applyPlayerEffects(player);
            if (settings.getBool(Setting.ANNOUNCE)) {
                plugin.messages().title(player, "bloodmoon.start-title", "bloodmoon.start-subtitle", 10, 60, 20);
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.6f, 0.6f);
            }
        }

        // Moonrise scare for everyone, a few seconds after the title.
        ScareType rise = ScareType.parse(settings.moonriseScare());
        if (rise != null && settings.getBool(Setting.SCARES)) {
            Tasks.later(plugin, 80L, () -> {
                for (Player player : world.getPlayers()) {
                    if (canScare(player)) {
                        plugin.scares().perform(player, rise);
                    }
                }
            });
        }
        plugin.getLogger().info("Blood moon started in " + world.getName());
        return true;
    }

    /** Ends the Blood Moon in this world (command / menu). Survivors get their rewards when announce is true. */
    public void end(World world, boolean announce) {
        finish(world, announce, false);
    }

    private void finish(World world, boolean announce, boolean natural) {
        ActiveMoon moon = active.remove(world.getUID());
        if (moon == null) {
            return;
        }

        for (Player player : world.getPlayers()) {
            clearPlayerEffects(player, moon);
        }
        if (moon.bar != null) {
            moon.bar.removeAll();
        }
        if (moon.changedWeather) {
            world.setThundering(false);
            world.setStorm(false);
        }

        if (settings.getBool(Setting.DESPAWN_AT_END)) {
            for (UUID id : moon.mobs) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.isValid()) {
                    Particles.spawn(world, "LARGE_SMOKE", entity.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.01);
                    entity.remove();
                }
            }
        }

        List<Player> survivors = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (!moon.died.contains(player.getUniqueId())
                    && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)) {
                survivors.add(player);
            }
        }

        if (announce) {
            boolean rewards = settings.getBool(Setting.SURVIVOR_REWARDS);
            for (Player player : world.getPlayers()) {
                if (settings.getBool(Setting.ANNOUNCE)) {
                    plugin.messages().send(player, "bloodmoon.end");
                }
            }
            if (rewards) {
                for (Player player : survivors) {
                    plugin.messages().title(player, "bloodmoon.survived-title", "bloodmoon.survived-subtitle", 10, 50, 20);
                    for (String command : settings.rewardCommands()) {
                        // <player> and the old %player% both work
                        String line = Text.replace(command, "player", player.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(line));
                    }
                }
            }
        }

        Bukkit.getPluginManager().callEvent(new BloodMoonEndEvent(world, moon.kills, moon.wave, survivors, natural));
        plugin.getLogger().info("Blood moon ended in " + world.getName() + " (" + moon.kills + " kills)");
    }

    private void updateBar(ActiveMoon moon, long time) {
        moon.mobs.removeIf(id -> {
            Entity entity = Bukkit.getEntity(id);
            return entity == null || !entity.isValid();
        });
        if (moon.bar == null) {
            return;
        }
        double progress = (double) (NIGHT_END - time) / (NIGHT_END - NIGHT_START);
        moon.bar.setProgress(Math.max(0, Math.min(1, progress)));
        moon.bar.setTitle(plugin.messages().legacy("bloodmoon.bossbar",
                "kills", String.valueOf(moon.kills), "wave", String.valueOf(moon.wave)));
    }

    // =====================================================================
    //  Blood Moon scares
    // =====================================================================

    private void maybeScares(World world, ActiveMoon moon) {
        if (!settings.getBool(Setting.SCARES)) {
            return;
        }
        long now = System.currentTimeMillis();
        long interval = settings.getInt(Setting.SCARE_INTERVAL) * 1000L;
        if (moon.nextScareAt == 0) {
            moon.nextScareAt = now + interval;
        }
        if (now < moon.nextScareAt) {
            return;
        }
        moon.nextScareAt = now + interval;

        double chance = settings.getDouble(Setting.SCARE_CHANCE);
        for (Player player : world.getPlayers()) {
            if (canScare(player) && ThreadLocalRandom.current().nextDouble() < chance) {
                plugin.scares().perform(player, plugin.scares().pickWeighted(settings.scareWeights()));
            }
        }
    }

    /** Survival/adventure, not exempt, not busy (duel, combat...), and not already being haunted. */
    private boolean canScare(Player player) {
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return false;
        }
        if (player.hasPermission("copenights.exempt") || plugin.isExempt(player.getUniqueId())) {
            return false;
        }
        return !plugin.isBusy(player) && !plugin.herobrine().has(player.getUniqueId());
    }

    /** A random scare from "horde-warning-scares", or null if the list is empty. */
    private ScareType hordeWarning() {
        List<String> list = settings.hordeWarningScares();
        if (list.isEmpty()) {
            return null;
        }
        return ScareType.parse(list.get(ThreadLocalRandom.current().nextInt(list.size())));
    }

    // =====================================================================
    //  Players
    // =====================================================================

    /** Boss bar + red tint for a player in a Blood Moon world (on start, join, respawn, world change). */
    public void applyPlayerEffects(Player player) {
        ActiveMoon moon = active.get(player.getWorld().getUID());
        if (moon == null) {
            return;
        }
        if (moon.bar != null) {
            moon.bar.addPlayer(player);
        }
        if (settings.getBool(Setting.RED_SKY)) {
            // A personal world border with a warning zone bigger than the border = red vignette everywhere.
            WorldBorder real = player.getWorld().getWorldBorder();
            WorldBorder tint = Bukkit.createWorldBorder();
            tint.setCenter(real.getCenter());
            tint.setSize(real.getSize());
            tint.setWarningDistance((int) Math.min(Integer.MAX_VALUE, real.getSize()));
            player.setWorldBorder(tint);
        }
    }

    /** Removes the boss bar and red tint. */
    public void clearPlayerEffects(Player player, ActiveMoon moonOrNull) {
        if (moonOrNull != null && moonOrNull.bar != null) {
            moonOrNull.bar.removePlayer(player);
        }
        for (ActiveMoon moon : active.values()) {
            if (moon.bar != null) {
                moon.bar.removePlayer(player);
            }
        }
        player.setWorldBorder(null); // back to the real border
    }

    public void markDied(Player player) {
        ActiveMoon moon = active.get(player.getWorld().getUID());
        if (moon != null) {
            moon.died.add(player.getUniqueId());
        }
    }

    // =====================================================================
    //  Mobs
    // =====================================================================

    public boolean isHordeMob(Entity entity) {
        return entity.getPersistentDataContainer().has(hordeKey, PersistentDataType.BYTE);
    }

    public boolean isBloodMob(Entity entity) {
        return entity.getPersistentDataContainer().has(mobKey, PersistentDataType.BYTE);
    }

    /** True when there's no Blood Moon here, or the world reached "max-mobs". */
    public boolean atMobCap(World world) {
        ActiveMoon moon = active.get(world.getUID());
        return moon == null || moon.mobs.size() >= settings.getInt(Setting.MAX_MOBS);
    }

    public void countKill(World world) {
        ActiveMoon moon = active.get(world.getUID());
        if (moon != null) {
            moon.kills++;
        }
    }

    /** A random enabled special mob, or null if none of the configured ones exist on this server. */
    public EntityType randomSpecial() {
        List<EntityType> types = parseMobs(settings.specialMobs());
        return types.isEmpty() ? null : types.get(ThreadLocalRandom.current().nextInt(types.size()));
    }

    /** Spawns a buffed Blood Moon mob (extra spawns and special swaps). */
    public void spawnBloodMob(Location at, EntityType type) {
        if (at.getWorld() == null || atMobCap(at.getWorld())) {
            return;
        }
        Entity entity = at.getWorld().spawnEntity(at, type); // reason CUSTOM, so it can't trigger the multiplier again
        if (entity instanceof LivingEntity living) {
            buff(living);
        }
    }

    /** Tags and buffs a mob for the current Blood Moon (health, speed, sometimes giant). */
    public void buff(LivingEntity entity) {
        ActiveMoon moon = active.get(entity.getWorld().getUID());
        entity.getPersistentDataContainer().set(mobKey, PersistentDataType.BYTE, (byte) 1);
        if (moon != null) {
            moon.mobs.add(entity.getUniqueId());
        }

        double health = settings.getDouble(Setting.HEALTH_MULTIPLIER);
        boolean giant = ThreadLocalRandom.current().nextDouble() < settings.getDouble(Setting.GIANT_CHANCE);
        // setBase returns false on 1.20-1.20.4 (no "scale" attribute), so giants just don't happen there.
        if (giant && Attributes.setBase(entity, "scale", settings.getDouble(Setting.GIANT_SCALE))) {
            health *= 1.5; // giants are tankier too
        }
        Attributes.multiplyBase(entity, "max_health", health);
        Attributes.multiplyBase(entity, "movement_speed", settings.getDouble(Setting.SPEED_MULTIPLIER));
        Attributes.fillHealth(entity);
    }

    // =====================================================================
    //  Hordes
    // =====================================================================

    /**
     * Waves: the first comes shortly after moonrise, then one every interval.
     * Each wave sends one horde per "horde-players-per-horde" online players (each on a different random player,
     * skipping busy ones), and hordes grow a little every wave.
     */
    private void maybeAutoHorde(World world, ActiveMoon moon) {
        if (!settings.getBool(Setting.HORDES)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (moon.nextWaveAt == 0) {
            moon.nextWaveAt = now + settings.getInt(Setting.HORDE_FIRST_DELAY) * 1000L;
        }
        if (now < moon.nextWaveAt) {
            return;
        }
        moon.nextWaveAt = now + settings.getInt(Setting.HORDE_INTERVAL) * 1000L;

        List<String> skipStates = settings.hordeSkipStates();
        String[] skip = skipStates.toArray(new String[0]);
        List<Player> targets = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            boolean playing = player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
            boolean busy = skip.length > 0 && CopeCore.states().hasAny(player, skip);
            if (playing && !busy) {
                targets.add(player);
            }
        }
        if (targets.isEmpty()) {
            return;
        }

        moon.wave++;
        int perHorde = Math.max(1, settings.getInt(Setting.HORDE_PLAYERS_PER));
        int hordes = (int) Math.ceil(Bukkit.getOnlinePlayers().size() / (double) perHorde);
        hordes = Math.max(1, Math.min(hordes, settings.getInt(Setting.HORDE_MAX_PER_WAVE)));
        hordes = Math.min(hordes, targets.size()); // never two hordes on the same player in one wave
        int size = waveSize(moon.wave);
        int wave = moon.wave;

        Collections.shuffle(targets);
        for (int i = 0; i < hordes; i++) {
            Player victim = targets.get(i);
            ScareType warning = settings.getBool(Setting.SCARES) ? hordeWarning() : null;
            if (warning != null && canScare(victim)) {
                // A warning scare first... then the horde hits 4 seconds later.
                plugin.scares().perform(victim, warning);
                Tasks.later(plugin, 80L, () -> {
                    if (victim.isOnline() && isActive(victim.getWorld())) {
                        spawnHorde(victim, size, wave);
                    }
                });
            } else {
                spawnHorde(victim, size, wave);
            }
        }

        if (settings.getBool(Setting.ANNOUNCE)) {
            for (Player player : world.getPlayers()) {
                plugin.messages().send(player, "bloodmoon.wave-announce",
                        "wave", String.valueOf(wave), "hordes", String.valueOf(hordes), "size", String.valueOf(size));
            }
        }
    }

    /** Start size + growth per wave, capped at "horde-max-size". */
    public int waveSize(int wave) {
        int size = settings.getInt(Setting.HORDE_SIZE) + settings.getInt(Setting.HORDE_GROWTH) * Math.max(0, wave - 1);
        return Math.min(size, settings.getInt(Setting.HORDE_MAX_SIZE));
    }

    /** Current wave in this world (0 without a Blood Moon). */
    public int wave(World world) {
        ActiveMoon moon = active.get(world.getUID());
        return moon == null ? 0 : moon.wave;
    }

    /**
     * Surrounds a player with a horde. Works with or without an active Blood Moon.
     * Mobs appear in a ring around the player a few at a time, already hunting them.
     *
     * @return how many mobs will spawn (0 if another plugin cancelled it or there's no room)
     */
    public int spawnHorde(Player target, int requestedSize, int wave) {
        World world = target.getWorld();
        HordeSpawnEvent event = new HordeSpawnEvent(target, Math.max(0, Math.min(requestedSize, 100)), wave);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return 0;
        }
        int size = event.getSize();

        ActiveMoon moon = active.get(world.getUID());
        if (moon != null) {
            int room = settings.getInt(Setting.MAX_MOBS) - moon.mobs.size();
            size = Math.min(size, Math.max(0, room));
        }
        boolean captain = settings.getBool(Setting.HORDE_CAPTAIN);
        if (size == 0 && !captain) {
            return 0;
        }

        if (settings.getBool(Setting.ANNOUNCE)) {
            plugin.messages().title(target, "bloodmoon.horde-title", "bloodmoon.horde-subtitle", 5, 40, 10,
                    "wave", String.valueOf(wave));
        }
        for (Player near : world.getPlayers()) {
            if (near.getLocation().distanceSquared(target.getLocation()) < 80 * 80) {
                near.playSound(target.getLocation(), Sound.EVENT_RAID_HORN, SoundCategory.HOSTILE, 64f, 0.8f);
            }
        }

        List<EntityType> pool = parseMobs(settings.hordeMobs());
        if (pool.isEmpty()) {
            pool.add(EntityType.ZOMBIE);
        }
        int radius = settings.getInt(Setting.HORDE_RADIUS);
        final int total = size;
        final List<EntityType> mobs = pool;

        new BukkitRunnable() {
            int spawned = 0;
            boolean captainDone = !captain;

            @Override
            public void run() {
                if (!target.isOnline() || !target.getWorld().equals(world)) {
                    cancel();
                    return;
                }
                // 3 per tick keeps big hordes from causing a lag spike.
                for (int i = 0; i < 3; i++) {
                    if (!captainDone) {
                        spawnHordeMob(target, radius, EntityType.ZOMBIE, true);
                        captainDone = true;
                        continue;
                    }
                    if (spawned >= total) {
                        cancel();
                        return;
                    }
                    EntityType type = mobs.get(ThreadLocalRandom.current().nextInt(mobs.size()));
                    if (ThreadLocalRandom.current().nextDouble() < settings.getDouble(Setting.SPECIAL_CHANCE)) {
                        EntityType special = randomSpecial();
                        if (special != null) {
                            type = special;
                        }
                    }
                    spawnHordeMob(target, radius, type, false);
                    spawned++;
                }
            }
        }.runTaskTimer(plugin, 10L, 2L);

        // Boss roll: spawns once the horde has had a moment to arrive.
        if (ThreadLocalRandom.current().nextDouble() < settings.getDouble(Setting.HORDE_BOSS_CHANCE)) {
            Tasks.later(plugin, 40L, () -> spawnHordeBoss(target));
        }
        return total + (captain ? 1 : 0);
    }

    /**
     * Runs the "horde-boss-command" (Cope Bosses by default) for a random boss at the horde's player.
     * Set "horde-boss-chance" to 0 if you don't use a boss plugin.
     */
    private void spawnHordeBoss(Player target) {
        if (!target.isOnline()) {
            return;
        }
        List<String> bosses = settings.hordeBosses();
        if (bosses.isEmpty()) {
            return;
        }
        String boss = bosses.get(ThreadLocalRandom.current().nextInt(bosses.size()));
        Location at = target.getLocation();
        String command = stripSlash(Text.replace(settings.hordeBossCommand(),
                "boss", boss,
                "player", target.getName(),
                "world", at.getWorld() == null ? "" : at.getWorld().getName(),
                "x", String.valueOf(at.getBlockX()),
                "y", String.valueOf(at.getBlockY()),
                "z", String.valueOf(at.getBlockZ())));

        boolean ok;
        if (settings.hordeBossRunAsPlayer()) {
            // Run as the player so the boss spawns where they stand, with the boss plugin's
            // permission for this one command only.
            PermissionAttachment attachment = target.addAttachment(plugin);
            for (String permission : settings.hordeBossPermissions()) {
                attachment.setPermission(permission, true);
            }
            try {
                ok = target.performCommand(command);
            } finally {
                target.removeAttachment(attachment);
            }
        } else {
            ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        if (!ok) {
            plugin.getLogger().warning("Horde boss command failed: /" + command);
            return;
        }

        for (Player player : target.getWorld().getPlayers()) {
            plugin.messages().send(player, "bloodmoon.boss-announce", "boss", prettyName(boss), "player", target.getName());
            if (player.getLocation().distanceSquared(at) < 100 * 100) {
                player.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 3f, 0.7f);
            }
        }
    }

    private void spawnHordeMob(Player target, int radius, EntityType type, boolean isCaptain) {
        Location spot = ringSpot(target, radius);
        if (spot == null || spot.getWorld() == null) {
            return;
        }
        World world = spot.getWorld();
        Entity entity = world.spawnEntity(spot, type);
        Particles.spawn(world, "LARGE_SMOKE", spot.clone().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.02);
        Particles.spawn(world, "SOUL_FIRE_FLAME", spot, 6, 0.3, 0.1, 0.3, 0.01);

        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        buff(living);
        living.getPersistentDataContainer().set(hordeKey, PersistentDataType.BYTE, (byte) 1);
        living.setRemoveWhenFarAway(true);

        if (isCaptain) {
            Attributes.setBase(living, "scale", Math.max(1.5, settings.getDouble(Setting.GIANT_SCALE))); // 1.20.5+
            Attributes.multiplyBase(living, "max_health", 2.0);
            Attributes.fillHealth(living);
            living.setCustomName(plugin.messages().legacy("bloodmoon.horde-captain"));
            living.setCustomNameVisible(true);
            living.setGlowing(true);
            EntityEquipment equipment = living.getEquipment();
            if (equipment != null) {
                equipment.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                equipment.setItemInMainHand(new ItemStack(Material.IRON_AXE));
                equipment.setHelmetDropChance(0f);
                equipment.setItemInMainHandDropChance(0.25f);
            }
        }
        if (living instanceof Mob mob) {
            mob.setTarget(target);
        }
    }

    /**
     * Turns names from the config into mob types, skipping ones that don't exist on this server
     * (BOGGED and BREEZE need 1.21) or can't be spawned.
     */
    private static List<EntityType> parseMobs(List<String> names) {
        List<EntityType> out = new ArrayList<>();
        for (String name : names) {
            EntityType type = mobType(name);
            if (type != null) {
                out.add(type);
            }
        }
        return out;
    }

    /** The spawnable living mob type with this name, or null. */
    public static EntityType mobType(String name) {
        if (name == null) {
            return null;
        }
        try {
            EntityType type = EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
            return type.isSpawnable() && type.isAlive() ? type : null;
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** A random standable spot in a ring around the player. Falls back to closer spots. */
    private static Location ringSpot(Player player, int radius) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location base = player.getLocation();
        for (int attempt = 0; attempt < 12; attempt++) {
            double distance = attempt < 8
                    ? radius * random.nextDouble(0.8, 1.2)
                    : random.nextDouble(3, Math.max(4, radius / 2.0));
            double angle = random.nextDouble(Math.PI * 2);
            Location spot = standable(base.getWorld(),
                    base.getX() + Math.cos(angle) * distance, base.getY(), base.getZ() + Math.sin(angle) * distance);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** Solid floor with two free (non-liquid) blocks above, searched from 6 above to 8 below. */
    private static Location standable(World world, double x, double y, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int blockY = (int) Math.floor(y);
        if (world == null || !world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return null;
        }
        for (int dy = 6; dy >= -8; dy--) {
            int feetY = blockY + dy;
            if (feetY <= world.getMinHeight() || feetY + 2 >= world.getMaxHeight()) {
                continue;
            }
            Block floor = world.getBlockAt(blockX, feetY - 1, blockZ);
            Block feet = world.getBlockAt(blockX, feetY, blockZ);
            Block head = world.getBlockAt(blockX, feetY + 1, blockZ);
            if (floor.getType().isSolid() && feet.isPassable() && head.isPassable()
                    && !feet.isLiquid() && !head.isLiquid()) {
                return new Location(world, blockX + 0.5, feetY, blockZ + 0.5);
            }
        }
        return null;
    }

    /** "frost-colossus" -> "Frost Colossus" */
    public static String prettyName(String id) {
        StringBuilder out = new StringBuilder();
        for (String part : id.toLowerCase(Locale.ROOT).split("[-_ ]")) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    /** State of one running Blood Moon. */
    public static final class ActiveMoon {
        long nextWaveAt;
        long nextScareAt;
        int wave;
        BossBar bar;
        boolean changedWeather;
        int kills;
        final Set<UUID> mobs = new HashSet<>();
        final Set<UUID> died = new HashSet<>();
    }
}

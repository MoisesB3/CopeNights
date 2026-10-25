package us.copestudios.copenights;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

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

import us.copestudios.copenights.BloodMoonSettings.Setting;

/** Runs blood moon nights: scheduling, mob buffs, boss bar, red tint, rewards. */
public class BloodMoonManager {

    private static final long NIGHT_START = 12500; // just after sunset
    private static final long NIGHT_END = 23000;   // dawn

    private final CopeNights plugin;
    private final BloodMoonSettings settings;
    private final NamespacedKey mobKey;
    private final NamespacedKey hordeKey;

    private final Map<UUID, ActiveMoon> active = new HashMap<>();   // world uid -> state
    private final Map<UUID, Long> lastStartedDay = new HashMap<>();
    private final Map<UUID, Long> warnedDay = new HashMap<>();
    private BukkitTask task;

    public BloodMoonManager(CopeNights plugin, BloodMoonSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.mobKey = new NamespacedKey(plugin, "bloodmoon_mob");
        this.hordeKey = new NamespacedKey(plugin, "horde_mob");
    }

    public void startTask() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (UUID id : new ArrayList<>(active.keySet())) {
            World w = Bukkit.getWorld(id);
            if (w != null) end(w, false);
        }
        active.clear();
    }

    // ------------------------------------------------------------------
    // Schedule
    // ------------------------------------------------------------------

    private void tick() {
        for (World w : eligibleWorlds()) {
            long time = w.getTime();
            long day = w.getFullTime() / 24000L;
            ActiveMoon moon = active.get(w.getUID());

            if (moon != null) {
                if (time >= NIGHT_END || time < NIGHT_START) {
                    end(w, true);
                } else {
                    updateBar(w, moon, time);
                    maybeAutoHorde(w, moon);
                    maybeScares(w, moon);
                }
                continue;
            }

            if (!settings.getBool(Setting.ENABLED) || !isBloodDay(day)) continue;

            // Sunset warning
            if (time >= 11000 && time < NIGHT_START && !Long.valueOf(day).equals(warnedDay.get(w.getUID()))) {
                warnedDay.put(w.getUID(), day);
                if (settings.getBool(Setting.ANNOUNCE)) {
                    for (Player p : w.getPlayers()) {
                        p.sendMessage(plugin.prefix() + settings.msg("warning"));
                        p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 1f, 0.5f);
                    }
                }
            }

            if (time >= NIGHT_START && time < NIGHT_END
                    && !Long.valueOf(day).equals(lastStartedDay.get(w.getUID()))) {
                start(w);
            }
        }
    }

    public boolean isBloodDay(long day) {
        int every = settings.getInt(Setting.EVERY_DAYS);
        return every > 0 && day > 0 && day % every == 0;
    }

    /** Days until the next blood moon night in this world (0 = tonight). */
    public long daysUntilNext(World w) {
        int every = Math.max(1, settings.getInt(Setting.EVERY_DAYS));
        long day = w.getFullTime() / 24000L;
        long rem = day % every;
        if (day > 0 && rem == 0) {
            boolean alreadyDone = Long.valueOf(day).equals(lastStartedDay.get(w.getUID())) && !isActive(w);
            return alreadyDone || w.getTime() >= NIGHT_END ? every : 0;
        }
        return every - rem;
    }

    public List<World> eligibleWorlds() {
        List<String> names = settings.worlds();
        List<World> out = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            if (names.isEmpty() ? w.getEnvironment() == World.Environment.NORMAL : names.contains(w.getName())) {
                out.add(w);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Start / end
    // ------------------------------------------------------------------

    public boolean isActive(World w) {
        return w != null && active.containsKey(w.getUID());
    }

    public Set<World> activeWorlds() {
        Set<World> out = new HashSet<>();
        for (UUID id : active.keySet()) {
            World w = Bukkit.getWorld(id);
            if (w != null) out.add(w);
        }
        return out;
    }

    /** Manual start: skips to nightfall so the blood moon can run. */
    public void forceStart(World w) {
        if (isActive(w)) return;
        if (w.getTime() < NIGHT_START || w.getTime() >= NIGHT_END) w.setTime(13000);
        start(w);
    }

    private void start(World w) {
        if (isActive(w)) return;
        lastStartedDay.put(w.getUID(), w.getFullTime() / 24000L);

        ActiveMoon moon = new ActiveMoon();
        active.put(w.getUID(), moon);

        if (settings.getBool(Setting.BOSS_BAR)) {
            moon.bar = Bukkit.createBossBar(settings.msg("bossbar", "%kills%", "0", "%wave%", "0"),
                    BarColor.RED, BarStyle.SEGMENTED_10);
        }
        if (settings.getBool(Setting.THUNDER) && !w.isThundering()) {
            moon.changedWeather = true;
            w.setStorm(true);
            w.setThundering(true);
            w.setWeatherDuration(12000);
            w.setThunderDuration(12000);
        }

        for (Player p : w.getPlayers()) {
            applyPlayerEffects(p);
            if (settings.getBool(Setting.ANNOUNCE)) {
                p.sendTitle(settings.msg("start-title"), settings.msg("start-subtitle"), 10, 60, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.6f, 0.6f);
            }
        }
        // Moonrise scare for everyone, a few seconds after the title
        ScareType rise = ScareType.parse(settings.startScare());
        if (rise != null && settings.getBool(Setting.SCARES)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player p : w.getPlayers()) if (canScare(p)) plugin.scares().perform(p, rise);
            }, 80L);
        }
        plugin.getLogger().info("Blood moon started in " + w.getName());
    }

    // ------------------------------------------------------------------
    // Blood moon scares
    // ------------------------------------------------------------------

    private void maybeScares(World w, ActiveMoon moon) {
        if (!settings.getBool(Setting.SCARES)) return;
        long now = System.currentTimeMillis();
        if (moon.nextScareAt == 0) moon.nextScareAt = now + settings.getInt(Setting.SCARE_INTERVAL) * 1000L;
        if (now < moon.nextScareAt) return;
        moon.nextScareAt = now + settings.getInt(Setting.SCARE_INTERVAL) * 1000L;

        double chance = settings.getDouble(Setting.SCARE_CHANCE);
        for (Player p : w.getPlayers()) {
            if (!canScare(p) || ThreadLocalRandom.current().nextDouble() >= chance) continue;
            plugin.scares().perform(p, plugin.scares().pickWeighted(settings.scareWeights()));
        }
    }

    /** Survival/adventure, not exempt, and not already mid-Herobrine. */
    private boolean canScare(Player p) {
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return false;
        if (p.hasPermission("copenights.exempt") || plugin.isExempt(p.getUniqueId())) return false;
        return !plugin.herobrine().has(p.getUniqueId());
    }

    /** A random warning scare from horde-warning-scares, or null if none set. */
    private ScareType hordeWarning() {
        List<String> list = settings.hordeWarningScares();
        if (list.isEmpty()) return null;
        return ScareType.parse(list.get(ThreadLocalRandom.current().nextInt(list.size())));
    }

    public void end(World w, boolean announce) {
        ActiveMoon moon = active.remove(w.getUID());
        if (moon == null) return;

        for (Player p : w.getPlayers()) clearPlayerEffects(p, moon);
        if (moon.bar != null) moon.bar.removeAll();

        if (moon.changedWeather) {
            w.setThundering(false);
            w.setStorm(false);
        }

        if (settings.getBool(Setting.DESPAWN_AT_END)) {
            for (UUID id : moon.mobs) {
                Entity e = Bukkit.getEntity(id);
                if (e != null && e.isValid()) {
                    w.spawnParticle(Particle.LARGE_SMOKE, e.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.01);
                    e.remove();
                }
            }
        }

        if (announce) {
            boolean rewards = settings.getBool(Setting.SURVIVOR_REWARDS);
            for (Player p : w.getPlayers()) {
                if (settings.getBool(Setting.ANNOUNCE)) p.sendMessage(plugin.prefix() + settings.msg("end"));
                if (rewards && !moon.died.contains(p.getUniqueId())) {
                    p.sendTitle(settings.msg("survived-title"), settings.msg("survived-subtitle"), 10, 50, 20);
                    for (String cmd : settings.rewardCommands()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
                    }
                }
            }
        }
        plugin.getLogger().info("Blood moon ended in " + w.getName() + " (" + moon.kills + " kills)");
    }

    private void updateBar(World w, ActiveMoon moon, long time) {
        if (moon.bar == null) return;
        double progress = (double) (NIGHT_END - time) / (NIGHT_END - NIGHT_START);
        moon.bar.setProgress(Math.max(0, Math.min(1, progress)));
        moon.bar.setTitle(settings.msg("bossbar", "%kills%", String.valueOf(moon.kills),
                "%wave%", String.valueOf(moon.wave)));
        moon.mobs.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid();
        });
    }

    // ------------------------------------------------------------------
    // Players
    // ------------------------------------------------------------------

    public void applyPlayerEffects(Player p) {
        ActiveMoon moon = active.get(p.getWorld().getUID());
        if (moon == null) return;
        if (moon.bar != null) moon.bar.addPlayer(p);
        if (settings.getBool(Setting.RED_SKY)) {
            WorldBorder real = p.getWorld().getWorldBorder();
            WorldBorder tint = Bukkit.createWorldBorder();
            tint.setCenter(real.getCenter());
            tint.setSize(real.getSize());
            // Warning zone bigger than the border = red vignette everywhere inside it
            tint.setWarningDistance((int) Math.min(Integer.MAX_VALUE, real.getSize()));
            p.setWorldBorder(tint);
        }
    }

    public void clearPlayerEffects(Player p, ActiveMoon moonOrNull) {
        if (moonOrNull != null && moonOrNull.bar != null) moonOrNull.bar.removePlayer(p);
        for (ActiveMoon m : active.values()) if (m.bar != null) m.bar.removePlayer(p);
        p.setWorldBorder(null); // back to the real border
    }

    public void markDied(Player p) {
        ActiveMoon moon = active.get(p.getWorld().getUID());
        if (moon != null) moon.died.add(p.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Mobs
    // ------------------------------------------------------------------

    public boolean isHordeMob(Entity e) {
        return e.getPersistentDataContainer().has(hordeKey, PersistentDataType.BYTE);
    }

    public boolean isBloodMob(Entity e) {
        return e.getPersistentDataContainer().has(mobKey, PersistentDataType.BYTE);
    }

    public boolean atMobCap(World w) {
        ActiveMoon moon = active.get(w.getUID());
        return moon == null || moon.mobs.size() >= settings.getInt(Setting.MAX_MOBS);
    }

    public void countKill(World w) {
        ActiveMoon moon = active.get(w.getUID());
        if (moon != null) moon.kills++;
    }

    /** Picks a random enabled special mob, or null if none are configured. */
    public EntityType randomSpecial() {
        List<EntityType> types = new ArrayList<>();
        for (String s : settings.specialMobs()) {
            try {
                EntityType t = EntityType.valueOf(s.toUpperCase(Locale.ROOT));
                if (t.isSpawnable() && t.isAlive()) types.add(t);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (types.isEmpty()) return null;
        return types.get(ThreadLocalRandom.current().nextInt(types.size()));
    }

    /** Spawns a buffed blood moon mob (used for extras and special swaps). */
    public void spawnBloodMob(Location loc, EntityType type) {
        if (loc.getWorld() == null || atMobCap(loc.getWorld())) return;
        Entity e = loc.getWorld().spawnEntity(loc, type); // reason CUSTOM, so it won't re-trigger the multiplier
        if (e instanceof LivingEntity le) buff(le);
    }

    /** Tags and buffs a mob for the current blood moon. */
    public void buff(LivingEntity e) {
        ActiveMoon moon = active.get(e.getWorld().getUID());
        e.getPersistentDataContainer().set(mobKey, PersistentDataType.BYTE, (byte) 1);
        if (moon != null) moon.mobs.add(e.getUniqueId());

        double hp = settings.getDouble(Setting.HEALTH_MULTIPLIER);
        boolean giant = ThreadLocalRandom.current().nextDouble() < settings.getDouble(Setting.GIANT_CHANCE);
        if (giant) {
            Attribute scale = attr("scale");
            if (scale != null && e.getAttribute(scale) != null) {
                e.getAttribute(scale).setBaseValue(settings.getDouble(Setting.GIANT_SCALE));
                hp *= 1.5; // giants are tankier too
            }
        }

        multiplyBase(e, attr("max_health"), hp);
        multiplyBase(e, attr("movement_speed"), settings.getDouble(Setting.SPEED_MULTIPLIER));
        AttributeInstance max = attr("max_health") == null ? null : e.getAttribute(attr("max_health"));
        if (max != null) e.setHealth(Math.max(1, max.getValue()));
    }

    private void multiplyBase(LivingEntity e, Attribute a, double mult) {
        if (a == null || mult == 1.0) return;
        AttributeInstance inst = e.getAttribute(a);
        if (inst != null) inst.setBaseValue(inst.getBaseValue() * mult);
    }

    /** Attribute lookup that works on 1.21.1 ("generic.scale") and 1.21.3+ ("scale"). */
    private static Attribute attr(String key) {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        if (a == null) a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + key));
        return a;
    }

    // ------------------------------------------------------------------
    // Hordes
    // ------------------------------------------------------------------

    /**
     * Waves: first one shortly after moonrise, then every interval.
     * Each wave sends 1 horde per X online players (different random players),
     * and hordes get a little bigger every wave.
     */
    private void maybeAutoHorde(World w, ActiveMoon moon) {
        if (!settings.getBool(Setting.HORDES)) return;
        long now = System.currentTimeMillis();
        if (moon.nextWaveAt == 0) {
            moon.nextWaveAt = now + settings.getInt(Setting.HORDE_FIRST_DELAY) * 1000L;
        }
        if (now < moon.nextWaveAt) return;
        moon.nextWaveAt = now + settings.getInt(Setting.HORDE_INTERVAL) * 1000L;

        List<Player> targets = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) targets.add(p);
        }
        if (targets.isEmpty()) return;

        moon.wave++;
        int perHorde = Math.max(1, settings.getInt(Setting.HORDE_PLAYERS_PER));
        int hordes = (int) Math.ceil(Bukkit.getOnlinePlayers().size() / (double) perHorde);
        hordes = Math.max(1, Math.min(hordes, settings.getInt(Setting.HORDE_MAX_PER_WAVE)));
        hordes = Math.min(hordes, targets.size()); // never two hordes on the same player in one wave
        int size = waveSize(moon.wave);

        Collections.shuffle(targets);
        int wave = moon.wave;
        for (int i = 0; i < hordes; i++) {
            Player victim = targets.get(i);
            ScareType warn = settings.getBool(Setting.SCARES) ? hordeWarning() : null;
            if (warn != null && canScare(victim)) {
                // Warning scare first... then the horde hits 4 seconds later
                plugin.scares().perform(victim, warn);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (victim.isOnline() && isActive(victim.getWorld())) spawnHorde(victim, size, wave);
                }, 80L);
            } else {
                spawnHorde(victim, size, wave);
            }
        }

        if (settings.getBool(Setting.ANNOUNCE)) {
            String msg = settings.msg("wave-announce",
                    "%wave%", String.valueOf(moon.wave),
                    "%hordes%", String.valueOf(hordes),
                    "%size%", String.valueOf(size));
            for (Player p : w.getPlayers()) p.sendMessage(plugin.prefix() + msg);
        }
    }

    /** start size + growth per wave, capped. */
    public int waveSize(int wave) {
        int size = settings.getInt(Setting.HORDE_SIZE) + settings.getInt(Setting.HORDE_GROWTH) * Math.max(0, wave - 1);
        return Math.min(size, settings.getInt(Setting.HORDE_MAX_SIZE));
    }

    public int wave(World w) {
        ActiveMoon moon = active.get(w.getUID());
        return moon == null ? 0 : moon.wave;
    }

    /**
     * Surrounds a player with a horde. Works with or without an active blood moon.
     * Mobs appear in a ring around the player a few at a time, already hunting them.
     * @return how many mobs will be spawned
     */
    public int spawnHorde(Player target, int size, int wave) {
        World w = target.getWorld();
        ActiveMoon moon = active.get(w.getUID());
        if (moon != null) {
            int room = settings.getInt(Setting.MAX_MOBS) - moon.mobs.size();
            size = Math.min(size, Math.max(0, room));
        }
        size = Math.max(0, Math.min(size, 100));
        boolean captain = settings.getBool(Setting.HORDE_CAPTAIN);
        if (size == 0 && !captain) return 0;

        if (settings.getBool(Setting.ANNOUNCE)) {
            target.sendTitle(settings.msg("horde-title"),
                    settings.msg("horde-subtitle", "%wave%", String.valueOf(wave)), 5, 40, 10);
        }
        for (Player near : w.getPlayers()) {
            if (near.getLocation().distanceSquared(target.getLocation()) < 80 * 80) {
                near.playSound(target.getLocation(), Sound.EVENT_RAID_HORN, SoundCategory.HOSTILE, 64f, 0.8f);
            }
        }

        List<EntityType> pool = hordePool();
        int radius = settings.getInt(Setting.HORDE_RADIUS);
        final int total = size;

        new BukkitRunnable() {
            int spawned = 0;
            boolean captainDone = !captain;

            @Override
            public void run() {
                if (!target.isOnline() || !target.getWorld().equals(w)) {
                    cancel();
                    return;
                }
                // 3 per tick keeps big hordes from causing a lag spike
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
                    EntityType type = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                    if (ThreadLocalRandom.current().nextDouble() < settings.getDouble(Setting.SPECIAL_CHANCE)) {
                        EntityType special = randomSpecial();
                        if (special != null) type = special;
                    }
                    spawnHordeMob(target, radius, type, false);
                    spawned++;
                }
            }
        }.runTaskTimer(plugin, 10L, 2L);

        // Boss roll - spawns once the horde has had a moment to arrive
        if (ThreadLocalRandom.current().nextDouble() < settings.getDouble(Setting.HORDE_BOSS_CHANCE)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> spawnHordeBoss(target), 40L);
        }
        return total + (captain ? 1 : 0);
    }

    /** Runs the Cope Bosses spawn command for a random boss at the horde's player. */
    private void spawnHordeBoss(Player target) {
        if (!target.isOnline()) return;
        List<String> bosses = settings.hordeBosses();
        if (bosses.isEmpty()) return;
        String boss = bosses.get(ThreadLocalRandom.current().nextInt(bosses.size()));
        Location l = target.getLocation();
        String cmd = settings.hordeBossCommand()
                .replace("%boss%", boss)
                .replace("%player%", target.getName())
                .replace("%world%", l.getWorld().getName())
                .replace("%x%", String.valueOf(l.getBlockX()))
                .replace("%y%", String.valueOf(l.getBlockY()))
                .replace("%z%", String.valueOf(l.getBlockZ()));
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        boolean ok;
        if (settings.hordeBossRunAsPlayer()) {
            // Run as the player so the boss spawns where they stand; grant the boss
            // plugin's permission for this one command only.
            PermissionAttachment att = target.addAttachment(plugin);
            for (String perm : settings.hordeBossPermissions()) att.setPermission(perm, true);
            try {
                ok = target.performCommand(cmd);
            } finally {
                target.removeAttachment(att);
            }
        } else {
            ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        if (!ok) {
            plugin.getLogger().warning("Horde boss command failed: /" + cmd);
            return;
        }

        String pretty = prettyBoss(boss);
        String msg = settings.msg("boss-announce", "%boss%", pretty, "%player%", target.getName());
        for (Player p : target.getWorld().getPlayers()) {
            p.sendMessage(plugin.prefix() + msg);
            if (p.getLocation().distanceSquared(l) < 100 * 100) {
                p.playSound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 3f, 0.7f);
            }
        }
    }

    private static String prettyBoss(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("[-_ ]")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private void spawnHordeMob(Player target, int radius, EntityType type, boolean isCaptain) {
        Location spot = ringSpot(target, radius);
        if (spot == null) return;
        World w = spot.getWorld();
        Entity e = w.spawnEntity(spot, type);
        w.spawnParticle(Particle.LARGE_SMOKE, spot.clone().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.02);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, spot, 6, 0.3, 0.1, 0.3, 0.01);

        if (!(e instanceof LivingEntity le)) return;
        buff(le);
        le.getPersistentDataContainer().set(hordeKey, PersistentDataType.BYTE, (byte) 1);
        le.setRemoveWhenFarAway(true);

        if (isCaptain) {
            Attribute scale = attr("scale");
            if (scale != null && le.getAttribute(scale) != null) {
                le.getAttribute(scale).setBaseValue(Math.max(1.5, settings.getDouble(Setting.GIANT_SCALE)));
            }
            multiplyBase(le, attr("max_health"), 2.0);
            AttributeInstance max = attr("max_health") == null ? null : le.getAttribute(attr("max_health"));
            if (max != null) le.setHealth(Math.max(1, max.getValue()));
            le.setCustomName(settings.msg("horde-captain"));
            le.setCustomNameVisible(true);
            le.setGlowing(true);
            if (le.getEquipment() != null) {
                le.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                le.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
                le.getEquipment().setHelmetDropChance(0f);
                le.getEquipment().setItemInMainHandDropChance(0.25f);
            }
        }
        if (le instanceof Mob mob) mob.setTarget(target);
    }

    private List<EntityType> hordePool() {
        List<EntityType> out = new ArrayList<>();
        for (String s : settings.hordeMobs()) {
            try {
                EntityType t = EntityType.valueOf(s.toUpperCase(Locale.ROOT));
                if (t.isSpawnable() && t.isAlive()) out.add(t);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (out.isEmpty()) out.add(EntityType.ZOMBIE);
        return out;
    }

    /** A random standable spot in a ring around the player. Falls back to closer spots. */
    private Location ringSpot(Player p, int radius) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location base = p.getLocation();
        for (int attempt = 0; attempt < 12; attempt++) {
            double dist = attempt < 8 ? radius * r.nextDouble(0.8, 1.2) : r.nextDouble(3, Math.max(4, radius / 2.0));
            double angle = r.nextDouble(Math.PI * 2);
            Location spot = standable(base.getWorld(),
                    base.getX() + Math.cos(angle) * dist, base.getY(), base.getZ() + Math.sin(angle) * dist);
            if (spot != null) return spot;
        }
        return null;
    }

    private Location standable(World w, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return null;
        for (int dy = 6; dy >= -8; dy--) {
            int fy = by + dy;
            if (fy <= w.getMinHeight() || fy + 2 >= w.getMaxHeight()) continue;
            Block floor = w.getBlockAt(bx, fy - 1, bz);
            Block feet = w.getBlockAt(bx, fy, bz);
            Block head = w.getBlockAt(bx, fy + 1, bz);
            if (floor.getType().isSolid() && feet.isPassable() && head.isPassable()
                    && !feet.isLiquid() && !head.isLiquid()) {
                return new Location(w, bx + 0.5, fy, bz + 0.5);
            }
        }
        return null;
    }

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

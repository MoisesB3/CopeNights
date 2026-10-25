package us.copestudios.copenights;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import us.copestudios.copenights.BloodMoonSettings.Setting;

public class BloodMoonListener implements Listener {

    private final CopeNights plugin;
    private final BloodMoonManager moon;
    private final BloodMoonSettings settings;

    public BloodMoonListener(CopeNights plugin, BloodMoonManager moon, BloodMoonSettings settings) {
        this.plugin = plugin;
        this.moon = moon;
        this.settings = settings;
    }

    // ---------------- spawns ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (!(e.getEntity() instanceof Enemy)) return;
        LivingEntity mob = e.getEntity();
        if (!moon.isActive(mob.getWorld()) || moon.atMobCap(mob.getWorld())) return;

        Location loc = e.getLocation().clone();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Swap for a special mob?
        if (r.nextDouble() < settings.getDouble(Setting.SPECIAL_CHANCE)) {
            EntityType special = moon.randomSpecial();
            if (special != null) {
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> moon.spawnBloodMob(loc, special));
                scheduleExtras(loc, mob.getType());
                return;
            }
        }

        moon.buff(mob);
        scheduleExtras(loc, mob.getType());
    }

    /** spawn-multiplier 2.0 = 1 extra, 2.5 = 1 extra + 50% chance of another. */
    private void scheduleExtras(Location loc, EntityType type) {
        double extra = settings.getDouble(Setting.SPAWN_MULTIPLIER) - 1.0;
        if (extra <= 0) return;
        int count = (int) Math.floor(extra);
        if (ThreadLocalRandom.current().nextDouble() < extra - count) count++;
        if (count <= 0) return;

        final int n = count;
        // Next tick: spawning inside a spawn event can upset the server's spawner.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int i = 0; i < n; i++) {
                EntityType t = type;
                if (ThreadLocalRandom.current().nextDouble() < settings.getDouble(Setting.SPECIAL_CHANCE)) {
                    EntityType special = moon.randomSpecial();
                    if (special != null) t = special;
                }
                moon.spawnBloodMob(loc, t);
            }
        });
    }

    // ---------------- combat ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity attacker = e.getDamager();
        if (attacker instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Entity shooter) attacker = shooter;
        }
        if (moon.isBloodMob(attacker)) {
            e.setDamage(e.getDamage() * settings.getDouble(Setting.DAMAGE_MULTIPLIER));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player) return;
        if (!moon.isBloodMob(dead)) return;

        moon.countKill(dead.getWorld());
        if (dead.getKiller() == null) return; // only reward real kills

        double dropMult = settings.getDouble(Setting.DROP_MULTIPLIER) - 1.0;
        if (dropMult > 0) {
            List<ItemStack> extra = new ArrayList<>();
            for (ItemStack drop : e.getDrops()) {
                int add = (int) Math.floor(drop.getAmount() * dropMult);
                if (ThreadLocalRandom.current().nextDouble() < (drop.getAmount() * dropMult) - add) add++;
                if (add > 0) {
                    ItemStack copy = drop.clone();
                    copy.setAmount(add);
                    extra.add(copy);
                }
            }
            e.getDrops().addAll(extra);
        }
        e.setDroppedExp((int) Math.round(e.getDroppedExp() * settings.getDouble(Setting.XP_MULTIPLIER)));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        moon.markDied(e.getEntity());
    }

    // ---------------- sleep ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent e) {
        if (settings.getBool(Setting.BLOCK_SLEEP) && moon.isActive(e.getPlayer().getWorld())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.prefix() + settings.msg("no-sleep"));
        }
    }

    // ---------------- keep boss bar / tint in sync ----------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        moon.applyPlayerEffects(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        moon.clearPlayerEffects(e.getPlayer(), null);
        moon.applyPlayerEffects(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            moon.clearPlayerEffects(p, null);
            moon.applyPlayerEffects(p);
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        moon.clearPlayerEffects(e.getPlayer(), null);
    }
}

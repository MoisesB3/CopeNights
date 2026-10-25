package us.copestudios.copenights;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The second batch of scares. Everything here is harmless and reverts itself:
 * fake blocks are client-side only, fake TNT never explodes, sky drop returns you safely.
 */
public class ExtraScares {

    private final CopeNights plugin;
    private final HerobrineManager herobrine;

    /** players who must not take fall damage right now (sky drop) */
    private final Set<UUID> fallProtected = new HashSet<>();
    /** where to put a sky-dropped player back if they log out mid-air */
    private final Map<UUID, Location> skyReturn = new HashMap<>();

    public ExtraScares(CopeNights plugin, HerobrineManager herobrine) {
        this.plugin = plugin;
        this.herobrine = herobrine;
    }

    // ------------------------------------------------------------------
    // SKY_DROP - yanked into the sky, free fall, then back where they stood
    // ------------------------------------------------------------------

    public boolean skyDrop(Player p) {
        if (skyReturn.containsKey(p.getUniqueId())) return false;
        Location orig = p.getLocation().clone();
        World w = orig.getWorld();

        double height = plugin.getConfig().getDouble("sky-drop.height", 100);
        double seconds = plugin.getConfig().getDouble("sky-drop.seconds", 2.5);
        double topY = w.getMaxHeight() - 3;
        double targetY = Math.min(orig.getY() + height, topY);
        if (targetY - orig.getY() < 30) return false; // not enough sky (e.g. already near build limit)

        Location sky = orig.clone();
        sky.setY(targetY);
        sky.setPitch(80f); // looking straight down at the ground rushing up
        if (!sky.getBlock().isPassable() || !sky.clone().add(0, 1, 0).getBlock().isPassable()) return false;

        fallProtected.add(p.getUniqueId());
        skyReturn.put(p.getUniqueId(), orig);

        p.teleport(sky);
        p.setVelocity(new Vector(0, -0.5, 0));
        p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, SoundCategory.MASTER, 1f, 0.6f);
        p.playSound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.MASTER, 1f, 1f);
        p.sendTitle(CopeNights.color(plugin.getConfig().getString("sky-drop.title", "&#A80000&lFALLING")),
                CopeNights.color(plugin.getConfig().getString("sky-drop.subtitle", "&#FF8C1Ahe let go of you")), 0, 40, 10);

        Bukkit.getScheduler().runTaskLater(plugin, () -> returnFromSky(p), Math.max(20L, (long) (seconds * 20)));
        return true;
    }

    private void returnFromSky(Player p) {
        Location orig = skyReturn.remove(p.getUniqueId());
        if (orig == null || !p.isOnline()) return;
        p.stopSound(Sound.ITEM_ELYTRA_FLYING);
        p.setFallDistance(0);
        p.setVelocity(new Vector());
        p.teleport(orig);
        p.setFallDistance(0);
        p.playSound(orig, Sound.ENTITY_PLAYER_BIG_FALL, SoundCategory.MASTER, 1f, 0.6f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
        // keep fall damage off a little longer in case the client lags behind
        Bukkit.getScheduler().runTaskLater(plugin, () -> fallProtected.remove(p.getUniqueId()), 40L);
    }

    public boolean isFallProtected(UUID id) {
        return fallProtected.contains(id);
    }

    /** Called on quit so nobody logs back in mid-air. */
    public void onQuit(Player p) {
        Location orig = skyReturn.remove(p.getUniqueId());
        if (orig != null) {
            p.setFallDistance(0);
            p.teleport(orig);
        }
        fallProtected.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------
    // BURIED - gravel caves in around them (client-side only)
    // ------------------------------------------------------------------

    public boolean buried(Player p) {
        Block feet = p.getLocation().getBlock();
        List<Location> changed = new ArrayList<>();
        BlockData gravel = Material.GRAVEL.createBlockData();

        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean inside = dx == 0 && dz == 0 && dy < 2; // keep the player's own space
                    if (inside) continue;
                    Block b = feet.getRelative(dx, dy, dz);
                    if (!b.isPassable()) continue;
                    changed.add(b.getLocation());
                }
            }
        }
        if (changed.isEmpty()) return false;

        // drop the gravel in from the top down for a "cave-in" feel
        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                for (int n = 0; n < 4 && i < changed.size(); n++, i++) {
                    Location l = changed.get(changed.size() - 1 - i);
                    p.sendBlockChange(l, gravel);
                    p.playSound(l, Sound.BLOCK_GRAVEL_PLACE, SoundCategory.BLOCKS, 1f, 0.7f);
                }
                if (i >= changed.size()) {
                    cancel();
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, false));
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1f, 1f);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> restore(p, changed), 70L);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    // ------------------------------------------------------------------
    // LIGHTS_OUT - nearby torches/lanterns blow out one by one (client-side)
    // ------------------------------------------------------------------

    public boolean lightsOut(Player p) {
        Block c = p.getLocation().getBlock();
        List<Location> lights = new ArrayList<>();
        int r = 12;
        for (int x = -r; x <= r; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -r; z <= r; z++) {
                    Block b = c.getRelative(x, y, z);
                    if (isBlowable(b)) lights.add(b.getLocation());
                    if (lights.size() >= 60) break;
                }
            }
        }
        if (lights.isEmpty()) return false;

        // closest lights go out last, so it feels like the dark is closing in
        Location me = p.getLocation();
        lights.sort((a, b) -> Double.compare(b.distanceSquared(me), a.distanceSquared(me)));

        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                if (i >= lights.size()) {
                    cancel();
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 0.8f, 0.5f);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> restore(p, lights), 160L);
                    return;
                }
                Location l = lights.get(i++);
                p.sendBlockChange(l, unlit(l.getBlock().getBlockData()));
                p.playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 1.4f);
                p.spawnParticle(Particle.SMOKE, l.clone().add(0.5, 0.6, 0.5), 6, 0.1, 0.1, 0.1, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 3L);
        return true;
    }

    private boolean isBlowable(Block b) {
        Material m = b.getType();
        if (m == Material.TORCH || m == Material.WALL_TORCH || m == Material.SOUL_TORCH || m == Material.SOUL_WALL_TORCH
                || m == Material.LANTERN || m == Material.SOUL_LANTERN) return true;
        BlockData d = b.getBlockData();
        return (Tag.CANDLES.isTagged(m) || m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE
                || m == Material.REDSTONE_LAMP) && d instanceof Lightable l && l.isLit();
    }

    private BlockData unlit(BlockData d) {
        if (d instanceof Lightable l) {
            Lightable copy = (Lightable) l.clone();
            copy.setLit(false);
            return copy;
        }
        return Material.AIR.createBlockData(); // torches / lanterns just vanish
    }

    // ------------------------------------------------------------------
    // FAKE_TNT - primed TNT appears behind them (only they see it) and "explodes"
    // ------------------------------------------------------------------

    public boolean fakeTnt(Player p) {
        Location l = behindGround(p, 2);
        if (l == null) return false;
        TNTPrimed tnt = l.getWorld().spawn(l, TNTPrimed.class, t -> {
            t.setVisibleByDefault(false);
            t.setPersistent(false);
            t.setFuseTicks(200); // real fuse is long; we remove it way before
            t.setYield(0f);
            t.setIsIncendiary(false);
            t.getPersistentDataContainer().set(plugin.scareEntityKey(), PersistentDataType.BYTE, (byte) 1);
        });
        p.showEntity(plugin, tnt);
        p.playSound(l, Sound.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1f, 1f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location at = tnt.isValid() ? tnt.getLocation() : l;
            tnt.remove();
            if (!p.isOnline()) return;
            p.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1f, 0.8f);
            p.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
            p.playHurtAnimation(0);
        }, 60L);
        return true;
    }

    // ------------------------------------------------------------------
    // TURN_AROUND - camera gets spun around... and Herobrine is right there
    // ------------------------------------------------------------------

    public boolean turnAround(Player p) {
        if (!herobrine.stalker(p)) return false;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            Location l = p.getLocation();
            l.setYaw(l.getYaw() + 180f);
            l.setPitch(0f);
            p.teleport(l); // only way to rotate a player's camera
        }, 25L);
        return true;
    }

    // ------------------------------------------------------------------
    // FAKE_JOIN - "Herobrine joined the game" (only they see it)
    // ------------------------------------------------------------------

    public boolean fakeJoin(Player p) {
        String name = plugin.getConfig().getString("fake-join.name", "Herobrine");
        p.sendMessage(ChatColor.YELLOW + name + " joined the game");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            List<String> lines = plugin.getConfig().getStringList("fake-join.messages");
            String line = lines.isEmpty() ? "hi " + p.getName() : lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
            p.sendMessage("<" + name + "> " + line.replace("%player%", p.getName()));
            p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.7f, 0.6f);
        }, 80L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) p.sendMessage(ChatColor.YELLOW + name + " left the game");
        }, 160L);
        return true;
    }

    // ------------------------------------------------------------------
    // KNOCK - knocking on the nearest door, then it creaks open (client-side)
    // ------------------------------------------------------------------

    public boolean knock(Player p) {
        Block door = nearestDoor(p, 12);
        Location soundAt = door != null ? door.getLocation().add(0.5, 1, 0.5) : behind(p, 5);

        int[] knockTicks = {0, 6, 12, 40, 46};
        for (int t : knockTicks) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.playSound(soundAt, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.35f, 1.8f);
            }, t);
        }

        if (door != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                List<Location> halves = new ArrayList<>();
                for (Block half : new Block[]{door, door.getRelative(0, 1, 0)}) {
                    if (half.getBlockData() instanceof Openable o && !o.isOpen()) {
                        Openable open = (Openable) o.clone();
                        open.setOpen(true);
                        p.sendBlockChange(half.getLocation(), open);
                        halves.add(half.getLocation());
                    }
                }
                p.playSound(soundAt, Sound.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1f, 0.5f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    restore(p, halves);
                    if (p.isOnline()) p.playSound(soundAt, Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1f, 0.5f);
                }, 80L);
            }, 90L);
        }
        return true;
    }

    private Block nearestDoor(Player p, int r) {
        Block c = p.getLocation().getBlock();
        Block best = null;
        double bestD = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    Block b = c.getRelative(x, y, z);
                    if (!Tag.WOODEN_DOORS.isTagged(b.getType())) continue;
                    // use the bottom half
                    if (Tag.WOODEN_DOORS.isTagged(b.getRelative(0, -1, 0).getType())) b = b.getRelative(0, -1, 0);
                    double d = b.getLocation().distanceSquared(p.getLocation());
                    if (d < bestD && d > 4) {
                        bestD = d;
                        best = b;
                    }
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // SURROUNDED - groans from every direction, closing in... then silence
    // ------------------------------------------------------------------

    public boolean surrounded(Player p) {
        Sound[] groans = {Sound.ENTITY_ZOMBIE_AMBIENT, Sound.ENTITY_SKELETON_AMBIENT, Sound.ENTITY_HUSK_AMBIENT,
                Sound.ENTITY_ZOMBIE_VILLAGER_AMBIENT, Sound.ENTITY_SPIDER_AMBIENT};
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                if (tick < 12) {
                    double dist = 10 - tick * 0.6; // closing in
                    double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                    Location l = p.getLocation().add(Math.cos(angle) * dist, 1, Math.sin(angle) * dist);
                    Sound s = groans[ThreadLocalRandom.current().nextInt(groans.length)];
                    p.playSound(l, s, SoundCategory.HOSTILE, 1f, 0.7f);
                } else if (tick == 18) {
                    // dead silence, then one breath right behind them
                    p.playSound(behind(p, 0.8), Sound.ENTITY_WARDEN_SNIFF, SoundCategory.HOSTILE, 1f, 0.8f);
                    cancel();
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 6L);
        return true;
    }

    // ------------------------------------------------------------------
    // PHANTOM_HIT - something invisible hits them (no real damage) and laughs
    // ------------------------------------------------------------------

    public boolean phantomHit(Player p) {
        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                float yaw = (float) ThreadLocalRandom.current().nextDouble(360);
                p.playHurtAnimation(yaw);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1f, 1f);
                p.playSound(behind(p, 1), Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 0.8f, 0.8f);
            }, i * 15L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) p.playSound(behind(p, 3), Sound.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 0.8f, 0.5f);
        }, 55L);
        return true;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Puts the real blocks back for this player. */
    private void restore(Player p, List<Location> locs) {
        if (!p.isOnline()) return;
        for (Location l : locs) p.sendBlockChange(l, l.getBlock().getBlockData());
    }

    private Location behind(Player p, double dist) {
        Location l = p.getLocation();
        Vector dir = l.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) dir = new Vector(0, 0, 1);
        dir.normalize().multiply(-dist);
        return l.add(dir).add(0, 1.2, 0);
    }

    private Location behindGround(Player p, double dist) {
        Location l = behind(p, dist).subtract(0, 1.2, 0);
        Block b = l.getBlock();
        for (int i = 0; i < 3 && !b.isPassable(); i++) b = b.getRelative(0, 1, 0);
        return b.isPassable() ? b.getLocation().add(0.5, 0, 0.5) : null;
    }
}

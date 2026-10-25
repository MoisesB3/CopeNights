package us.copestudios.copenights.scare;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import us.copestudios.copecore.CopeCore;
import us.copestudios.copecore.util.Entities;
import us.copestudios.copecore.util.Particles;
import us.copestudios.copenights.CopeNights;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns "Herobrine" apparitions that only the targeted player can see.
 *
 * <h2>How the body works</h2>
 * <p>It's a frozen, silent, invulnerable skeleton wearing a player head (with the Herobrine skin) and
 * Steve-colored leather armor. It's hidden from everyone ({@code setVisibleByDefault(false)}) and then shown to
 * the target only, so other players never see it. It is never saved to the world, carries a hidden tag
 * ({@link CopeNights#scareEntityKey()}) so leftovers can be found and removed, and is registered with CopeCore's
 * entity protection so other Cope plugins leave it alone too.</p>
 *
 * <p>Only one apparition per player at a time: a new one replaces the old one.</p>
 */
public final class HerobrineManager {

    private final CopeNights plugin;
    private volatile PlayerProfile headProfile;

    /** target player -> their current apparition */
    private final Map<UUID, Apparition> active = new HashMap<>();

    public HerobrineManager(CopeNights plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Skin
    // ------------------------------------------------------------------

    /**
     * Loads the head skin from config.yml: "herobrine.skin-url" (a direct texture link) wins over
     * "herobrine.skin-name" (an account name, looked up from Mojang in the background so the server never freezes).
     */
    public void loadSkin() {
        String url = plugin.settings().get().getString("herobrine.skin-url", "");
        String name = plugin.settings().get().getString("herobrine.skin-name", "Herobrine");

        if (url != null && !url.isBlank()) {
            try {
                UUID id = UUID.nameUUIDFromBytes(("CopeNights:" + url).getBytes(StandardCharsets.UTF_8));
                PlayerProfile profile = Bukkit.createPlayerProfile(id, "Herobrine");
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(URI.create(url).toURL());
                profile.setTextures(textures);
                headProfile = profile;
                return;
            } catch (Exception e) {
                plugin.getLogger().warning("Bad herobrine.skin-url, falling back to skin-name: " + e.getMessage());
            }
        }

        if (name == null || name.isBlank()) {
            headProfile = null;
            return;
        }
        // Look the skin up off the main thread so the server never hitches.
        try {
            Bukkit.createPlayerProfile(name).update()
                    .thenAccept(p -> headProfile = p)
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("Could not load Herobrine skin '" + name + "': " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load Herobrine skin '" + name + "': " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Public scares
    // ------------------------------------------------------------------

    /** Herobrine appears in the distance, stares, and vanishes when looked at or approached. */
    public boolean sighting(Player target) {
        double min = plugin.settings().get().getDouble("herobrine.min-distance", 14);
        double max = Math.max(min + 1, plugin.settings().get().getDouble("herobrine.max-distance", 26));

        Location spot = findSightingSpot(target, min, max);
        if (spot == null) return false;

        remove(target.getUniqueId());
        Skeleton body = spawnBody(target, spot);

        int lifetime = (int) (plugin.settings().get().getDouble("herobrine.lifetime-seconds", 12) * 20);
        int lookNeeded = (int) (plugin.settings().get().getDouble("herobrine.vanish-look-seconds", 1.5) * 20);
        double vanishDist = plugin.settings().get().getDouble("herobrine.vanish-distance", 8);

        Apparition app = new Apparition(body);
        active.put(target.getUniqueId(), app);

        app.task = new BukkitRunnable() {
            int age = 0;
            int lookTicks = 0;

            @Override
            public void run() {
                age += 2;
                if (!valid(target, body)) {
                    cleanup(target.getUniqueId(), app);
                    cancel();
                    return;
                }
                face(body, target);

                double dist = body.getLocation().distance(target.getLocation());
                if (isLookingAt(target, body, 0.97)) lookTicks += 2;

                if (dist < vanishDist || lookTicks >= lookNeeded) {
                    vanish(target, app, true);
                    cancel();
                } else if (age >= lifetime) {
                    vanish(target, app, false);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
        return true;
    }

    /** Herobrine stands right behind the player. Turning around triggers a scream. */
    public boolean stalker(Player target) {
        Location spot = findBehindSpot(target, 1.8);
        if (spot == null) return false;

        remove(target.getUniqueId());
        Skeleton body = spawnBody(target, spot);
        int lifetime = (int) (plugin.settings().get().getDouble("herobrine.stalker-lifetime-seconds", 10) * 20);

        Apparition app = new Apparition(body);
        active.put(target.getUniqueId(), app);

        // A single step sound behind them to make them want to turn around.
        target.playSound(body.getLocation(), Sound.BLOCK_GRAVEL_STEP, SoundCategory.HOSTILE, 0.7f, 0.8f);

        app.task = new BukkitRunnable() {
            int age = 0;

            @Override
            public void run() {
                age += 1;
                if (!valid(target, body)) {
                    cleanup(target.getUniqueId(), app);
                    cancel();
                    return;
                }

                boolean facing = isFacingHorizontally(target, body, 0.5);

                // If they walk away without looking, quietly catch back up behind them.
                if (!facing && body.getLocation().distanceSquared(target.getLocation()) > 25) {
                    Location again = findBehindSpot(target, 1.8);
                    if (again != null) body.teleport(again);
                }
                face(body, target);

                if (age > 10 && facing) {
                    Location l = target.getLocation();
                    target.playSound(l, Sound.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 1f, 0.6f);
                    target.playSound(l, Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 1f, 0.5f);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, false));
                    // Let them see his face for a split second, then vanish
                    Bukkit.getScheduler().runTaskLater(plugin, () -> vanish(target, app, true), 8L);
                    cancel();
                    return;
                }

                if (age % 60 == 0) {
                    target.playSound(body.getLocation(), Sound.BLOCK_GRAVEL_STEP, SoundCategory.HOSTILE, 0.5f, 0.7f);
                }
                if (age >= lifetime) {
                    vanish(target, app, false);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    /** True while this player has an apparition. */
    public boolean has(UUID target) {
        return active.containsKey(target);
    }

    public void remove(UUID target) {
        Apparition app = active.remove(target);
        if (app != null) app.destroy();
    }

    /** Removes every apparition. @return how many there were */
    public int removeAll() {
        int n = active.size();
        active.values().forEach(Apparition::destroy);
        active.clear();
        return n;
    }

    /** True if this entity is the body of a live apparition (not a leftover). */
    public boolean isActiveEntity(Entity e) {
        for (Apparition a : active.values()) {
            if (a.body.getUniqueId().equals(e.getUniqueId())) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void vanish(Player target, Apparition app, boolean withEffect) {
        if (withEffect && target.isOnline() && app.body.isValid()) {
            Location l = app.body.getLocation().add(0, 1, 0);
            // "LARGE_SMOKE" on 1.20.5+, "SMOKE_LARGE" before: CopeCore picks the one that exists.
            Particles.spawn(target, "LARGE_SMOKE", l, 25, 0.3, 0.7, 0.3, 0.01);
            target.playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.6f, 0.5f);
        }
        cleanup(target.getUniqueId(), app);
    }

    private void cleanup(UUID target, Apparition app) {
        if (active.get(target) == app) active.remove(target);
        app.destroy();
    }

    private boolean valid(Player target, Entity body) {
        return target.isOnline() && body.isValid()
                && body.getWorld().equals(target.getWorld());
    }

    private Skeleton spawnBody(Player target, Location at) {
        // CopeCore picks the spawn method this server has (its signature changed in 1.20.2).
        Skeleton body = Entities.spawn(at, Skeleton.class, e -> {
            e.setVisibleByDefault(false);   // hidden from everyone...
            e.setPersistent(false);         // ...never saved to the world
            e.setRemoveWhenFarAway(true);
            e.setAI(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setCanPickupItems(false);
            e.setCustomNameVisible(false);
            e.getPersistentDataContainer().set(plugin.scareEntityKey(), PersistentDataType.BYTE, (byte) 1);
            e.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false, false));

            EntityEquipment eq = e.getEquipment();
            if (eq != null) {
                eq.clear();
                eq.setHelmet(head());
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, Color.fromRGB(0x00A8A8)));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, Color.fromRGB(0x3A3A9E)));
                eq.setBoots(leather(Material.LEATHER_BOOTS, Color.fromRGB(0x4A4A4A)));
                eq.setItemInMainHand(new ItemStack(Material.AIR));
                eq.setHelmetDropChance(0f);
                eq.setChestplateDropChance(0f);
                eq.setLeggingsDropChance(0f);
                eq.setBootsDropChance(0f);
                eq.setItemInMainHandDropChance(0f);
            }
        });
        CopeCore.protection().protect(body); // other Cope plugins treat it as untouchable too
        target.showEntity(plugin, body); // ...except the target
        face(body, target);
        return body;
    }

    private ItemStack head() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        PlayerProfile profile = headProfile;
        if (profile != null && head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack leather(Material mat, Color color) {
        ItemStack item = new ItemStack(mat);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void face(Entity body, Player target) {
        Vector dir = target.getEyeLocation().toVector()
                .subtract(body.getLocation().add(0, 1.62, 0).toVector());
        if (dir.lengthSquared() < 1.0E-4) return;
        Location look = body.getLocation().setDirection(dir);
        body.setRotation(look.getYaw(), look.getPitch());
    }

    private boolean isLookingAt(Player p, Entity e, double threshold) {
        Location eye = p.getEyeLocation();
        Vector to = e.getLocation().add(0, 1.4, 0).toVector().subtract(eye.toVector());
        if (to.lengthSquared() < 1.0E-4) return true;
        return eye.getDirection().dot(to.normalize()) > threshold && p.hasLineOfSight(e);
    }

    private boolean isFacingHorizontally(Player p, Entity e, double threshold) {
        Vector look = p.getLocation().getDirection().setY(0);
        Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        if (look.lengthSquared() < 1.0E-4 || to.lengthSquared() < 1.0E-4) return false;
        return look.normalize().dot(to.normalize()) > threshold;
    }

    /** Somewhere ahead-ish of the player, preferably in plain view. */
    private Location findSightingSpot(Player p, double min, double max) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location base = p.getLocation();
        Location fallback = null;

        for (int attempt = 0; attempt < 20; attempt++) {
            double yaw = Math.toRadians(base.getYaw() + r.nextDouble(-65, 65));
            double dist = r.nextDouble(min, max);
            // Minecraft yaw: 0 = +Z, 90 = -X
            double x = base.getX() - Math.sin(yaw) * dist;
            double z = base.getZ() + Math.cos(yaw) * dist;

            Location ground = standableNear(base.getWorld(), x, base.getY(), z, 10, 14);
            if (ground == null) continue;
            if (fallback == null) fallback = ground;

            Location eye = p.getEyeLocation();
            Vector to = ground.clone().add(0, 1.6, 0).toVector().subtract(eye.toVector());
            RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, to.clone().normalize(), to.length(),
                    FluidCollisionMode.NEVER, true);
            if (hit == null) return ground; // clear line of sight
        }
        return fallback;
    }

    private Location findBehindSpot(Player p, double dist) {
        Location base = p.getLocation();
        Vector back = base.getDirection().setY(0);
        if (back.lengthSquared() < 1.0E-4) back = new Vector(0, 0, 1);
        back.normalize().multiply(-dist);
        Location l = base.clone().add(back);
        return standableNear(base.getWorld(), l.getX(), base.getY(), l.getZ(), 2, 3);
    }

    /** Finds a block with solid ground and 2 free blocks above, scanning around the given Y. */
    private Location standableNear(World w, double x, double y, double z, int up, int down) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = (int) Math.floor(y);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return null;

        for (int dy = up; dy >= -down; dy--) {
            int fy = by + dy;
            if (fy <= w.getMinHeight() || fy + 2 >= w.getMaxHeight()) continue;
            Block floor = w.getBlockAt(bx, fy - 1, bz);
            Block feet = w.getBlockAt(bx, fy, bz);
            Block headB = w.getBlockAt(bx, fy + 1, bz);
            if (floor.getType().isSolid() && feet.isPassable() && headB.isPassable()
                    && !feet.isLiquid() && !headB.isLiquid()) {
                return new Location(w, bx + 0.5, fy, bz + 0.5);
            }
        }
        return null;
    }

    private static final class Apparition {
        final Skeleton body;
        BukkitTask task;

        Apparition(Skeleton body) {
            this.body = body;
        }

        void destroy() {
            if (task != null) task.cancel();
            if (body.isValid()) body.remove();
        }
    }
}

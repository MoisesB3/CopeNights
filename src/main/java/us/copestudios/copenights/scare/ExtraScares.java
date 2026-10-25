package us.copestudios.copenights.scare;

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
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
import us.copestudios.copecore.CopeCore;
import us.copestudios.copecore.text.Text;
import us.copestudios.copecore.util.Entities;
import us.copestudios.copecore.util.Particles;
import us.copestudios.copenights.CopeNights;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static us.copestudios.copenights.scare.ScareManager.behind;

/**
 * The second batch of scares. Everything here is harmless and undoes itself:
 * fake blocks are client-side only, fake TNT never explodes, and sky drop puts you back where you were.
 */
public final class ExtraScares {

    /** LIGHTS_OUT: how far to look for lights, and the most it blows out. */
    private static final int LIGHT_RADIUS = 12;
    private static final int MAX_LIGHTS = 60;

    private final CopeNights plugin;
    private final HerobrineManager herobrine;

    /** Players who must not take fall damage right now (sky drop). */
    private final Set<UUID> fallProtected = new HashSet<>();
    /** Where to put a sky-dropped player back, even if they log out mid-air. */
    private final Map<UUID, Location> skyReturn = new HashMap<>();

    ExtraScares(CopeNights plugin, HerobrineManager herobrine) {
        this.plugin = plugin;
        this.herobrine = herobrine;
    }

    // =====================================================================
    //  SKY_DROP - yanked into the sky, free fall, then back where they stood
    // =====================================================================

    public boolean skyDrop(Player player) {
        if (skyReturn.containsKey(player.getUniqueId())) {
            return false; // already falling
        }
        Location original = player.getLocation().clone();
        World world = original.getWorld();
        if (world == null) {
            return false;
        }

        double height = plugin.settings().get().getDouble("sky-drop.height", 100);
        double seconds = plugin.settings().get().getDouble("sky-drop.seconds", 2.5);
        double topY = world.getMaxHeight() - 3;
        double targetY = Math.min(original.getY() + height, topY);
        if (targetY - original.getY() < 30) {
            return false; // not enough sky (already near the build limit)
        }

        Location sky = original.clone();
        sky.setY(targetY);
        sky.setPitch(80f); // looking down at the ground rushing up
        if (!sky.getBlock().isPassable() || !sky.clone().add(0, 1, 0).getBlock().isPassable()) {
            return false;
        }

        fallProtected.add(player.getUniqueId());
        skyReturn.put(player.getUniqueId(), original);

        player.teleport(sky);
        player.setVelocity(new Vector(0, -0.5, 0));
        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, SoundCategory.MASTER, 1f, 0.6f);
        player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.MASTER, 1f, 1f);
        plugin.messages().title(player, "scares.sky-drop.title", "scares.sky-drop.subtitle", 0, 40, 10,
                "player", player.getName());

        Bukkit.getScheduler().runTaskLater(plugin, () -> returnFromSky(player), Math.max(20L, (long) (seconds * 20)));
        return true;
    }

    private void returnFromSky(Player player) {
        Location original = skyReturn.remove(player.getUniqueId());
        if (original == null || !player.isOnline()) {
            return;
        }
        player.stopSound(Sound.ITEM_ELYTRA_FLYING);
        player.setFallDistance(0);
        player.setVelocity(new Vector());
        player.teleport(original);
        player.setFallDistance(0);
        player.playSound(original, Sound.ENTITY_PLAYER_BIG_FALL, SoundCategory.MASTER, 1f, 0.6f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
        // Keep fall damage off a little longer in case the client lags behind.
        Bukkit.getScheduler().runTaskLater(plugin, () -> fallProtected.remove(player.getUniqueId()), 40L);
    }

    public boolean isFallProtected(UUID id) {
        return fallProtected.contains(id);
    }

    /** Called on quit so nobody logs back in mid-air. */
    public void onQuit(Player player) {
        Location original = skyReturn.remove(player.getUniqueId());
        if (original != null) {
            player.setFallDistance(0);
            player.teleport(original);
        }
        fallProtected.remove(player.getUniqueId());
    }

    // =====================================================================
    //  BURIED - gravel caves in around them (only they see it)
    // =====================================================================

    public boolean buried(Player player) {
        Block feet = player.getLocation().getBlock();
        List<Location> changed = new ArrayList<>();
        BlockData gravel = Material.GRAVEL.createBlockData();

        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean playerSpace = dx == 0 && dz == 0 && dy < 2; // keep their own two blocks free
                    if (playerSpace) {
                        continue;
                    }
                    Block block = feet.getRelative(dx, dy, dz);
                    if (block.isPassable()) {
                        changed.add(block.getLocation());
                    }
                }
            }
        }
        if (changed.isEmpty()) {
            return false;
        }

        // Drop the gravel in from the top down for a "cave-in" feel.
        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                for (int n = 0; n < 4 && index < changed.size(); n++, index++) {
                    Location at = changed.get(changed.size() - 1 - index);
                    player.sendBlockChange(at, gravel);
                    player.playSound(at, Sound.BLOCK_GRAVEL_PLACE, SoundCategory.BLOCKS, 1f, 0.7f);
                }
                if (index >= changed.size()) {
                    cancel();
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, false));
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1f, 1f);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> restore(player, changed), 70L);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    // =====================================================================
    //  LIGHTS_OUT - nearby torches/lanterns blow out one by one (only they see it)
    // =====================================================================

    public boolean lightsOut(Player player) {
        Block center = player.getLocation().getBlock();
        List<Location> lights = new ArrayList<>();

        // FIX: the old version's "break" only left the innermost loop, so it kept scanning (and adding)
        // after reaching the limit. The label stops the whole search as soon as we have enough.
        search:
        for (int x = -LIGHT_RADIUS; x <= LIGHT_RADIUS; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -LIGHT_RADIUS; z <= LIGHT_RADIUS; z++) {
                    Block block = center.getRelative(x, y, z);
                    if (isBlowable(block)) {
                        lights.add(block.getLocation());
                        if (lights.size() >= MAX_LIGHTS) {
                            break search;
                        }
                    }
                }
            }
        }
        if (lights.isEmpty()) {
            return false;
        }

        // The closest lights go out last, so it feels like the dark is closing in.
        Location here = player.getLocation();
        lights.sort((a, b) -> Double.compare(b.distanceSquared(here), a.distanceSquared(here)));

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (index >= lights.size()) {
                    cancel();
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 0.8f, 0.5f);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> restore(player, lights), 160L);
                    return;
                }
                Location at = lights.get(index++);
                player.sendBlockChange(at, unlit(at.getBlock().getBlockData()));
                player.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 1.4f);
                // "SMOKE" on 1.20.5+, "SMOKE_NORMAL" before.
                Particles.spawn(player, "SMOKE", at.clone().add(0.5, 0.6, 0.5), 6, 0.1, 0.1, 0.1, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 3L);
        return true;
    }

    private static boolean isBlowable(Block block) {
        Material type = block.getType();
        if (type == Material.TORCH || type == Material.WALL_TORCH || type == Material.SOUL_TORCH
                || type == Material.SOUL_WALL_TORCH || type == Material.LANTERN || type == Material.SOUL_LANTERN) {
            return true;
        }
        boolean canBeLit = Tag.CANDLES.isTagged(type) || type == Material.CAMPFIRE
                || type == Material.SOUL_CAMPFIRE || type == Material.REDSTONE_LAMP;
        return canBeLit && block.getBlockData() instanceof Lightable lightable && lightable.isLit();
    }

    private static BlockData unlit(BlockData data) {
        if (data instanceof Lightable lightable) {
            Lightable copy = (Lightable) lightable.clone();
            copy.setLit(false);
            return copy;
        }
        return Material.AIR.createBlockData(); // torches and lanterns just vanish
    }

    // =====================================================================
    //  FAKE_TNT - lit TNT appears behind them (only they see it) and "explodes"
    // =====================================================================

    public boolean fakeTnt(Player player) {
        Location at = behindGround(player, 2);
        if (at == null || at.getWorld() == null) {
            return false;
        }
        // CopeCore picks the spawn method this server has (its signature changed in 1.20.2).
        TNTPrimed tnt = Entities.spawn(at, TNTPrimed.class, entity -> {
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setFuseTicks(200); // the real fuse is long; we remove it well before
            entity.setYield(0f);
            entity.setIsIncendiary(false);
            entity.getPersistentDataContainer().set(plugin.scareEntityKey(), PersistentDataType.BYTE, (byte) 1);
        });
        CopeCore.protection().protect(tnt);
        player.showEntity(plugin, tnt);
        player.playSound(at, Sound.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1f, 1f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location boom = tnt.isValid() ? tnt.getLocation() : at;
            tnt.remove();
            if (!player.isOnline()) {
                return;
            }
            player.playSound(boom, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1f, 0.8f);
            // "EXPLOSION_EMITTER" on 1.20.5+, "EXPLOSION_HUGE" before.
            Particles.spawn(player, "EXPLOSION_EMITTER", boom, 1, 0, 0, 0, 0);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
            hurtFlash(player, 0);
        }, 60L);
        return true;
    }

    // =====================================================================
    //  TURN_AROUND - camera spins around... and Herobrine is right there
    // =====================================================================

    public boolean turnAround(Player player) {
        if (!herobrine.stalker(player)) {
            return false;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location at = player.getLocation();
            at.setYaw(at.getYaw() + 180f);
            at.setPitch(0f);
            player.teleport(at); // the only way to rotate a player's camera
        }, 25L);
        return true;
    }

    // =====================================================================
    //  FAKE_JOIN - "Herobrine joined the game" (only they see it)
    // =====================================================================

    public boolean fakeJoin(Player player) {
        // The fake name can be colored in messages.yml if you like (vanilla join messages are plain yellow).
        String name = plugin.messages().format("scares.fake-join.name");
        plugin.messages().sendRaw(player, "scares.fake-join.join", "name", name);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            List<String> lines = plugin.messages().file().get().getStringList("scares.fake-join.lines");
            String line = lines.isEmpty() ? "hi <player>"
                    : lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
            line = Text.replace(line, "player", player.getName());
            plugin.messages().sendRaw(player, "scares.fake-join.chat", "name", name, "line", line);
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.7f, 0.6f);
        }, 80L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.messages().sendRaw(player, "scares.fake-join.leave", "name", name);
            }
        }, 160L);
        return true;
    }

    // =====================================================================
    //  KNOCK - knocking on the nearest door, then it creaks open (only they see it)
    // =====================================================================

    public boolean knock(Player player) {
        Block door = nearestDoor(player, 12);
        Location soundAt = door != null ? door.getLocation().add(0.5, 1, 0.5) : behind(player, 5);

        for (int tick : new int[]{0, 6, 12, 40, 46}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.playSound(soundAt, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.35f, 1.8f);
                }
            }, tick);
        }

        if (door != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                List<Location> halves = new ArrayList<>();
                for (Block half : new Block[]{door, door.getRelative(0, 1, 0)}) {
                    if (half.getBlockData() instanceof Openable openable && !openable.isOpen()) {
                        Openable open = (Openable) openable.clone();
                        open.setOpen(true);
                        player.sendBlockChange(half.getLocation(), open);
                        halves.add(half.getLocation());
                    }
                }
                player.playSound(soundAt, Sound.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1f, 0.5f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    restore(player, halves);
                    if (player.isOnline()) {
                        player.playSound(soundAt, Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1f, 0.5f);
                    }
                }, 80L);
            }, 90L);
        }
        return true;
    }

    /** The closest wooden door (bottom half), at least 2 blocks away. */
    private static Block nearestDoor(Player player, int radius) {
        Block center = player.getLocation().getBlock();
        Block best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getRelative(x, y, z);
                    if (!Tag.WOODEN_DOORS.isTagged(block.getType())) {
                        continue;
                    }
                    if (Tag.WOODEN_DOORS.isTagged(block.getRelative(0, -1, 0).getType())) {
                        block = block.getRelative(0, -1, 0); // use the bottom half
                    }
                    double distance = block.getLocation().distanceSquared(player.getLocation());
                    if (distance < bestDistance && distance > 4) {
                        bestDistance = distance;
                        best = block;
                    }
                }
            }
        }
        return best;
    }

    // =====================================================================
    //  SURROUNDED - groans from every direction, closing in... then one breath
    // =====================================================================

    public boolean surrounded(Player player) {
        Sound[] groans = {Sound.ENTITY_ZOMBIE_AMBIENT, Sound.ENTITY_SKELETON_AMBIENT, Sound.ENTITY_HUSK_AMBIENT,
                Sound.ENTITY_ZOMBIE_VILLAGER_AMBIENT, Sound.ENTITY_SPIDER_AMBIENT};
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                ThreadLocalRandom random = ThreadLocalRandom.current();
                if (tick < 12) {
                    double distance = 10 - tick * 0.6; // closing in
                    double angle = random.nextDouble(Math.PI * 2);
                    Location at = player.getLocation().add(Math.cos(angle) * distance, 1, Math.sin(angle) * distance);
                    player.playSound(at, groans[random.nextInt(groans.length)], SoundCategory.HOSTILE, 1f, 0.7f);
                } else if (tick == 18) {
                    // dead silence, then one breath right behind them
                    player.playSound(behind(player, 0.8), Sound.ENTITY_WARDEN_SNIFF, SoundCategory.HOSTILE, 1f, 0.8f);
                    cancel();
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 6L);
        return true;
    }

    // =====================================================================
    //  PHANTOM_HIT - something invisible hits them (no real damage) and laughs
    // =====================================================================

    public boolean phantomHit(Player player) {
        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                float yaw = (float) ThreadLocalRandom.current().nextDouble(360);
                hurtFlash(player, yaw); // red flash + camera shake, no damage
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1f, 1f);
                player.playSound(behind(player, 1), Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 0.8f, 0.8f);
            }, i * 15L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(behind(player, 3), Sound.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 0.8f, 0.5f);
            }
        }, 55L);
        return true;
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    /** Cached Player#playHurtAnimation(float), or null when the server doesn't have it. */
    private static final Method HURT_ANIMATION = findHurtAnimation();

    private static Method findHurtAnimation() {
        try {
            return Player.class.getMethod("playHurtAnimation", float.class);
        } catch (NoSuchMethodException missing) {
            return null;
        }
    }

    /**
     * The "you got hit" red flash and camera shake, without any damage.
     * <p>playHurtAnimation(yaw) doesn't exist on Spigot 1.20-1.20.1 (it came later), so it's called through
     * reflection, and older servers get the classic hurt effect instead (no direction, same flash).</p>
     */
    @SuppressWarnings("deprecation")
    private static void hurtFlash(Player player, float yaw) {
        if (HURT_ANIMATION != null) {
            try {
                HURT_ANIMATION.invoke(player, yaw);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall through to the old effect
            }
        }
        try {
            player.playEffect(EntityEffect.valueOf("HURT"));
        } catch (IllegalArgumentException ignored) {
            // effect removed on this version: the hurt sound still plays
        }
    }

    /** Shows the real blocks to this player again. */
    private static void restore(Player player, List<Location> locations) {
        if (!player.isOnline()) {
            return;
        }
        for (Location at : locations) {
            player.sendBlockChange(at, at.getBlock().getBlockData());
        }
    }

    /** A free block on the ground behind the player, or null if there isn't one. */
    private static Location behindGround(Player player, double distance) {
        Location at = behind(player, distance).subtract(0, 1.2, 0);
        Block block = at.getBlock();
        for (int i = 0; i < 3 && !block.isPassable(); i++) {
            block = block.getRelative(0, 1, 0);
        }
        return block.isPassable() ? block.getLocation().add(0.5, 0, 0.5) : null;
    }
}

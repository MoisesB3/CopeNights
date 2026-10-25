package us.copestudios.copenights;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ScareManager {

    private final CopeNights plugin;
    private final HerobrineManager herobrine;
    private final ExtraScares extra;

    /** last auto-scare time per player (ms) */
    private final Map<UUID, Long> lastAutoScare = new HashMap<>();

    private static final Sound[] CREEPY_SOUNDS = {
            Sound.AMBIENT_CAVE,
            Sound.ENTITY_ENDERMAN_SCREAM,
            Sound.ENTITY_GHAST_SCREAM,
            Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
            Sound.BLOCK_WOODEN_DOOR_OPEN,
            Sound.BLOCK_CHEST_OPEN,
            Sound.ENTITY_WITCH_CELEBRATE,
            Sound.ENTITY_WARDEN_NEARBY_CLOSE,
            Sound.ENTITY_ENDERMAN_STARE,
            Sound.ENTITY_SKELETON_AMBIENT
    };

    public ScareManager(CopeNights plugin, HerobrineManager herobrine) {
        this.plugin = plugin;
        this.herobrine = herobrine;
        this.extra = new ExtraScares(plugin, herobrine);
    }

    public ExtraScares extra() {
        return extra;
    }

    /**
     * Runs a scare on a player.
     * @return the scare that actually ran, or null if it couldn't (e.g. no room to spawn Herobrine)
     */
    public ScareType perform(Player p, ScareType type) {
        if (type == ScareType.RANDOM) type = pickWeighted();

        boolean ok = switch (type) {
            case HEROBRINE -> herobrine.sighting(p);
            case STALKER -> herobrine.stalker(p);
            case JUMPSCARE -> jumpscare(p);
            case SOUNDS -> sounds(p);
            case FOOTSTEPS -> footsteps(p);
            case CREEPER -> creeper(p);
            case WHISPER -> whisper(p);
            case BLACKOUT -> blackout(p);
            case LIGHTNING -> lightning(p);
            case SKY_DROP -> extra.skyDrop(p);
            case BURIED -> extra.buried(p);
            case LIGHTS_OUT -> extra.lightsOut(p);
            case FAKE_TNT -> extra.fakeTnt(p);
            case TURN_AROUND -> extra.turnAround(p);
            case FAKE_JOIN -> extra.fakeJoin(p);
            case KNOCK -> extra.knock(p);
            case SURROUNDED -> extra.surrounded(p);
            case PHANTOM_HIT -> extra.phantomHit(p);
            case RANDOM -> false;
        };

        // If Herobrine couldn't find room to stand, fall back to something that always works.
        if (!ok && (type == ScareType.HEROBRINE || type == ScareType.STALKER || type == ScareType.TURN_AROUND)) {
            footsteps(p);
            return ScareType.FOOTSTEPS;
        }
        // Scares that need something nearby (sky room, torches, space) fall back to a sound scare.
        if (!ok && (type == ScareType.SKY_DROP || type == ScareType.BURIED || type == ScareType.LIGHTS_OUT
                || type == ScareType.FAKE_TNT)) {
            extra.surrounded(p);
            return ScareType.SURROUNDED;
        }
        return ok ? type : null;
    }

    /** Weighted pick using auto.weights; falls back to an even pick if every weight is 0. */
    public ScareType pickWeighted() {
        return pickWeighted(plugin.getConfig().getConfigurationSection("auto.weights"));
    }

    /** Weighted pick from any "TYPE: weight" section (used by the blood moon's own weights). */
    public ScareType pickWeighted(ConfigurationSection sec) {
        Map<ScareType, Integer> weights = new EnumMap<>(ScareType.class);
        int total = 0;
        for (ScareType t : ScareType.values()) {
            if (t == ScareType.RANDOM) continue;
            int w = sec == null ? 1 : Math.max(0, sec.getInt(t.name(), 0));
            if (w > 0) {
                weights.put(t, w);
                total += w;
            }
        }
        if (total == 0) {
            List<ScareType> all = new ArrayList<>(List.of(ScareType.values()));
            all.remove(ScareType.RANDOM);
            return all.get(ThreadLocalRandom.current().nextInt(all.size()));
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (Map.Entry<ScareType, Integer> e : weights.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) return e.getKey();
        }
        return ScareType.SOUNDS;
    }

    // ------------------------------------------------------------------
    // Auto cooldowns
    // ------------------------------------------------------------------

    public boolean onAutoCooldown(Player p) {
        long cd = plugin.getConfig().getLong("auto.player-cooldown-seconds", 300) * 1000L;
        Long last = lastAutoScare.get(p.getUniqueId());
        return last != null && System.currentTimeMillis() - last < cd;
    }

    public void markAutoScared(Player p) {
        lastAutoScare.put(p.getUniqueId(), System.currentTimeMillis());
    }

    public void forget(UUID id) {
        lastAutoScare.remove(id);
    }

    // ------------------------------------------------------------------
    // Scares
    // ------------------------------------------------------------------

    private boolean jumpscare(Player p) {
        Location l = p.getLocation();
        p.spawnParticle(Particle.ELDER_GUARDIAN, l, 1);
        p.playSound(l, Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.MASTER, 1f, 1f);
        p.playSound(l, Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.MASTER, 1f, 0.5f);
        p.playSound(l, Sound.ENTITY_GHAST_HURT, SoundCategory.MASTER, 1f, 0.6f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
        p.sendTitle(
                CopeNights.color(plugin.getConfig().getString("jumpscare.title", "&4&l☠")),
                CopeNights.color(plugin.getConfig().getString("jumpscare.subtitle", "&cfound you")),
                0, 30, 10);
        return true;
    }

    private boolean sounds(Player p) {
        Sound s = CREEPY_SOUNDS[ThreadLocalRandom.current().nextInt(CREEPY_SOUNDS.length)];
        float pitch = (float) ThreadLocalRandom.current().nextDouble(0.5, 0.9);
        p.playSound(behind(p, 3), s, SoundCategory.HOSTILE, 1f, pitch);
        return true;
    }

    private boolean creeper(Player p) {
        p.playSound(behind(p, 1.2), Sound.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1f, 0.5f);
        return true;
    }

    private boolean whisper(Player p) {
        List<String> lines = plugin.whisperLines();
        String line = lines.isEmpty() ? "I see you." : lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        String fmt = plugin.getConfig().getString("whisper.format", "&e<Herobrine> &f%msg%");
        p.sendMessage(CopeNights.color(fmt.replace("%msg%", line)));
        p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.6f, 0.6f);
        return true;
    }

    private boolean footsteps(Player p) {
        new BukkitRunnable() {
            double dist = 9;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                p.playSound(behind(p, dist), stepSound(p), SoundCategory.HOSTILE, 0.9f, 0.85f);
                dist -= 1.2;
                if (dist < 1.5) {
                    cancel();
                    // Sometimes... he's actually there when they turn around.
                    if (ThreadLocalRandom.current().nextDouble() < 0.35) {
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (p.isOnline()) herobrine.stalker(p);
                        }, 4L);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 7L);
        return true;
    }

    private boolean blackout(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 110, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 130, 0, false, false, false));
        new BukkitRunnable() {
            int beats = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                if (beats < 6) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1f, 1f);
                    beats++;
                } else {
                    p.playSound(behind(p, 2), Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 1f, 1f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 15L);
        return true;
    }

    private boolean lightning(Player p) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angle = r.nextDouble(Math.PI * 2);
        double dist = r.nextDouble(3, 6);
        Location strike = p.getLocation().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        p.getWorld().strikeLightningEffect(strike); // effect only: no damage, no fire
        return true;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** A point behind the player at ear height. Sounds played here come "from behind". */
    private Location behind(Player p, double dist) {
        Location l = p.getLocation();
        Vector dir = l.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) dir = new Vector(0, 0, 1);
        dir.normalize().multiply(-dist);
        return l.add(dir).add(0, 1.2, 0);
    }

    private Sound stepSound(Player p) {
        Block below = p.getLocation().subtract(0, 1, 0).getBlock();
        if (below.getType() == Material.AIR || !below.getType().isSolid()) return Sound.BLOCK_STONE_STEP;
        return below.getBlockData().getSoundGroup().getStepSound();
    }
}

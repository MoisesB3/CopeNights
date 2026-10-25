package us.copestudios.copenights.scare;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import us.copestudios.copecore.text.Text;
import us.copestudios.copecore.util.Particles;
import us.copestudios.copenights.CopeNights;
import us.copestudios.copenights.ScareType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs scares. The simple ones live here; the ones with fake blocks/entities are in {@link ExtraScares};
 * Herobrine himself is in {@link HerobrineManager}.
 *
 * <p>Every scare is harmless: nothing is broken, nobody takes real damage, and anything fake (blocks,
 * TNT, Herobrine) is only visible to the scared player and cleans itself up.</p>
 */
public final class ScareManager {

    /** Name of the auto-scare cooldown in CopeCore's {@link us.copestudios.copecore.player.Cooldowns}. */
    private static final String AUTO_COOLDOWN = "auto-scare";

    private final CopeNights plugin;
    private final HerobrineManager herobrine;
    private final ExtraScares extra;

    /** Picked at random for the SOUNDS scare. Sound constants link fine on every version from 1.20 up. */
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
     *
     * @param type the scare, or {@link ScareType#RANDOM} for a weighted pick from config.yml "auto.weights"
     * @return the scare that actually ran (it can be a fallback, see below), or null if nothing ran
     */
    public ScareType perform(Player player, ScareType type) {
        if (type == ScareType.RANDOM) {
            type = pickWeighted();
        }

        boolean ok = switch (type) {
            case HEROBRINE -> herobrine.sighting(player);
            case STALKER -> herobrine.stalker(player);
            case JUMPSCARE -> jumpscare(player);
            case SOUNDS -> sounds(player);
            case FOOTSTEPS -> footsteps(player);
            case CREEPER -> creeper(player);
            case WHISPER -> whisper(player);
            case BLACKOUT -> blackout(player);
            case LIGHTNING -> lightning(player);
            case SKY_DROP -> extra.skyDrop(player);
            case BURIED -> extra.buried(player);
            case LIGHTS_OUT -> extra.lightsOut(player);
            case FAKE_TNT -> extra.fakeTnt(player);
            case TURN_AROUND -> extra.turnAround(player);
            case FAKE_JOIN -> extra.fakeJoin(player);
            case KNOCK -> extra.knock(player);
            case SURROUNDED -> extra.surrounded(player);
            case PHANTOM_HIT -> extra.phantomHit(player);
            case RANDOM -> false;
        };
        if (ok) {
            return type;
        }

        // Herobrine couldn't find room to stand: footsteps always work.
        if (type == ScareType.HEROBRINE || type == ScareType.STALKER || type == ScareType.TURN_AROUND) {
            footsteps(player);
            return ScareType.FOOTSTEPS;
        }
        // Scares that need something nearby (sky room, torches, free space) fall back to a sound scare.
        if (type == ScareType.SKY_DROP || type == ScareType.BURIED || type == ScareType.LIGHTS_OUT
                || type == ScareType.FAKE_TNT) {
            extra.surrounded(player);
            return ScareType.SURROUNDED;
        }
        return null;
    }

    /** Weighted pick using config.yml "auto.weights"; an even pick if every weight is 0. */
    public ScareType pickWeighted() {
        return pickWeighted(plugin.settings().get().getConfigurationSection("auto.weights"));
    }

    /** Weighted pick from any "TYPE: weight" section (the Blood Moon has its own weights). */
    public ScareType pickWeighted(ConfigurationSection weightsSection) {
        Map<ScareType, Integer> weights = new EnumMap<>(ScareType.class);
        int total = 0;
        for (ScareType type : ScareType.values()) {
            if (type == ScareType.RANDOM) {
                continue;
            }
            int weight = weightsSection == null ? 1 : Math.max(0, weightsSection.getInt(type.name(), 0));
            if (weight > 0) {
                weights.put(type, weight);
                total += weight;
            }
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (total == 0) {
            List<ScareType> all = new ArrayList<>(List.of(ScareType.values()));
            all.remove(ScareType.RANDOM);
            return all.get(random.nextInt(all.size()));
        }
        int roll = random.nextInt(total);
        for (Map.Entry<ScareType, Integer> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return ScareType.SOUNDS;
    }

    // =====================================================================
    //  Auto-scare cooldown (stored in CopeCore's Cooldowns helper)
    // =====================================================================

    public boolean onAutoCooldown(Player player) {
        return plugin.autoCooldowns().isActive(player.getUniqueId(), AUTO_COOLDOWN);
    }

    public void markAutoScared(Player player) {
        long millis = plugin.settings().get().getLong("auto.player-cooldown-seconds", 300) * 1000L;
        plugin.autoCooldowns().set(player.getUniqueId(), AUTO_COOLDOWN, millis);
    }

    public void forget(UUID id) {
        plugin.autoCooldowns().clear(id, AUTO_COOLDOWN);
    }

    // =====================================================================
    //  The simple scares
    // =====================================================================

    /** Elder guardian face + screams + a title. */
    private boolean jumpscare(Player player) {
        Location at = player.getLocation();
        // "ELDER_GUARDIAN" on 1.20.5+, "MOB_APPEARANCE" before: CopeCore picks the right one.
        Particles.spawn(player, "ELDER_GUARDIAN", at, 1, 0, 0, 0, 0);
        player.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.MASTER, 1f, 1f);
        player.playSound(at, Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.MASTER, 1f, 0.5f);
        player.playSound(at, Sound.ENTITY_GHAST_HURT, SoundCategory.MASTER, 1f, 0.6f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
        plugin.messages().title(player, "scares.jumpscare.title", "scares.jumpscare.subtitle", 0, 30, 10,
                "player", player.getName());
        return true;
    }

    /** One random creepy sound right behind them. */
    private boolean sounds(Player player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Sound sound = CREEPY_SOUNDS[random.nextInt(CREEPY_SOUNDS.length)];
        float pitch = (float) random.nextDouble(0.5, 0.9);
        player.playSound(behind(player, 3), sound, SoundCategory.HOSTILE, 1f, pitch);
        return true;
    }

    /** Creeper hiss in their ear. Nothing explodes. */
    private boolean creeper(Player player) {
        player.playSound(behind(player, 1.2), Sound.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1f, 0.5f);
        return true;
    }

    /** A chat whisper "from Herobrine" (messages.yml: scares.whisper). */
    private boolean whisper(Player player) {
        List<String> lines = plugin.messages().file().get().getStringList("scares.whisper.lines");
        String line = lines.isEmpty() ? "I see you." : lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        line = Text.replace(line, "player", player.getName());
        // sendRaw = no prefix: it has to look like a real chat message.
        plugin.messages().sendRaw(player, "scares.whisper.format", "player", player.getName(), "msg", line);
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.6f, 0.6f);
        return true;
    }

    /** Footsteps creeping up from behind. Sometimes Herobrine is really there at the end. */
    private boolean footsteps(Player player) {
        new BukkitRunnable() {
            double distance = 9;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                player.playSound(behind(player, distance), stepSound(player), SoundCategory.HOSTILE, 0.9f, 0.85f);
                distance -= 1.2;
                if (distance < 1.5) {
                    cancel();
                    if (ThreadLocalRandom.current().nextDouble() < 0.35) {
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline()) {
                                herobrine.stalker(player);
                            }
                        }, 4L);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 7L);
        return true;
    }

    /** Blinded with a heartbeat... then a roar. */
    private boolean blackout(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 110, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 130, 0, false, false, false));
        new BukkitRunnable() {
            int beats = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (beats < 6) {
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1f, 1f);
                    beats++;
                } else {
                    player.playSound(behind(player, 2), Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 1f, 1f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 15L);
        return true;
    }

    /** Harmless lightning a few blocks away (effect only: no damage, no fire). */
    private boolean lightning(Player player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2);
        double distance = random.nextDouble(3, 6);
        Location strike = player.getLocation().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
        player.getWorld().strikeLightningEffect(strike);
        return true;
    }

    // =====================================================================
    //  Helpers shared with ExtraScares
    // =====================================================================

    /** A point behind the player at ear height. Sounds played there come "from behind". */
    static Location behind(Player player, double distance) {
        Location at = player.getLocation();
        Vector direction = at.getDirection().setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize().multiply(-distance);
        return at.add(direction).add(0, 1.2, 0);
    }

    /** The step sound of the block they stand on, so footsteps sound like they're on the same floor. */
    private static Sound stepSound(Player player) {
        Block below = player.getLocation().subtract(0, 1, 0).getBlock();
        if (below.getType() == Material.AIR || !below.getType().isSolid()) {
            return Sound.BLOCK_STONE_STEP;
        }
        return below.getBlockData().getSoundGroup().getStepSound();
    }
}

package us.copestudios.copenights.api;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * What Cope Nights offers to other plugins. Found through CopeCore's Services, so your plugin only needs
 * {@code softdepend: [CopeNights]} and compiles against Cope Nights with {@code <scope>provided</scope>}.
 *
 * <pre>{@code
 * Services.find(NightsService.class).ifPresent(nights -> {
 *     if (nights.isBloodMoon(player.getWorld())) {
 *         // double rewards during a Blood Moon
 *     }
 * });
 * }</pre>
 */
public interface NightsService {

    /** True while a Blood Moon is active in this world. */
    boolean isBloodMoon(World world);

    /** Current horde wave in this world (0 when there's no Blood Moon). */
    int wave(World world);

    /** Days until the next Blood Moon in this world (0 = tonight / now). */
    long daysUntilBloodMoon(World world);

    /** True if the entity was spawned or buffed by a Blood Moon (including horde mobs). */
    boolean isBloodMoonMob(Entity entity);

    /** True if the entity belongs to a horde. */
    boolean isHordeMob(Entity entity);

    /**
     * Runs a scare on a player. {@code type} is a scare name from /coni list ("herobrine", "knock"...) or "random".
     *
     * @return the scare that actually ran (it can be a fallback), or null if the name is unknown or nothing ran
     */
    String scare(Player player, String type);

    /** Names of every scare type (lowercase), for tab completion or menus. */
    List<String> scareTypes();

    /**
     * Sends a horde at a player, with or without a Blood Moon.
     *
     * @return how many mobs will spawn (0 if it was cancelled or there was no room)
     */
    int spawnHorde(Player target, int size);
}

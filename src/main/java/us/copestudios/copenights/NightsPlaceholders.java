package us.copestudios.copenights;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import us.copestudios.copecore.CopeCore;
import us.copestudios.copenights.bloodmoon.BloodMoonManager;

import java.util.List;
import java.util.Locale;

/**
 * PlaceholderAPI placeholders, registered through CopeCore (so Cope Nights doesn't need its own PAPI code).
 * They only work when PlaceholderAPI is installed.
 *
 * <pre>
 * %cope_nights_auto%       ON/OFF text: is server-wide auto scaring on
 * %cope_nights_exempt%     true/false: is this player exempt from auto scares
 * %cope_nights_bloodmoon%  true/false: Blood Moon active in the player's world
 * %cope_nights_wave%       current horde wave in the player's world (0 = none)
 * %cope_nights_next%       days until the next Blood Moon in the player's world (0 = tonight)
 * </pre>
 *
 * <p>For a player who is offline (no world), the first eligible world is used.</p>
 */
final class NightsPlaceholders {

    private NightsPlaceholders() {
    }

    static void register(CopeNights plugin) {
        CopeCore.placeholders().register(plugin, "nights", (player, key) -> resolve(plugin, player, key));
    }

    private static String resolve(CopeNights plugin, OfflinePlayer player, String key) {
        BloodMoonManager bloodMoon = plugin.bloodMoon();
        World world = worldOf(player, bloodMoon);
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "auto" -> plugin.isAutoEnabled() ? "ON" : "OFF";
            case "exempt" -> String.valueOf(player != null && plugin.isExempt(player.getUniqueId()));
            case "bloodmoon" -> String.valueOf(world != null && bloodMoon.isActive(world));
            case "wave" -> String.valueOf(world == null ? 0 : bloodMoon.wave(world));
            case "next" -> world == null ? "-" : String.valueOf(bloodMoon.daysUntilNext(world));
            default -> null; // unknown placeholder: PlaceholderAPI leaves it as typed
        };
    }

    private static World worldOf(OfflinePlayer player, BloodMoonManager bloodMoon) {
        if (player != null) {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online != null) {
                return online.getWorld();
            }
        }
        List<World> worlds = bloodMoon.eligibleWorlds();
        return worlds.isEmpty() ? null : worlds.get(0);
    }
}

package us.copestudios.copenights.scare;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import us.copestudios.copenights.CopeNights;
import us.copestudios.copenights.ScareType;
import us.copestudios.copenights.bloodmoon.BloodMoonSettings.Setting;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls for random scares on every online player while auto mode is ON (/coni toggle).
 * Runs every "auto.check-interval-seconds". A player is skipped when any of these is true:
 * <ul>
 *   <li>they have the copenights.exempt permission or were exempted with /coni exempt</li>
 *   <li>they have a CopeCore state from "auto.skip-states" (in a duel, in combat, fighting a boss...)</li>
 *   <li>creative/spectator (with survival-only), wrong world, daytime (night-only), too bright (dark-only)</li>
 *   <li>they were auto-scared less than "auto.player-cooldown-seconds" ago, or Herobrine is already on them</li>
 * </ul>
 */
public final class AutoScareTask implements Runnable {

    private final CopeNights plugin;

    public AutoScareTask(CopeNights plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.isAutoEnabled()) {
            return;
        }
        YamlConfiguration config = plugin.settings().get();
        double chance = config.getDouble("auto.chance-per-check", 0.08);
        boolean nightOnly = config.getBoolean("auto.night-only", false);
        boolean darkOnly = config.getBoolean("auto.dark-only", false);
        boolean survivalOnly = config.getBoolean("auto.survival-only", true);
        List<String> worlds = config.getStringList("auto.worlds");

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("copenights.exempt") || plugin.isExempt(player.getUniqueId())) {
                continue;
            }
            if (plugin.isBusy(player)) {
                continue; // in a duel / in combat / fighting a boss...
            }
            GameMode mode = player.getGameMode();
            if (survivalOnly && (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR)) {
                continue;
            }
            World world = player.getWorld();
            if (!worlds.isEmpty() && !worlds.contains(world.getName())) {
                continue;
            }
            boolean bloodMoon = plugin.bloodMoon().isActive(world);
            if (nightOnly && !isNight(world) && !bloodMoon) {
                continue;
            }
            if (darkOnly && player.getLocation().getBlock().getLightLevel() > 7) {
                continue;
            }
            if (plugin.scares().onAutoCooldown(player) || plugin.herobrine().has(player.getUniqueId())) {
                continue;
            }

            double rollChance = bloodMoon
                    ? chance * plugin.bloodSettings().getDouble(Setting.SCARE_BOOST)
                    : chance;
            if (ThreadLocalRandom.current().nextDouble() < rollChance) {
                plugin.scares().perform(player, ScareType.RANDOM);
                plugin.scares().markAutoScared(player);
            }
        }
    }

    /** World time 13000-23000. The Nether and the End always count as night. */
    private static boolean isNight(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return true;
        }
        long time = world.getTime();
        return time >= 13000 && time <= 23000;
    }
}

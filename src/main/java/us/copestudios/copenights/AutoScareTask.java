package us.copestudios.copenights;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Rolls for random scares on every online player while auto mode is ON. */
public class AutoScareTask extends BukkitRunnable {

    private final CopeNights plugin;

    public AutoScareTask(CopeNights plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.isAutoEnabled()) return;

        double chance = plugin.getConfig().getDouble("auto.chance-per-check", 0.08);
        boolean nightOnly = plugin.getConfig().getBoolean("auto.night-only", false);
        boolean darkOnly = plugin.getConfig().getBoolean("auto.dark-only", false);
        boolean survivalOnly = plugin.getConfig().getBoolean("auto.survival-only", true);
        List<String> worlds = plugin.getConfig().getStringList("auto.worlds");

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.hasPermission("copenights.exempt") || plugin.isExempt(p.getUniqueId())) continue;
            if (survivalOnly && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) continue;
            if (!worlds.isEmpty() && !worlds.contains(p.getWorld().getName())) continue;
            if (nightOnly && !isNight(p.getWorld()) && !plugin.bloodMoon().isActive(p.getWorld())) continue;
            if (darkOnly && p.getLocation().getBlock().getLightLevel() > 7) continue;
            if (plugin.scares().onAutoCooldown(p)) continue;
            if (plugin.herobrine().has(p.getUniqueId())) continue;

            double rollChance = chance;
            if (plugin.bloodMoon().isActive(p.getWorld())) {
                rollChance *= plugin.bloodSettings().getDouble(BloodMoonSettings.Setting.SCARE_BOOST);
            }
            if (ThreadLocalRandom.current().nextDouble() < rollChance) {
                plugin.scares().perform(p, ScareType.RANDOM);
                plugin.scares().markAutoScared(p);
            }
        }
    }

    private boolean isNight(World w) {
        if (w.getEnvironment() != World.Environment.NORMAL) return true; // Nether/End are always "night"
        long t = w.getTime();
        return t >= 13000 && t <= 23000;
    }
}

package us.copestudios.copenights;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import us.copestudios.copenights.BloodMoonSettings.Setting;

/**
 * Custom Coni death messages:
 *  horde     - killed by a horde mob
 *  bloodmoon - killed by any other blood moon mob
 *  mob       - killed by a normal mob (or a boss)
 *  player    - killed by another player
 * Other deaths (falling, lava...) keep the vanilla message.
 */
public class DeathMessageListener implements Listener {

    private final CopeNights plugin;

    public DeathMessageListener(CopeNights plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // setDeathMessage(String) works on Paper, Purpur, Spigot and Arclight
    public void onDeath(PlayerDeathEvent e) {
        BloodMoonSettings s = plugin.bloodSettings();
        if (!s.getBool(Setting.DEATH_MESSAGES)) return;

        Player victim = e.getEntity();
        Entity killer = resolveKiller(victim);
        if (killer == null) return;

        BloodMoonManager bm = plugin.bloodMoon();
        String type;
        if (killer instanceof Player) type = "player";
        else if (bm.isHordeMob(killer)) type = "horde";
        else if (bm.isBloodMob(killer)) type = "bloodmoon";
        else if (killer instanceof LivingEntity) type = "mob";
        else return;

        List<String> lines = s.deathMessages(type);
        if (lines.isEmpty()) return;
        String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));

        line = line.replace("%player%", victim.getName())
                .replace("%mob%", nameOf(killer))
                .replace("%killer%", nameOf(killer))
                .replace("%wave%", String.valueOf(Math.max(1, bm.wave(victim.getWorld()))));
        if (killer instanceof Player k) line = line.replace("%weapon%", weaponOf(k));

        e.setDeathMessage(CopeNights.color(line));
    }

    private Entity resolveKiller(Player victim) {
        if (victim.getKiller() != null) return victim.getKiller();
        EntityDamageEvent last = victim.getLastDamageCause();
        if (!(last instanceof EntityDamageByEntityEvent ede)) return null;
        Entity d = ede.getDamager();
        if (d instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Entity shooter) return shooter;
        }
        return d;
    }

    @SuppressWarnings("deprecation")
    private static String nameOf(Entity e) {
        if (e instanceof Player p) return p.getName();
        String custom = e.getCustomName();
        if (custom != null && !custom.isBlank()) return custom;
        return pretty(e.getType().name());
    }

    @SuppressWarnings("deprecation")
    private static String weaponOf(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType() == Material.AIR) return "their fists";
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        return pretty(it.getType().name());
    }

    private static String pretty(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String part : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}

package us.copestudios.copenights.bloodmoon;

import org.bukkit.ChatColor;
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
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;
import us.copestudios.copecore.text.Text;
import us.copestudios.copenights.CopeNights;
import us.copestudios.copenights.bloodmoon.BloodMoonSettings.Setting;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coni death messages (messages.yml "death:"), picked at random:
 * <ul>
 *   <li>horde     - killed by a horde mob</li>
 *   <li>bloodmoon - killed by any other Blood Moon mob</li>
 *   <li>mob       - killed by a normal mob (or a boss)</li>
 *   <li>player    - killed by another player</li>
 * </ul>
 * Other deaths (falling, lava...) keep the normal message.
 *
 * <p>SECURITY FIX: renamed mobs and renamed weapons are now inserted as plain, escaped text. Before, a sword
 * renamed in an anvil could carry color codes or tags into everyone's chat.</p>
 */
public final class DeathMessageListener implements Listener {

    private final CopeNights plugin;

    public DeathMessageListener(CopeNights plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // setDeathMessage(String) works on Spigot, Paper, Purpur and Arclight
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.bloodSettings().getBool(Setting.DEATH_MESSAGES)) {
            return;
        }
        Player victim = event.getEntity();
        Entity killer = resolveKiller(victim);
        if (killer == null) {
            return;
        }

        BloodMoonManager bloodMoon = plugin.bloodMoon();
        String type;
        if (killer instanceof Player) {
            type = "player";
        } else if (bloodMoon.isHordeMob(killer)) {
            type = "horde";
        } else if (bloodMoon.isBloodMob(killer)) {
            type = "bloodmoon";
        } else if (killer instanceof LivingEntity) {
            type = "mob";
        } else {
            return;
        }

        // An empty list (or a missing key) means: keep the normal death message.
        List<String> lines = plugin.messages().file().get().getStringList("death." + type);
        if (lines.isEmpty()) {
            return;
        }
        String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        String killerName = safe(nameOf(killer));
        line = Text.replace(line,
                "player", victim.getName(),
                "mob", killerName,
                "killer", killerName,
                "weapon", safe(weaponOf(killer)),
                "wave", String.valueOf(Math.max(1, bloodMoon.wave(victim.getWorld()))));
        event.setDeathMessage(Text.legacy(line));
    }

    /** The player who got the kill, or the entity (or projectile shooter) that dealt the last hit. */
    private static Entity resolveKiller(Player victim) {
        if (victim.getKiller() != null) {
            return victim.getKiller();
        }
        EntityDamageEvent last = victim.getLastDamageCause();
        if (!(last instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Entity shooter) {
                return shooter;
            }
        }
        return damager;
    }

    /** Removes colors and disables tags, so a name can never change the message around it. */
    private static String safe(String name) {
        String plain = ChatColor.stripColor(name);
        return Text.escape(plain == null ? "" : plain);
    }

    @SuppressWarnings("deprecation") // getCustomName() is the cross-platform way
    private static String nameOf(Entity entity) {
        if (entity instanceof Player player) {
            return player.getName();
        }
        String custom = entity.getCustomName();
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        return pretty(entity.getType().name());
    }

    /** What the killer held: the item's name, or messages.yml "death.fists" for an empty hand. */
    @SuppressWarnings("deprecation")
    private String weaponOf(Entity killer) {
        ItemStack item = null;
        if (killer instanceof Player player) {
            item = player.getInventory().getItemInMainHand();
        } else if (killer instanceof LivingEntity living) {
            EntityEquipment equipment = living.getEquipment();
            item = equipment == null ? null : equipment.getItemInMainHand();
        }
        if (item == null || item.getType() == Material.AIR) {
            return Text.plain(plugin.messages().format("death.fists"));
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return pretty(item.getType().name());
    }

    /** "WITHER_SKELETON" -> "Wither Skeleton" */
    private static String pretty(String enumName) {
        StringBuilder out = new StringBuilder();
        for (String part : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}

package us.copestudios.copenights;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/** Keeps Herobrine bodies untouchable and cleans up anything left behind. */
public class ScareListener implements Listener {

    private final CopeNights plugin;

    public ScareListener(CopeNights plugin) {
        this.plugin = plugin;
    }

    private boolean isScareEntity(Entity e) {
        return e.getPersistentDataContainer().has(plugin.scareEntityKey(), PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (isScareEntity(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(EntityCombustEvent e) {
        if (isScareEntity(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (isScareEntity(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.herobrine().remove(e.getPlayer().getUniqueId());
        plugin.scares().extra().onQuit(e.getPlayer());
    }

    /** Sky drop never hurts. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && e.getEntity() instanceof Player p
                && plugin.scares().extra().isFallProtected(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /** Fake TNT can never really explode. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (isScareEntity(e.getEntity())) {
            e.setCancelled(true);
            e.getEntity().remove();
        }
    }

    /** Safety net: removes any leftover bodies (e.g. after a crash). */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity ent : e.getEntities()) {
            if (isScareEntity(ent) && !plugin.herobrine().isActiveEntity(ent)) ent.remove();
        }
    }
}

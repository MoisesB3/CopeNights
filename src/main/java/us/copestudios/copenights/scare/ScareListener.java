package us.copestudios.copenights.scare;

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
import us.copestudios.copenights.CopeNights;

/**
 * Keeps the fake scare entities (Herobrine bodies, fake TNT) untouchable, stops sky-drop fall damage,
 * and cleans up anything left behind (for example after a crash).
 */
public final class ScareListener implements Listener {

    private final CopeNights plugin;

    public ScareListener(CopeNights plugin) {
        this.plugin = plugin;
    }

    private boolean isScareEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(plugin.scareEntityKey(), PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isScareEntity(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        // Sky drop never hurts.
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && event.getEntity() instanceof Player player
                && plugin.scares().extra().isFallProtected(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(EntityCombustEvent event) {
        if (isScareEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (isScareEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Fake TNT can never really explode. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (isScareEntity(event.getEntity())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.herobrine().remove(event.getPlayer().getUniqueId());
        plugin.scares().extra().onQuit(event.getPlayer());
    }

    /** Safety net: removes leftover bodies when chunks load (e.g. after a crash). */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isScareEntity(entity) && !plugin.herobrine().isActiveEntity(entity)) {
                entity.remove();
            }
        }
    }
}

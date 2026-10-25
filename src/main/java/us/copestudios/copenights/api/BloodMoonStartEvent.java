package us.copestudios.copenights.api;

import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired just before a Blood Moon starts in a world (automatic or /coni bloodmoon start).
 * Cancel it to stop this Blood Moon from happening (for example while an event is running).
 *
 * <pre>{@code
 * @EventHandler
 * public void onBloodMoon(BloodMoonStartEvent event) {
 *     if (myEventRunning) event.setCancelled(true);
 * }
 * }</pre>
 */
public final class BloodMoonStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final boolean forced;
    private boolean cancelled;

    public BloodMoonStartEvent(World world, boolean forced) {
        this.world = world;
        this.forced = forced;
    }

    /** The world the Blood Moon starts in. */
    public World getWorld() {
        return world;
    }

    /** True if an admin started it by hand (command or menu) instead of the day counter. */
    public boolean isForced() {
        return forced;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

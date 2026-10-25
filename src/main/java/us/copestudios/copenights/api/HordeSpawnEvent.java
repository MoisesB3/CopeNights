package us.copestudios.copenights.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired before a horde is sent at a player (automatic waves, /coni bloodmoon horde, and the menu button).
 * Cancel it to protect the player, or change the size.
 *
 * <p>Automatic hordes already skip players with a CopeCore state from bloodmoon.yml "horde-skip-states"
 * (in a duel, in an arena...), so most plugins never need this.</p>
 */
public final class HordeSpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player target;
    private final int wave;
    private int size;
    private boolean cancelled;

    public HordeSpawnEvent(Player target, int size, int wave) {
        this.target = target;
        this.size = size;
        this.wave = wave;
    }

    /** The player the horde hunts. */
    public Player getTarget() {
        return target;
    }

    /** Wave number (1 for hand-summoned hordes outside a Blood Moon). */
    public int getWave() {
        return wave;
    }

    /** Mobs in the horde, not counting the captain. */
    public int getSize() {
        return size;
    }

    /** Changes the number of mobs (0-100). */
    public void setSize(int size) {
        this.size = Math.max(0, Math.min(100, size));
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

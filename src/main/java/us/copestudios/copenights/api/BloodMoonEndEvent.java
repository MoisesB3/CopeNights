package us.copestudios.copenights.api;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.List;

/**
 * Fired when a Blood Moon ends in a world (dawn, /coni bloodmoon stop, or the server shutting down).
 * Use it to give your own rewards, update stats, etc.
 */
public final class BloodMoonEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final int kills;
    private final int waves;
    private final List<Player> survivors;
    private final boolean natural;

    public BloodMoonEndEvent(World world, int kills, int waves, List<Player> survivors, boolean natural) {
        this.world = world;
        this.kills = kills;
        this.waves = waves;
        this.survivors = Collections.unmodifiableList(survivors);
        this.natural = natural;
    }

    public World getWorld() {
        return world;
    }

    /** Blood Moon mobs killed during the night. */
    public int getKills() {
        return kills;
    }

    /** How many horde waves came. */
    public int getWaves() {
        return waves;
    }

    /** Players in the world who never died during this Blood Moon. */
    public List<Player> getSurvivors() {
        return survivors;
    }

    /** True at dawn. False when an admin stopped it or the server is shutting down. */
    public boolean isNatural() {
        return natural;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

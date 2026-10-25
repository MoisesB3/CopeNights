package us.copestudios.copenights.api;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import us.copestudios.copenights.CopeNights;
import us.copestudios.copenights.ScareType;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The real {@link NightsService}, registered in onEnable with CopeCore's Services. */
public final class NightsServiceImpl implements NightsService {

    private final CopeNights plugin;

    public NightsServiceImpl(CopeNights plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isBloodMoon(World world) {
        return plugin.bloodMoon().isActive(world);
    }

    @Override
    public int wave(World world) {
        return plugin.bloodMoon().wave(world);
    }

    @Override
    public long daysUntilBloodMoon(World world) {
        return plugin.bloodMoon().daysUntilNext(world);
    }

    @Override
    public boolean isBloodMoonMob(Entity entity) {
        return entity != null && plugin.bloodMoon().isBloodMob(entity);
    }

    @Override
    public boolean isHordeMob(Entity entity) {
        return entity != null && plugin.bloodMoon().isHordeMob(entity);
    }

    @Override
    public String scare(Player player, String type) {
        ScareType parsed = ScareType.parse(type);
        if (player == null || parsed == null) {
            return null;
        }
        ScareType ran = plugin.scares().perform(player, parsed);
        return ran == null ? null : ran.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public List<String> scareTypes() {
        return Arrays.stream(ScareType.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    public int spawnHorde(Player target, int size) {
        if (target == null) {
            return 0;
        }
        return plugin.bloodMoon().spawnHorde(target, size, Math.max(1, plugin.bloodMoon().wave(target.getWorld())));
    }
}

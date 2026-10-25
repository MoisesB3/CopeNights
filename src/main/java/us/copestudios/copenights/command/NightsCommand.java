package us.copestudios.copenights.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import us.copestudios.copecore.command.CommandContext;
import us.copestudios.copecore.command.CommandFailure;
import us.copestudios.copecore.command.CopeCommand;
import us.copestudios.copecore.text.Messages;
import us.copestudios.copenights.CopeNights;
import us.copestudios.copenights.ScareType;
import us.copestudios.copenights.bloodmoon.BloodMoonManager;
import us.copestudios.copenights.bloodmoon.BloodMoonMenu;
import us.copestudios.copenights.bloodmoon.BloodMoonSettings;
import us.copestudios.copenights.bloodmoon.BloodMoonSettings.Setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /coni (aliases /cosc, /copenights). Everything needs copenights.admin.
 *
 * <pre>
 * /coni &lt;player|all|random&gt; [type]               scare someone (type defaults to random)
 * /coni toggle [on|off]                            server-wide random scares
 * /coni list | status | clear | reload
 * /coni exempt &lt;player&gt;
 * /coni bloodmoon [menu|start [world]|stop|status]   (alias: bm)
 * /coni bloodmoon horde &lt;player|all|random&gt; [size]
 * </pre>
 *
 * <p>Built with CopeCore's {@link CopeCommand}: permissions, usage lines, the clickable help page and tab completion
 * are handled by the framework. The "&lt;player&gt; [type]" form uses {@link CopeCommand#fallback()}, which receives
 * any first word that isn't a sub command name.</p>
 */
public final class NightsCommand {

    private static final String PERMISSION = "copenights.admin";
    private static final List<String> BLOODMOON_ACTIONS = List.of("menu", "start", "stop", "status", "horde");

    private final CopeNights plugin;
    private final Messages messages;

    public NightsCommand(CopeNights plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
    }

    /** Builds the command tree and connects it to "coni" from plugin.yml. */
    public void register() {
        CopeCommand command = new CopeCommand(plugin, "coni", messages);

        command.fallback().permission(PERMISSION)
                .usage("<player|all|random> [type]")
                .descriptionKey("command.help.scare").description("scare someone")
                .minArgs(1)
                .tab(ctx -> ctx.size() <= 1 ? targets() : scareTypes())
                .executes(this::scare);

        command.sub("toggle").permission(PERMISSION).usage("[on|off]")
                .descriptionKey("command.help.toggle").description("random server-wide scares")
                .tab(ctx -> List.of("on", "off"))
                .executes(this::toggle);

        command.sub("list").permission(PERMISSION)
                .descriptionKey("command.help.list").description("all scare types")
                .executes(this::list);

        command.sub("status").permission(PERMISSION)
                .descriptionKey("command.help.status").description("auto scare settings")
                .executes(this::status);

        command.sub("exempt").permission(PERMISSION).usage("<player>").minArgs(1)
                .descriptionKey("command.help.exempt").description("skip a player in auto scares")
                .tab(ctx -> onlineNames())
                .executes(this::exempt);

        command.sub("clear").permission(PERMISSION)
                .descriptionKey("command.help.clear").description("remove every active Herobrine")
                .executes(ctx -> ctx.reply("command.cleared", "count", String.valueOf(plugin.herobrine().removeAll())));

        command.sub("bloodmoon").aliases("bm").permission(PERMISSION)
                .usage("[menu|start|stop|status|horde]")
                .descriptionKey("command.help.bloodmoon").description("menu, start, stop, status or summon a horde")
                .tab(this::bloodMoonTab)
                .executes(this::bloodMoon);

        command.sub("reload").permission(PERMISSION)
                .descriptionKey("command.help.reload").description("reload config.yml, bloodmoon.yml and messages.yml")
                .executes(ctx -> {
                    long started = System.currentTimeMillis();
                    plugin.reload();
                    ctx.reply("generic.reloaded", "plugin", plugin.getName(),
                            "ms", String.valueOf(System.currentTimeMillis() - started));
                });

        command.register();
    }

    // =====================================================================
    //  /coni <player|all|random> [type]
    // =====================================================================

    private void scare(CommandContext ctx) {
        String who = ctx.arg(0);
        ScareType type = ScareType.RANDOM;
        if (ctx.has(1)) {
            type = ScareType.parse(ctx.arg(1));
            if (type == null) {
                throw new CommandFailure("command.unknown-type", "input", ctx.arg(1));
            }
        }

        List<Player> targets = resolveTargets(who);
        if (targets.isEmpty()) {
            throw new CommandFailure("command.no-target", "input", who);
        }

        int done = 0;
        String lastName = "";
        ScareType ran = null;
        for (Player target : targets) {
            ScareType result = plugin.scares().perform(target, type);
            if (result != null) {
                done++;
                lastName = target.getName();
                ran = result;
            }
        }

        if (done == 0) {
            ctx.reply("command.scare-failed");
        } else if (targets.size() == 1) {
            ctx.reply("command.scare-one", "player", lastName, "type", lower(ran));
        } else {
            ctx.reply("command.scare-many", "count", String.valueOf(done), "type", lower(type));
        }
    }

    /** "all"/"*"/"@a", "random"/"@r", or an exact player name. */
    private static List<Player> resolveTargets(String who) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<Player> targets = new ArrayList<>();
        if (who.equalsIgnoreCase("all") || who.equals("*") || who.equalsIgnoreCase("@a")) {
            targets.addAll(online);
        } else if (who.equalsIgnoreCase("random") || who.equalsIgnoreCase("@r")) {
            if (!online.isEmpty()) {
                targets.add(online.get(ThreadLocalRandom.current().nextInt(online.size())));
            }
        } else {
            Player player = Bukkit.getPlayerExact(who);
            if (player != null) {
                targets.add(player);
            }
        }
        return targets;
    }

    // =====================================================================
    //  toggle / list / status / exempt
    // =====================================================================

    private void toggle(CommandContext ctx) {
        boolean on;
        if (ctx.has(0)) {
            String value = ctx.arg(0).toLowerCase(Locale.ROOT);
            if (value.equals("on") || value.equals("true") || value.equals("enable")) {
                on = true;
            } else if (value.equals("off") || value.equals("false") || value.equals("disable")) {
                on = false;
            } else {
                throw new CommandFailure("command.toggle-invalid");
            }
        } else {
            on = !plugin.isAutoEnabled();
        }
        plugin.setAutoEnabled(on);
        if (!on) {
            plugin.herobrine().removeAll();
        }
        ctx.reply("command.toggled", "state", plugin.onOff(on));
    }

    private void list(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        header(sender, "command.list-title");
        // The click suggests "/coni <you> <type>" so you can try a scare on yourself.
        String self = sender instanceof Player player ? player.getName() : "<player>";
        for (ScareType type : ScareType.values()) {
            messages.sendRaw(sender, "command.list-line",
                    "type", lower(type), "description", type.description(), "player", self);
        }
        messages.sendRaw(sender, "common.footer");
    }

    private void status(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        YamlConfiguration config = plugin.settings().get();
        header(sender, "command.status-title");
        messages.sendRaw(sender, "command.status-enabled", "state", plugin.onOff(plugin.isAutoEnabled()));
        messages.sendRaw(sender, "command.status-timing",
                "interval", String.valueOf(config.getLong("auto.check-interval-seconds")),
                "chance", String.valueOf(Math.round(config.getDouble("auto.chance-per-check") * 100)),
                "cooldown", String.valueOf(config.getLong("auto.player-cooldown-seconds")));
        messages.sendRaw(sender, "command.status-filters",
                "night", plugin.onOff(config.getBoolean("auto.night-only")),
                "dark", plugin.onOff(config.getBoolean("auto.dark-only")),
                "survival", plugin.onOff(config.getBoolean("auto.survival-only")));
        List<String> skip = config.getStringList("auto.skip-states");
        messages.sendRaw(sender, "command.status-skip", "states", skip.isEmpty() ? "-" : String.join(", ", skip));
        messages.sendRaw(sender, "common.footer");
    }

    private void exempt(CommandContext ctx) {
        Player target = ctx.onlinePlayer(0);
        boolean nowExempt = plugin.toggleExempt(target.getUniqueId());
        ctx.reply(nowExempt ? "command.exempt-on" : "command.exempt-off", "player", target.getName());
    }

    // =====================================================================
    //  /coni bloodmoon ...
    // =====================================================================

    private void bloodMoon(CommandContext ctx) {
        String action = ctx.argOr(0, "menu").toLowerCase(Locale.ROOT);
        switch (action) {
            case "menu", "gui" -> {
                if (!ctx.isPlayer()) {
                    throw new CommandFailure("command.bm-menu-console");
                }
                new BloodMoonMenu(plugin, ctx.player(), BloodMoonMenu.Page.MAIN).open();
            }
            case "start" -> bloodMoonStart(ctx);
            case "stop" -> {
                BloodMoonManager bloodMoon = plugin.bloodMoon();
                int stopped = 0;
                for (World world : bloodMoon.activeWorlds()) {
                    bloodMoon.end(world, true);
                    stopped++;
                }
                ctx.reply("command.bm-stopped", "count", String.valueOf(stopped));
            }
            case "status" -> bloodMoonStatus(ctx.sender());
            case "horde" -> horde(ctx);
            default -> throw new CommandFailure("command.bm-unknown");
        }
    }

    private void bloodMoonStart(CommandContext ctx) {
        BloodMoonManager bloodMoon = plugin.bloodMoon();
        World world = null;
        if (ctx.has(1)) {
            world = Bukkit.getWorld(ctx.arg(1));
        } else if (ctx.isPlayer()) {
            world = ctx.player().getWorld();
        } else if (!bloodMoon.eligibleWorlds().isEmpty()) {
            world = bloodMoon.eligibleWorlds().get(0);
        }
        if (world == null || !bloodMoon.eligibleWorlds().contains(world)) {
            throw new CommandFailure("command.bm-bad-world");
        }
        if (bloodMoon.forceStart(world)) {
            ctx.reply("command.bm-started", "world", world.getName());
        } else {
            ctx.reply("command.bm-cancelled", "world", world.getName());
        }
    }

    private void bloodMoonStatus(CommandSender sender) {
        BloodMoonManager bloodMoon = plugin.bloodMoon();
        BloodMoonSettings settings = plugin.bloodSettings();
        header(sender, "command.bm-status-title");
        messages.sendRaw(sender, "command.bm-status-enabled",
                "state", plugin.onOff(settings.getBool(Setting.ENABLED)),
                "days", String.valueOf(settings.getInt(Setting.EVERY_DAYS)));
        List<World> worlds = bloodMoon.eligibleWorlds();
        if (worlds.isEmpty()) {
            messages.sendRaw(sender, "command.bm-status-no-worlds");
        }
        for (World world : worlds) {
            if (bloodMoon.isActive(world)) {
                messages.sendRaw(sender, "command.bm-status-active",
                        "world", world.getName(), "wave", String.valueOf(bloodMoon.wave(world)));
            } else {
                long days = bloodMoon.daysUntilNext(world);
                if (days == 0) {
                    messages.sendRaw(sender, "command.bm-status-tonight", "world", world.getName());
                } else {
                    messages.sendRaw(sender, "command.bm-status-later", "world", world.getName(), "days", String.valueOf(days));
                }
            }
        }
        messages.sendRaw(sender, "common.footer");
    }

    /** /coni bloodmoon horde <player|all|random> [size] */
    private void horde(CommandContext ctx) {
        if (!ctx.has(1)) {
            throw new CommandFailure("generic.usage", "usage", "/" + ctx.label() + " bloodmoon horde \\<player|all|random> [size]");
        }
        int size = ctx.has(2)
                ? ctx.integer(2, 1, 100)
                : plugin.bloodSettings().getInt(Setting.HORDE_SIZE);
        List<Player> targets = resolveTargets(ctx.arg(1));
        if (targets.isEmpty()) {
            throw new CommandFailure("command.no-target", "input", ctx.arg(1));
        }
        BloodMoonManager bloodMoon = plugin.bloodMoon();
        int total = 0;
        for (Player target : targets) {
            total += bloodMoon.spawnHorde(target, size, Math.max(1, bloodMoon.wave(target.getWorld())));
        }
        String who = targets.size() == 1 ? targets.get(0).getName() : targets.size() + " players";
        ctx.reply("command.horde-done", "target", who, "count", String.valueOf(total));
    }

    /** Tab completion for /coni bloodmoon (ctx args start AFTER "bloodmoon"). */
    private List<String> bloodMoonTab(CommandContext ctx) {
        if (ctx.size() <= 1) {
            return BLOODMOON_ACTIONS;
        }
        String action = ctx.arg(0).toLowerCase(Locale.ROOT);
        if (action.equals("start") && ctx.size() == 2) {
            return plugin.bloodMoon().eligibleWorlds().stream().map(World::getName).toList();
        }
        if (action.equals("horde")) {
            if (ctx.size() == 2) {
                return targets();
            }
            if (ctx.size() == 3) {
                return List.of("10", "20", "30", "50");
            }
        }
        return List.of();
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private void header(CommandSender sender, String titleKey) {
        messages.sendRaw(sender, "common.header", "title", messages.format(titleKey));
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
        return names;
    }

    private static List<String> targets() {
        List<String> options = new ArrayList<>(List.of("all", "random"));
        options.addAll(onlineNames());
        return options;
    }

    private static List<String> scareTypes() {
        return Arrays.stream(ScareType.values()).map(NightsCommand::lower).toList();
    }

    private static String lower(ScareType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}

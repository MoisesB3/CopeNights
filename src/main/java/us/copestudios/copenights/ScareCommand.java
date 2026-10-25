package us.copestudios.copenights;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /coni <player|all|random> [type]
 * /coni toggle [on|off]
 * /coni status | list | clear | reload
 * /coni exempt <player>
 * /coni bloodmoon [menu|start|stop|status]
 * /coni bloodmoon horde <player|all|random> [size]
 */
public class ScareCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("help", "toggle", "status", "list", "exempt", "clear", "reload", "bloodmoon", "bm");
    private static final List<String> BM_SUBS = List.of("menu", "start", "stop", "status", "horde");

    private final CopeNights plugin;

    public ScareCommand(CopeNights plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("copenights.admin")) {
            sender.sendMessage(plugin.prefix() + C.R + "No permission.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "toggle" -> toggle(sender, args);
            case "status" -> status(sender);
            case "list" -> list(sender);
            case "exempt" -> exempt(sender, args);
            case "clear" -> {
                int n = plugin.herobrine().removeAll();
                sender.sendMessage(plugin.prefix() + "Removed " + C.H + n + C.E + " active Herobrine(s).");
            }
            case "bloodmoon", "bm" -> bloodmoon(sender, args);
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.prefix() + C.ON + "Config reloaded.");
            }
            default -> scare(sender, args);
        }
        return true;
    }

    private void help(CommandSender s, String label) {
        s.sendMessage("");
        s.sendMessage(C.header("Cope Nights"));
        s.sendMessage(C.GRAY + "  Click a command to type it.");
        s.sendMessage("");
        s.sendMessage(C.gradient("§l Scares", C.ORANGE_HEX, C.DARK_RED_HEX));
        cmd(s, label, "", "<player|all|random> [type]", "scare someone", "/" + label + " ");
        cmd(s, label, "toggle", "[on|off]", "random server-wide scares", null);
        cmd(s, label, "list", "", "all scare types", null);
        cmd(s, label, "status", "", "auto scare settings", null);
        cmd(s, label, "exempt", "<player>", "skip a player in auto scares", null);
        cmd(s, label, "clear", "", "remove every active Herobrine", null);
        s.sendMessage("");
        s.sendMessage(C.gradient("§l Blood Moon", C.ORANGE_HEX, C.DARK_RED_HEX));
        cmd(s, label, "bloodmoon", "", "open the settings menu", null);
        cmd(s, label, "bloodmoon", "<start|stop|status>", "control the Blood Moon", "/" + label + " bloodmoon ");
        cmd(s, label, "bloodmoon horde", "<player|all|random> [size]", "summon a horde", null);
        s.sendMessage("");
        cmd(s, label, "reload", "", "reload config.yml + bloodmoon.yml", null);
        s.sendMessage(C.footer());
    }

    /** One help line. Players get it clickable with a hover tip. */
    private void cmd(CommandSender s, String label, String sub, String args, String desc, String suggest) {
        String full = "/" + label + (sub.isEmpty() ? "" : " " + sub);
        String text = C.R + "  ▸ " + C.O + full + (args.isEmpty() ? "" : " " + C.GRAY + args)
                + " " + C.BLOOD + "- " + C.E + desc;
        if (!(s instanceof Player p)) {
            s.sendMessage(text);
            return;
        }
        String typed = suggest != null ? suggest : full + (args.isEmpty() ? "" : " ");
        TextComponent line = new TextComponent(TextComponent.fromLegacyText(text));
        line.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, typed));
        line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(
                C.E + "Click to type " + C.O + typed.trim() + "\n" + C.GRAY + desc))));
        p.spigot().sendMessage(line);
    }

    private void toggle(CommandSender s, String[] args) {
        boolean on;
        if (args.length >= 2) {
            String v = args[1].toLowerCase(Locale.ROOT);
            if (v.equals("on") || v.equals("true") || v.equals("enable")) on = true;
            else if (v.equals("off") || v.equals("false") || v.equals("disable")) on = false;
            else {
                s.sendMessage(plugin.prefix() + C.R + "Use on or off.");
                return;
            }
        } else {
            on = !plugin.isAutoEnabled();
        }
        plugin.setAutoEnabled(on);
        if (!on) plugin.herobrine().removeAll();
        s.sendMessage(plugin.prefix() + "Server-wide random scares: " + (on ? C.onOff(true) : C.onOff(false)));
    }

    private void status(CommandSender s) {
        var c = plugin.getConfig();
        s.sendMessage(C.header("Auto Scares"));
        s.sendMessage(C.R + "  ▸ " + C.E + "Server-wide: " + C.onOff(plugin.isAutoEnabled()));
        s.sendMessage(C.R + "  ▸ " + C.E + "Roll every " + C.H + c.getLong("auto.check-interval-seconds") + "s"
                + C.BLOOD + " | " + C.E + "Chance " + C.H + Math.round(c.getDouble("auto.chance-per-check") * 100) + "%"
                + C.BLOOD + " | " + C.E + "Cooldown " + C.H + c.getLong("auto.player-cooldown-seconds") + "s");
        s.sendMessage(C.R + "  ▸ " + C.E + "Night only " + C.onOff(c.getBoolean("auto.night-only"))
                + C.BLOOD + "  " + C.E + "Dark only " + C.onOff(c.getBoolean("auto.dark-only"))
                + C.BLOOD + "  " + C.E + "Survival only " + C.onOff(c.getBoolean("auto.survival-only")));
        s.sendMessage(C.footer());
    }

    private void list(CommandSender s) {
        s.sendMessage(C.header("Scare Types"));
        for (ScareType t : ScareType.values()) {
            String name = t.name().toLowerCase(Locale.ROOT);
            String text = C.R + "  ▸ " + C.gradient(name, C.ORANGE_HEX, C.DARK_RED_HEX) + " " + C.BLOOD + "- " + C.E + t.description();
            if (s instanceof Player p) {
                TextComponent line = new TextComponent(TextComponent.fromLegacyText(text));
                line.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/coni " + p.getName() + " " + name));
                line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(
                        C.E + "Click to try " + C.O + name + C.E + " on yourself"))));
                p.spigot().sendMessage(line);
            } else {
                s.sendMessage(text);
            }
        }
        s.sendMessage(C.footer());
    }

    private void exempt(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(plugin.prefix() + C.R + "Usage: /coni exempt <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            s.sendMessage(plugin.prefix() + C.R + "Player not online.");
            return;
        }
        boolean now = plugin.toggleExempt(target.getUniqueId());
        s.sendMessage(plugin.prefix() + C.H + target.getName() + C.E + " is " + (now ? C.ON + "now exempt" : C.R + "no longer exempt")
                + C.E + " from auto scares.");
    }

    private void bloodmoon(CommandSender s, String[] args) {
        BloodMoonManager bm = plugin.bloodMoon();
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "menu";
        switch (action) {
            case "menu", "gui" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage(plugin.prefix() + C.R + "The menu is in-game only. Use start/stop/status.");
                    return;
                }
                BloodMoonMenu.open(plugin, p, BloodMoonMenu.Page.MAIN);
            }
            case "start" -> {
                org.bukkit.World w = null;
                if (args.length >= 3) w = Bukkit.getWorld(args[2]);
                else if (s instanceof Player p) w = p.getWorld();
                else if (!bm.eligibleWorlds().isEmpty()) w = bm.eligibleWorlds().get(0);
                if (w == null || !bm.eligibleWorlds().contains(w)) {
                    s.sendMessage(plugin.prefix() + C.R + "That world can't have blood moons (see worlds: in bloodmoon.yml).");
                    return;
                }
                bm.forceStart(w);
                s.sendMessage(plugin.prefix() + C.R + "Blood moon started in " + C.H + w.getName());
            }
            case "stop" -> {
                int n = 0;
                for (org.bukkit.World w : bm.activeWorlds()) {
                    bm.end(w, true);
                    n++;
                }
                s.sendMessage(plugin.prefix() + "Stopped " + C.H + n + C.E + " blood moon(s).");
            }
            case "status" -> {
                var bs = plugin.bloodSettings();
                s.sendMessage(C.header("Blood Moon"));
                s.sendMessage(C.R + "  ▸ " + C.E + "Enabled " + C.onOff(bs.getBool(BloodMoonSettings.Setting.ENABLED))
                        + C.BLOOD + " | " + C.E + "Every " + C.H + bs.getInt(BloodMoonSettings.Setting.EVERY_DAYS) + C.E + " days");
                for (org.bukkit.World w : bm.eligibleWorlds()) {
                    long d = bm.daysUntilNext(w);
                    String state = bm.isActive(w)
                            ? C.gradient("§lACTIVE", "FF2A00", C.DARK_RED_HEX) + C.E + " (wave " + C.H + bm.wave(w) + C.E + ")"
                            : d == 0 ? C.O + "tonight" : C.E + "in " + C.H + d + C.E + " day(s)";
                    s.sendMessage(C.R + "  ▸ " + C.H + w.getName() + C.BLOOD + " » " + state);
                }
                s.sendMessage(C.footer());
            }
            case "horde" -> {
                if (args.length < 3) {
                    s.sendMessage(plugin.prefix() + C.R + "Usage: /coni bm horde <player|all|random> [size]");
                    return;
                }
                int size = plugin.bloodSettings().getInt(BloodMoonSettings.Setting.HORDE_SIZE);
                if (args.length >= 4) {
                    try {
                        size = Math.max(1, Math.min(100, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ex) {
                        s.sendMessage(plugin.prefix() + C.R + "Size must be a number.");
                        return;
                    }
                }
                List<Player> targets = resolveTargets(args[2]);
                if (targets.isEmpty()) {
                    s.sendMessage(plugin.prefix() + C.R + "No matching online player: " + C.H + args[2]);
                    return;
                }
                int total = 0;
                for (Player p : targets) total += bm.spawnHorde(p, size, Math.max(1, bm.wave(p.getWorld())));
                s.sendMessage(plugin.prefix() + C.R + "Horde unleashed" + C.E + " on " + C.H + (targets.size() == 1
                        ? targets.get(0).getName() : targets.size() + " players") + C.E + " (" + total + " mobs).");
            }
            default -> s.sendMessage(plugin.prefix() + C.R + "Use /coni bloodmoon [menu|start|stop|status|horde]");
        }
    }

    private void scare(CommandSender s, String[] args) {
        String who = args[0];
        ScareType type = ScareType.RANDOM;
        if (args.length >= 2) {
            type = ScareType.parse(args[1]);
            if (type == null) {
                s.sendMessage(plugin.prefix() + C.R + "Unknown type. Try " + C.H + "/coni list");
                return;
            }
        }

        List<Player> targets = resolveTargets(who);

        if (targets.isEmpty()) {
            s.sendMessage(plugin.prefix() + C.R + "No matching online player: " + C.H + who);
            return;
        }

        int done = 0;
        String lastName = "";
        ScareType ran = null;
        for (Player p : targets) {
            ScareType r = plugin.scares().perform(p, type);
            if (r != null) {
                done++;
                lastName = p.getName();
                ran = r;
            }
        }

        if (done == 0) {
            s.sendMessage(plugin.prefix() + C.R + "Couldn't run that scare right now.");
        } else if (targets.size() == 1) {
            s.sendMessage(plugin.prefix() + "Hit " + C.H + lastName + C.E + " with " + C.R + ran.name().toLowerCase(Locale.ROOT) + C.E + ".");
        } else {
            s.sendMessage(plugin.prefix() + "Scared " + C.H + done + C.E + " player(s) with " + C.R + type.name().toLowerCase(Locale.ROOT) + C.E + ".");
        }
    }

    private List<Player> resolveTargets(String who) {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        List<Player> targets = new ArrayList<>();
        if (who.equalsIgnoreCase("all") || who.equals("*") || who.equalsIgnoreCase("@a")) {
            targets.addAll(online);
        } else if (who.equalsIgnoreCase("random") || who.equalsIgnoreCase("@r")) {
            if (!online.isEmpty()) {
                List<Player> list = new ArrayList<>(online);
                targets.add(list.get(ThreadLocalRandom.current().nextInt(list.size())));
            }
        } else {
            Player p = Bukkit.getPlayerExact(who);
            if (p != null) targets.add(p);
        }
        return targets;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("copenights.admin")) return List.of();

        if (args.length == 1) {
            List<String> opts = new ArrayList<>(SUBS);
            opts.add("all");
            opts.add("random");
            Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
            return filter(opts, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("toggle")) return filter(List.of("on", "off"), args[1]);
            if (sub.equals("bloodmoon") || sub.equals("bm")) return filter(BM_SUBS, args[1]);
            if (sub.equals("exempt")) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                return filter(names, args[1]);
            }
            if (SUBS.contains(sub)) return List.of();
            return filter(Arrays.stream(ScareType.values())
                    .map(t -> t.name().toLowerCase(Locale.ROOT)).toList(), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("bloodmoon") || args[0].equalsIgnoreCase("bm"))
                && args[1].equalsIgnoreCase("horde")) {
            List<String> opts = new ArrayList<>(List.of("all", "random"));
            Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
            return filter(opts, args[2]);
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("bloodmoon") || args[0].equalsIgnoreCase("bm"))
                && args[1].equalsIgnoreCase("horde")) {
            return filter(List.of("10", "20", "30", "50"), args[3]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("bloodmoon") || args[0].equalsIgnoreCase("bm"))
                && args[1].equalsIgnoreCase("start")) {
            return filter(plugin.bloodMoon().eligibleWorlds().stream().map(org.bukkit.World::getName).toList(), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).toList();
    }
}

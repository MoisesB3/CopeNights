package us.copestudios.copenights.bloodmoon;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import us.copestudios.copecore.gui.ItemBuilder;
import us.copestudios.copecore.gui.Menu;
import us.copestudios.copecore.gui.MenuClick;
import us.copestudios.copecore.text.Messages;
import us.copestudios.copecore.text.Text;
import us.copestudios.copecore.util.Attributes;
import us.copestudios.copenights.CopeNights;
import us.copestudios.copenights.bloodmoon.BloodMoonSettings.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The in-game editor for bloodmoon.yml: /coni bloodmoon.
 *
 * <h2>Pages</h2>
 * <ul>
 *   <li>MAIN   - every non-horde setting, start/stop, summon a horde, status, and links to the other pages</li>
 *   <li>HORDES - wave/horde settings</li>
 *   <li>MOBS   - which special mobs can appear</li>
 * </ul>
 *
 * <h2>Controls (from CopeCore)</h2>
 * <p>On/off settings: click. Numbers: left +step, right -step, shift ×10, Q to type an exact value in chat.
 * All texts are in messages.yml under "menu:".</p>
 *
 * <p>Built on CopeCore's {@link Menu}: it's recognised by its Java object (not the title), items can't be taken,
 * and opening another page from a click is safely delayed one tick.</p>
 */
public final class BloodMoonMenu extends Menu {

    public enum Page { MAIN, HORDES, MOBS }

    /** Every mob that can be picked as a "special" Blood Moon spawn (ones missing on this version are greyed out). */
    public static final List<String> MOB_CHOICES = List.of(
            "ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "SKELETON", "STRAY", "BOGGED",
            "WITHER_SKELETON", "CREEPER", "SPIDER", "CAVE_SPIDER", "WITCH", "ENDERMAN",
            "SILVERFISH", "SLIME", "MAGMA_CUBE", "PHANTOM", "BLAZE", "VEX", "BREEZE",
            "PILLAGER", "VINDICATOR", "EVOKER", "RAVAGER", "PIGLIN_BRUTE", "ZOMBIFIED_PIGLIN",
            "HOGLIN", "ZOGLIN", "GUARDIAN", "WARDEN");

    // Bottom row buttons
    private static final int SLOT_START = 45;
    private static final int SLOT_STOP = 46;
    private static final int SLOT_HORDE = 47;
    private static final int SLOT_HORDE_PAGE = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_MOBS = 52;
    private static final int SLOT_CLOSE = 53;

    private final CopeNights nights;
    private final Messages messages;
    private final Page page;

    public BloodMoonMenu(CopeNights plugin, Player viewer, Page page) {
        super(plugin, viewer, 6, plugin.messages().format(switch (page) {
            case MAIN -> "menu.title-main";
            case HORDES -> "menu.title-hordes";
            case MOBS -> "menu.title-mobs";
        }));
        this.nights = plugin;
        this.messages = plugin.messages();
        this.page = page;
    }

    // =====================================================================
    //  Building
    // =====================================================================

    @Override
    protected void build() {
        switch (page) {
            case MAIN -> buildMain();
            case HORDES -> buildHordes();
            case MOBS -> buildMobs();
        }
        fillPanes();
    }

    private void buildMain() {
        buildSettings();
        BloodMoonManager bloodMoon = nights.bloodMoon();

        set(SLOT_START, item(Material.LIME_CONCRETE, "menu.start-name", lore("menu.start-lore")),
                guarded(click -> {
                    World world = viewer.getWorld();
                    if (!bloodMoon.eligibleWorlds().contains(world)) {
                        messages.send(viewer, "menu.not-eligible");
                        return;
                    }
                    if (bloodMoon.forceStart(world)) {
                        messages.send(viewer, "command.bm-started", "world", world.getName());
                    }
                    refresh();
                }));
        set(SLOT_STOP, item(Material.RED_CONCRETE, "menu.stop-name", lore("menu.stop-lore")),
                guarded(click -> {
                    int stopped = 0;
                    for (World world : bloodMoon.activeWorlds()) {
                        bloodMoon.end(world, true);
                        stopped++;
                    }
                    messages.send(viewer, "command.bm-stopped", "count", String.valueOf(stopped));
                    refresh();
                }));
        set(SLOT_HORDE, item(Material.ZOMBIE_HEAD, "menu.horde-name", lore("menu.horde-lore")), guarded(this::summonHorde));
        set(SLOT_HORDE_PAGE, item(Material.GOAT_HORN, "menu.horde-page-name", lore("menu.horde-page-lore")),
                guarded(click -> new BloodMoonMenu(nights, viewer, Page.HORDES).open()));
        set(SLOT_INFO, item(Material.CLOCK, "menu.status-name", statusLines()));
        set(SLOT_MOBS, item(Material.ZOMBIE_SPAWN_EGG, "menu.mobs-name",
                        lore("menu.mobs-lore", "count", String.valueOf(nights.bloodSettings().specialMobs().size()))),
                guarded(click -> new BloodMoonMenu(nights, viewer, Page.MOBS).open()));
        closeButton(SLOT_CLOSE);
    }

    private void buildHordes() {
        buildSettings();
        set(SLOT_HORDE, item(Material.ZOMBIE_HEAD, "menu.horde-name", lore("menu.horde-lore")), guarded(this::summonHorde));
        backButton(SLOT_BACK, () -> new BloodMoonMenu(nights, viewer, Page.MAIN).open());
    }

    private void buildMobs() {
        List<String> enabled = upper(nights.bloodSettings().specialMobs());
        for (int slot = 0; slot < MOB_CHOICES.size() && slot < 45; slot++) {
            String mob = MOB_CHOICES.get(slot);
            String name = pretty(mob);
            boolean exists = BloodMoonManager.mobType(mob) != null;
            boolean on = enabled.contains(mob);

            Material egg = ItemBuilder.material(mob + "_SPAWN_EGG", Material.PAPER);
            List<String> lines = exists ? lore(on ? "menu.mob-on-lore" : "menu.mob-off-lore") : lore("menu.mob-missing-lore");
            ItemStack icon = ItemBuilder.of(exists ? egg : Material.BARRIER)
                    .name(messages.format(on ? "menu.mob-on-name" : "menu.mob-off-name", "mob", name))
                    .lore(lines).hideFlags().glow(on && exists).build();

            set(slot, icon, guarded(click -> {
                // Mobs that don't exist on this version can still be switched off, never on.
                List<String> list = new ArrayList<>(upper(nights.bloodSettings().specialMobs()));
                if (!list.remove(mob) && exists) {
                    list.add(mob);
                }
                nights.bloodSettings().setSpecialMobs(list);
                refresh();
            }));
        }
        backButton(SLOT_BACK, () -> new BloodMoonMenu(nights, viewer, Page.MAIN).open());
    }

    /** One button per setting that belongs on this page, in {@link Menu#GRID}. */
    private void buildSettings() {
        BloodMoonSettings settings = nights.bloodSettings();
        List<Setting> list = new ArrayList<>();
        for (Setting setting : Setting.values()) {
            if (setting.isHorde() == (page == Page.HORDES)) {
                list.add(setting);
            }
        }

        for (int i = 0; i < list.size() && i < GRID.length; i++) {
            Setting setting = list.get(i);
            String name = settingText(setting, "name", setting.display);
            String description = settingText(setting, "description", setting.description);

            List<String> lines = lore("menu.setting-lore", "description", description, "value", settings.display(setting));
            lines.add("");
            if (setting.isNumber()) {
                lines.addAll(numberHint(setting.step));
            } else {
                lines.add(messages.format("menu.toggle-hint"));
            }
            String needs = setting.requiredVersion();
            if (needs != null && !Attributes.exists("scale")) {
                lines.add("");
                lines.add(messages.format("menu.needs-version", "version", needs));
            }

            ItemStack icon = ItemBuilder.of(setting.icon)
                    .name(messages.format("menu.setting-name", "name", name))
                    .lore(lines).hideFlags()
                    .glow(!setting.isNumber() && settings.getBool(setting))
                    .build();

            set(GRID[i], icon, guarded(click -> {
                if (setting.isNumber()) {
                    adjustNumber(click, Text.plain(name), settings.numberValue(setting), setting.step,
                            setting.min, setting.max, setting.kind == BloodMoonSettings.Kind.INT,
                            value -> settings.set(setting, value));
                } else {
                    settings.toggle(setting);
                    refresh();
                }
            }));
        }
    }

    // =====================================================================
    //  Actions
    // =====================================================================

    private void summonHorde(MenuClick click) {
        close();
        int count = nights.bloodMoon().spawnHorde(viewer, nights.bloodSettings().getInt(Setting.HORDE_SIZE), 1);
        messages.send(viewer, "menu.summoned", "count", String.valueOf(count));
    }

    /**
     * Wraps a click action with a permission check: if an admin loses copenights.admin while the menu is open,
     * the menu just closes.
     */
    private Consumer<MenuClick> guarded(Consumer<MenuClick> action) {
        return click -> {
            if (!viewer.hasPermission("copenights.admin")) {
                close();
                return;
            }
            action.accept(click);
        };
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    /** Same lines as /coni bloodmoon status, for the Status item. */
    private List<String> statusLines() {
        BloodMoonManager bloodMoon = nights.bloodMoon();
        List<String> lines = new ArrayList<>();
        for (World world : bloodMoon.eligibleWorlds()) {
            if (bloodMoon.isActive(world)) {
                lines.add(messages.format("command.bm-status-active",
                        "world", world.getName(), "wave", String.valueOf(bloodMoon.wave(world))));
            } else {
                long days = bloodMoon.daysUntilNext(world);
                lines.add(days == 0
                        ? messages.format("command.bm-status-tonight", "world", world.getName())
                        : messages.format("command.bm-status-later", "world", world.getName(), "days", String.valueOf(days)));
            }
        }
        if (lines.isEmpty()) {
            lines.add(messages.format("command.bm-status-no-worlds"));
        }
        return lines;
    }

    /**
     * Optional per-setting text from messages.yml ("menu.settings.&lt;path&gt;.name" / ".description"),
     * or the English default.
     */
    private String settingText(Setting setting, String part, String fallback) {
        String key = "menu.settings." + setting.path + "." + part;
        return messages.has(key) ? messages.format(key) : fallback;
    }

    /** A list message with placeholders filled in, still as MiniMessage text (ItemBuilder converts it). */
    private List<String> lore(String key, String... pairs) {
        List<String> lines = new ArrayList<>();
        for (String line : messages.templates(key)) {
            lines.add(Text.replace(line, pairs));
        }
        return lines;
    }

    private ItemStack item(Material material, String nameKey, List<String> lore) {
        return ItemBuilder.of(material).name(messages.format(nameKey)).lore(lore).hideFlags().build();
    }

    /** Red border + black filler like the old menu (the Mobs page only has a red bottom row). */
    private void fillPanes() {
        ItemStack red = ItemBuilder.of(Material.RED_STAINED_GLASS_PANE).name(" ").build();
        ItemStack black = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int slot = 0; slot < size(); slot++) {
            if (getInventory().getItem(slot) != null) {
                continue;
            }
            boolean border = page == Page.MOBS
                    ? slot >= 45
                    : slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8;
            set(slot, border ? red : black);
        }
    }

    private static List<String> upper(List<String> names) {
        return names.stream().map(name -> name.toUpperCase(Locale.ROOT)).toList();
    }

    /** "WITHER_SKELETON" -> "Wither Skeleton" */
    private static String pretty(String enumName) {
        StringBuilder out = new StringBuilder();
        for (String part : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}

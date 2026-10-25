package us.copestudios.copenights;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import us.copestudios.copenights.BloodMoonSettings.Kind;
import us.copestudios.copenights.BloodMoonSettings.Setting;

/** Chest GUI for editing bloodmoon.yml in-game. Open with /coni bloodmoon */
public class BloodMoonMenu implements InventoryHolder {

    public enum Page { MAIN, HORDES, MOBS }

    /** Every mob that can be picked as a "special" blood moon spawn. */
    public static final List<String> MOB_CHOICES = List.of(
            "ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "SKELETON", "STRAY", "BOGGED",
            "WITHER_SKELETON", "CREEPER", "SPIDER", "CAVE_SPIDER", "WITCH", "ENDERMAN",
            "SILVERFISH", "SLIME", "MAGMA_CUBE", "PHANTOM", "BLAZE", "VEX", "BREEZE",
            "PILLAGER", "VINDICATOR", "EVOKER", "RAVAGER", "PIGLIN_BRUTE", "ZOMBIFIED_PIGLIN",
            "HOGLIN", "ZOGLIN", "GUARDIAN", "WARDEN");

    private static final int[] SETTING_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private static final int SLOT_START = 45, SLOT_STOP = 46, SLOT_HORDE = 47, SLOT_HORDE_PAGE = 48, SLOT_INFO = 49, SLOT_MOBS = 52, SLOT_CLOSE = 53;
    private static final int SLOT_BACK = 49;

    private final CopeNights plugin;
    private final Page page;
    private final Inventory inv;

    private BloodMoonMenu(CopeNights plugin, Page page) {
        this.plugin = plugin;
        this.page = page;
        String title = switch (page) {
            case MAIN -> C.R + "§lBlood Moon Settings";
            case HORDES -> C.R + "§lHorde Settings";
            case MOBS -> C.R + "§lBlood Moon Mobs";
        };
        this.inv = Bukkit.createInventory(this, 54, title);
        render();
    }

    public static void open(CopeNights plugin, Player p, Page page) {
        p.openInventory(new BloodMoonMenu(plugin, page).inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void render() {
        inv.clear();
        ItemStack pane = item(Material.RED_STAINED_GLASS_PANE, " ");
        ItemStack dark = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            boolean border = page == Page.MOBS
                    ? i >= 45
                    : (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8);
            inv.setItem(i, border ? pane : dark);
        }

        switch (page) {
            case MAIN -> renderMain();
            case HORDES -> renderHordes();
            case MOBS -> renderMobs();
        }
    }

    private List<Setting> pageSettings() {
        List<Setting> out = new ArrayList<>();
        for (Setting st : Setting.values()) {
            if (st.isHorde() == (page == Page.HORDES)) out.add(st);
        }
        return out;
    }

    private void renderSettings() {
        BloodMoonSettings s = plugin.bloodSettings();
        List<Setting> all = pageSettings();
        for (int i = 0; i < all.size() && i < SETTING_SLOTS.length; i++) {
            Setting st = all.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(C.E + st.description);
            lore.add("");
            lore.add(C.E + "Current: " + s.display(st));
            lore.add("");
            if (st.kind == Kind.BOOL) {
                lore.add(C.E + "Click " + C.E + "to toggle");
            } else {
                String step = st.kind == Kind.INT ? String.valueOf((int) st.step) : String.valueOf(st.step);
                lore.add(C.E + "Left-click " + C.E + "+" + step + "   " + C.E + "Right-click " + C.E + "-" + step);
                lore.add(C.E + "Shift-click " + C.E + "for 5x steps");
            }
            ItemStack it = item(st.icon, C.R + "§l" + st.display, lore);
            if (st.kind == Kind.BOOL && s.getBool(st)) glow(it);
            inv.setItem(SETTING_SLOTS[i], it);
        }
    }

    private void renderHordes() {
        renderSettings();
        inv.setItem(SLOT_HORDE, item(Material.ZOMBIE_HEAD, C.R + "§lSummon Horde On Me",
                List.of(C.E + "Spawns a wave-1 sized horde", C.E + "around you right now.")));
        inv.setItem(SLOT_BACK, item(Material.ARROW, C.H + "Back"));
    }

    private void renderMain() {
        renderSettings();
        BloodMoonSettings s = plugin.bloodSettings();
        BloodMoonManager bm = plugin.bloodMoon();
        inv.setItem(SLOT_START, item(Material.LIME_CONCRETE, C.ON + "§lStart Blood Moon Now",
                List.of(C.E + "Starts in your current world.", C.E + "Skips to nightfall if it's daytime.")));
        inv.setItem(SLOT_HORDE, item(Material.ZOMBIE_HEAD, C.R + "§lSummon Horde On Me",
                List.of(C.E + "Spawns a horde around you right now.", C.E + "Works even without a blood moon.")));
        inv.setItem(SLOT_HORDE_PAGE, item(Material.GOAT_HORN, C.R + "§lHorde Settings",
                List.of(C.E + "Waves, growth, player scaling", C.E + "and boss chance.")));
        inv.setItem(SLOT_STOP, item(Material.RED_CONCRETE, C.R + "§lStop Blood Moon",
                List.of(C.E + "Ends every active blood moon.")));

        List<String> info = new ArrayList<>();
        for (World w : bm.eligibleWorlds()) {
            if (bm.isActive(w)) info.add(C.H + w.getName() + C.E + ": " + C.R + "§lACTIVE " + C.E + "(wave " + C.H + bm.wave(w) + C.E + ")");
            else {
                long d = bm.daysUntilNext(w);
                info.add(C.H + w.getName() + C.E + ": " + (d == 0 ? C.R + "tonight" : "in " + C.H + d + C.E + " day(s)"));
            }
        }
        if (info.isEmpty()) info.add(C.R + "No eligible worlds (check worlds: in bloodmoon.yml)");
        inv.setItem(SLOT_INFO, item(Material.CLOCK, C.E + "§lStatus", info));

        inv.setItem(SLOT_MOBS, item(Material.ZOMBIE_SPAWN_EGG, C.O + "§lSpecial Mobs",
                List.of(C.E + "Choose which mobs can appear", C.E + "during a blood moon.",
                        "", C.E + "Enabled: " + C.H + s.specialMobs().size())));
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, C.R + "Close"));
    }

    private void renderMobs() {
        List<String> enabled = upper(plugin.bloodSettings().specialMobs());
        int slot = 0;
        for (String mob : MOB_CHOICES) {
            if (slot >= 45) break;
            Material egg = Material.matchMaterial(mob + "_SPAWN_EGG");
            if (egg == null) egg = Material.PAPER;
            boolean on = enabled.contains(mob);
            ItemStack it = item(egg, (on ? C.ON + "" : C.E) + pretty(mob),
                    List.of(on ? C.ON + "Enabled" : C.R + "Disabled", "", C.E + "Click " + C.E + "to toggle"));
            if (on) glow(it);
            inv.setItem(slot++, it);
        }
        inv.setItem(SLOT_BACK, item(Material.ARROW, C.H + "Back"));
    }

    // ------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------

    private void click(Player p, int slot, ClickType type) {
        switch (page) {
            case MAIN -> clickMain(p, slot, type);
            case HORDES -> clickHordes(p, slot, type);
            case MOBS -> clickMobs(p, slot);
        }
    }

    private boolean clickSetting(int slot, ClickType type) {
        List<Setting> all = pageSettings();
        for (int i = 0; i < all.size() && i < SETTING_SLOTS.length; i++) {
            if (SETTING_SLOTS[i] == slot) {
                plugin.bloodSettings().adjust(all.get(i), type.isRightClick() ? -1 : 1, type.isShiftClick());
                render();
                return true;
            }
        }
        return false;
    }

    private void clickHordes(Player p, int slot, ClickType type) {
        if (clickSetting(slot, type)) return;
        if (slot == SLOT_BACK) {
            open(plugin, p, Page.MAIN);
        } else if (slot == SLOT_HORDE) {
            p.closeInventory();
            int n = plugin.bloodMoon().spawnHorde(p, plugin.bloodSettings().getInt(Setting.HORDE_SIZE), 1);
            p.sendMessage(plugin.prefix() + "Summoned a horde of " + C.H + n + C.E + " on yourself.");
        }
    }

    private void clickMain(Player p, int slot, ClickType type) {
        if (clickSetting(slot, type)) return;
        BloodMoonManager bm = plugin.bloodMoon();
        switch (slot) {
            case SLOT_START -> {
                World w = p.getWorld();
                if (!bm.eligibleWorlds().contains(w)) {
                    p.sendMessage(plugin.prefix() + C.R + "Blood moons aren't enabled in this world.");
                    return;
                }
                bm.forceStart(w);
                p.sendMessage(plugin.prefix() + C.R + "Blood moon started in " + C.H + w.getName());
                render();
            }
            case SLOT_HORDE -> {
                p.closeInventory();
                int n = bm.spawnHorde(p, plugin.bloodSettings().getInt(Setting.HORDE_SIZE), 1);
                p.sendMessage(plugin.prefix() + "Summoned a horde of " + C.H + n + C.E + " on yourself.");
            }
            case SLOT_STOP -> {
                int n = 0;
                for (World w : bm.activeWorlds()) {
                    bm.end(w, true);
                    n++;
                }
                p.sendMessage(plugin.prefix() + "Stopped " + C.H + n + C.E + " blood moon(s).");
                render();
            }
            case SLOT_HORDE_PAGE -> open(plugin, p, Page.HORDES);
            case SLOT_MOBS -> open(plugin, p, Page.MOBS);
            case SLOT_CLOSE -> p.closeInventory();
            default -> { }
        }
    }

    private void clickMobs(Player p, int slot) {
        if (slot == SLOT_BACK) {
            open(plugin, p, Page.MAIN);
            return;
        }
        if (slot < 0 || slot >= 45 || slot >= MOB_CHOICES.size()) return;
        String mob = MOB_CHOICES.get(slot);
        List<String> enabled = new ArrayList<>(upper(plugin.bloodSettings().specialMobs()));
        if (!enabled.remove(mob)) enabled.add(mob);
        plugin.bloodSettings().setSpecialMobs(enabled);
        render();
    }

    // ------------------------------------------------------------------
    // Item helpers
    // ------------------------------------------------------------------

    private static ItemStack item(Material m, String name) {
        return item(m, name, List.of());
    }

    private static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    private static void glow(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
    }

    private static List<String> upper(List<String> in) {
        return in.stream().map(x -> x.toUpperCase(Locale.ROOT)).toList();
    }

    private static String pretty(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Event handler (registered once in onEnable)
    // ------------------------------------------------------------------

    public static final class Handler implements Listener {
        private final CopeNights plugin;

        public Handler(CopeNights plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getView().getTopInventory().getHolder() instanceof BloodMoonMenu menu)) return;
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            if (!p.hasPermission("copenights.admin")) {
                p.closeInventory();
                return;
            }
            menu.click(p, e.getRawSlot(), e.getClick());
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getView().getTopInventory().getHolder() instanceof BloodMoonMenu) e.setCancelled(true);
        }
    }
}

package us.copestudios.copenights;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import us.copestudios.copecore.config.ConfigFile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * One-time upgrade from Cope Nights 0.0.32 and older.
 *
 * <h2>What changed</h2>
 * <p>Old versions kept their texts inside config.yml (prefix, whisper, jumpscare...) and bloodmoon.yml
 * (messages:, death-messages-text:). Now every text lives in messages.yml, handled by CopeCore.</p>
 *
 * <h2>What this class does</h2>
 * <ol>
 *   <li>For each old text key, if it is still in the old file AND its value is different from the old
 *       default (so the owner customized it), the value is copied to its new place in messages.yml.
 *       Untouched texts are not copied, so those servers get the new, nicer defaults.</li>
 *   <li>The old keys are removed from config.yml / bloodmoon.yml so nobody edits a text that does nothing.</li>
 *   <li>"copecore-migrated: true" is written to data.yml so this never runs twice.</li>
 * </ol>
 *
 * <p>Old texts keep working as they are: CopeCore understands &amp;-codes, &amp;#RRGGBB, the old
 * {@code <gradient:#A:#B>} tags and %placeholders%.</p>
 */
final class LegacyMigration {

    /** Marker in data.yml. */
    private static final String DONE_KEY = "copecore-migrated";

    /** old config.yml key -> new messages.yml key */
    private static final String[][] CONFIG_KEYS = {
            {"prefix", "prefix"},
            {"whisper.format", "scares.whisper.format"},
            {"whisper.messages", "scares.whisper.lines"},
            {"jumpscare.title", "scares.jumpscare.title"},
            {"jumpscare.subtitle", "scares.jumpscare.subtitle"},
            {"sky-drop.title", "scares.sky-drop.title"},
            {"sky-drop.subtitle", "scares.sky-drop.subtitle"},
            {"fake-join.name", "scares.fake-join.name"},
            {"fake-join.messages", "scares.fake-join.lines"},
    };

    /** Old config.yml sections that only held texts and can be removed completely once moved. */
    private static final String[] CONFIG_TEXT_SECTIONS = {"whisper", "jumpscare", "fake-join"};

    /** old bloodmoon.yml key under messages: (same name under bloodmoon: in messages.yml) */
    private static final String[] BLOODMOON_KEYS = {
            "warning", "start-title", "start-subtitle", "end", "no-sleep", "bossbar",
            "survived-title", "survived-subtitle", "horde-title", "horde-subtitle", "horde-captain",
            "wave-announce", "boss-announce"};

    /** old bloodmoon.yml death-messages-text.<type> -> death.<type> */
    private static final String[] DEATH_TYPES = {"horde", "bloodmoon", "mob", "player"};

    private final CopeNights plugin;
    /** data.yml, shared with the main class so both write the same file. */
    private final ConfigFile data;

    LegacyMigration(CopeNights plugin, ConfigFile data) {
        this.plugin = plugin;
        this.data = data;
    }

    void run() {
        if (data.get().getBoolean(DONE_KEY, false)) {
            return;
        }

        YamlConfiguration oldDefaults = loadOldDefaults();
        ConfigFile config = plugin.settings();
        ConfigFile bloodmoon = plugin.bloodSettings().file();
        YamlConfiguration messages = plugin.messages().file().get();
        int copied = 0;
        boolean configChanged = false;
        boolean bloodmoonChanged = false;

        // ---- config.yml ----
        for (String[] pair : CONFIG_KEYS) {
            String oldKey = pair[0];
            if (!config.get().contains(oldKey, true)) {
                continue; // not in the user's file (fresh install, or already moved)
            }
            Object value = config.get().get(oldKey);
            if (isCustomized(value, oldDefaults, "config." + oldKey)) {
                messages.set(pair[1], value);
                copied++;
            }
            config.get().set(oldKey, null);
            configChanged = true;
        }
        for (String section : CONFIG_TEXT_SECTIONS) {
            if (config.get().contains(section, true)) {
                config.get().set(section, null);
                configChanged = true;
            }
        }

        // ---- bloodmoon.yml: messages ----
        for (String key : BLOODMOON_KEYS) {
            String oldKey = "messages." + key;
            if (!bloodmoon.get().contains(oldKey, true)) {
                continue;
            }
            Object value = bloodmoon.get().get(oldKey);
            if (isCustomized(value, oldDefaults, "bloodmoon." + oldKey)) {
                messages.set("bloodmoon." + key, value);
                copied++;
            }
        }
        if (bloodmoon.get().contains("messages", true)) {
            bloodmoon.get().set("messages", null);
            bloodmoonChanged = true;
        }

        // ---- bloodmoon.yml: death messages ----
        for (String type : DEATH_TYPES) {
            String oldKey = "death-messages-text." + type;
            if (!bloodmoon.get().contains(oldKey, true)) {
                continue;
            }
            List<String> lines = bloodmoon.get().getStringList(oldKey);
            if (isCustomized(lines, oldDefaults, "bloodmoon." + oldKey)) {
                messages.set("death." + type, lines);
                copied++;
            }
        }
        if (bloodmoon.get().contains("death-messages-text", true)) {
            bloodmoon.get().set("death-messages-text", null);
            bloodmoonChanged = true;
        }

        // ---- save everything ----
        if (copied > 0) {
            plugin.messages().file().save();
            plugin.messages().reload();
        }
        if (configChanged) {
            config.save();
        }
        if (bloodmoonChanged) {
            bloodmoon.save();
        }
        data.get().set(DONE_KEY, true);
        data.save();

        if (configChanged || bloodmoonChanged) {
            plugin.getLogger().info("Upgraded from an older Cope Nights: texts now live in messages.yml ("
                    + copied + " customized text(s) copied over).");
        }
    }

    /** True if the old value differs from what old Cope Nights shipped with (or there's no old default). */
    private static boolean isCustomized(Object value, YamlConfiguration oldDefaults, String path) {
        if (value instanceof ConfigurationSection) {
            return false; // a section where a text was expected: nothing usable to copy
        }
        if (oldDefaults == null || !oldDefaults.contains(path)) {
            return true;
        }
        Object original = oldDefaults.isList(path) ? oldDefaults.getStringList(path) : oldDefaults.get(path);
        return !Objects.equals(normalise(value), normalise(original));
    }

    /** Lists from YAML can be List&lt;Object&gt;; compare them as text. */
    private static Object normalise(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return value == null ? null : String.valueOf(value);
    }

    private YamlConfiguration loadOldDefaults() {
        try (InputStream in = plugin.getResource("legacy/old-defaults.yml")) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception error) {
            return null;
        }
    }
}

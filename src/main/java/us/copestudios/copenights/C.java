package us.copestudios.copenights;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cope Nights colors + RGB gradient engine.
 *
 * In config text you can use:
 *   &#RRGGBB                                   - a single RGB color
 *   <gradient:#A80000:#FF8C1A>text</gradient>  - smooth gradient (2 or more colors)
 *   &l &o &n &m &k &r                          - bold, italic, etc. (also inside gradients)
 *   normal &a &c &f codes still work
 *
 * Output is legacy §x hex, which works on Paper, Purpur, Spigot and Arclight (1.16+ clients).
 */
public final class C {

    // ---- Palette ----
    public static final String BLOOD_HEX = "5A0000";   // deepest red (shadows, separators)
    public static final String DARK_RED_HEX = "A80000"; // accents, warnings
    public static final String ORANGE_HEX = "FF8C1A";   // main brand orange
    public static final String EMBER_HEX = "FFB366";    // soft orange for body text
    public static final String CREAM_HEX = "FFE4C7";    // highlighted values (names, numbers)

    public static final String BLOOD = hex(BLOOD_HEX);
    public static final String R = hex(DARK_RED_HEX);
    public static final String O = hex(ORANGE_HEX);
    public static final String E = hex(EMBER_HEX);
    public static final String H = hex(CREAM_HEX);
    public static final String GRAY = hex("9A8F86");
    public static final String ON = hex("7CFF6B");
    public static final String OFF = hex("FF4A3D");

    /** » arrow used between prefix and text */
    public static final String ARROW = BLOOD + "» ";

    public static final String DEFAULT_PREFIX =
            "&#5A0000&l☾ <gradient:#FF8C1A:#A80000>&lConi</gradient> &#5A0000» &#FFB366";

    private static final Pattern GRADIENT = Pattern.compile("<gradient:([#0-9a-fA-F:]+)>(.*?)</gradient>", Pattern.DOTALL);
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    private C() {
    }

    // ------------------------------------------------------------------
    // Public helpers
    // ------------------------------------------------------------------

    /** Full color pass: gradients, &#hex, then & codes. */
    public static String colorize(String s) {
        if (s == null || s.isEmpty()) return "";
        Matcher g = GRADIENT.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (g.find()) {
            String[] stops = g.group(1).replace("#", "").split(":");
            List<String> valid = new ArrayList<>();
            for (String st : stops) if (st.length() == 6) valid.add(st);
            String inner = g.group(2);
            String rep = valid.size() >= 2 ? gradient(inner, valid.toArray(new String[0])) : inner;
            g.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        g.appendTail(sb);

        Matcher h = HEX.matcher(sb.toString());
        StringBuilder out = new StringBuilder();
        while (h.find()) h.appendReplacement(out, Matcher.quoteReplacement(hex(h.group(1))));
        h.appendTail(out);
        return translate(out.toString());
    }

    /** "FF8C1A" -> §x§f§f§8§c§1§a */
    public static String hex(String rrggbb) {
        StringBuilder sb = new StringBuilder("§x");
        for (char c : rrggbb.toCharArray()) sb.append('§').append(Character.toLowerCase(c));
        return sb.toString();
    }

    /**
     * Colors every character along a gradient. & / § format codes inside the text
     * (bold, italic...) are kept and re-applied after each color change.
     */
    public static String gradient(String text, String... stops) {
        String t = translate(text);
        // collect visible characters + formats active at each one
        List<Character> chars = new ArrayList<>();
        List<String> formats = new ArrayList<>();
        StringBuilder active = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '§' && i + 1 < t.length()) {
                char code = Character.toLowerCase(t.charAt(++i));
                if ("klmno".indexOf(code) >= 0) active.append('§').append(code);
                else if (code == 'r') active.setLength(0);
                continue; // color codes inside a gradient are ignored
            }
            chars.add(c);
            formats.add(active.toString());
        }
        int n = chars.size();
        if (n == 0) return "";

        int[][] rgb = new int[stops.length][];
        for (int i = 0; i < stops.length; i++) rgb[i] = parse(stops[i]);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            char c = chars.get(i);
            if (c == ' ') { // spaces don't need a color
                sb.append(' ');
                continue;
            }
            double pos = n == 1 ? 0 : (double) i / (n - 1);
            double scaled = pos * (stops.length - 1);
            int seg = Math.min((int) scaled, stops.length - 2);
            double f = scaled - seg;
            int[] a = rgb[seg], b = rgb[seg + 1];
            int r = (int) Math.round(a[0] + (b[0] - a[0]) * f);
            int gg = (int) Math.round(a[1] + (b[1] - a[1]) * f);
            int bb = (int) Math.round(a[2] + (b[2] - a[2]) * f);
            sb.append(hex(String.format("%02x%02x%02x", r, gg, bb))).append(formats.get(i)).append(c);
        }
        return sb.toString();
    }

    /** Divider line like ▬▬▬▬ ✦ Title ✦ ▬▬▬▬ with a blood→orange→blood gradient. */
    public static String header(String title) {
        String bar = "▬".repeat(9);
        return gradient(bar, BLOOD_HEX, DARK_RED_HEX) + " " + gradient("§l" + title, ORANGE_HEX, DARK_RED_HEX)
                + " " + gradient(bar, DARK_RED_HEX, BLOOD_HEX);
    }

    public static String footer() {
        return gradient("▬".repeat(32), BLOOD_HEX, DARK_RED_HEX, ORANGE_HEX, DARK_RED_HEX, BLOOD_HEX);
    }

    public static String onOff(boolean on) {
        return on ? ON + "§l✔ ON" : OFF + "§l✘ OFF";
    }

    // ------------------------------------------------------------------

    private static String translate(String s) {
        char[] b = s.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(b[i + 1]) > -1) {
                b[i] = '§';
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }

    private static int[] parse(String hex) {
        int v = Integer.parseInt(hex, 16);
        return new int[]{(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF};
    }
}

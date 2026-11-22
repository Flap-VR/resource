package me.sentient.takeout;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compiled blocklists from config.yml. Immutable once built, so the async chat thread can use it safely. */
public final class RuleSet {
    public record Match(String category, String match, int severity) {}

    public record Info(String name, int severity, String duration, String reason) {}

    private static final class Category {
        String name;
        int severity;
        String duration;
        String reason;
        final List<Pattern> words = new ArrayList<>();
        final List<Pattern> contains = new ArrayList<>();
        final List<Pattern> regex = new ArrayList<>();
        final List<Pattern> regexRaw = new ArrayList<>();
        final List<Pattern> squashedRegex = new ArrayList<>();
        final List<Pattern> allowed = new ArrayList<>();
    }

    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final List<Pattern> ignored = new ArrayList<>();

    public RuleSet(FileConfiguration cfg, Logger log) {
        for (String s : cfg.getStringList("ignored-text")) {
            String n = TextNormalizer.pre(s).trim();
            if (!n.isEmpty()) ignored.add(Pattern.compile(Pattern.quote(n)));
        }
        ConfigurationSection root = cfg.getConfigurationSection("categories");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null || !s.getBoolean("enabled", true)) continue;
            Category c = new Category();
            c.name = name;
            c.severity = s.getInt("severity", 1);
            c.duration = s.getString("ban-duration", "1d");
            c.reason = s.getString("reason", name);
            for (String w : s.getStringList("words")) add(c.words, () -> TextNormalizer.wordPattern(w), name, w, log);
            for (String w : s.getStringList("contains")) add(c.contains, () -> TextNormalizer.containsPattern(w), name, w, log);
            for (String w : s.getStringList("allowed-words")) add(c.allowed, () -> TextNormalizer.tokenPattern(w), name, w, log);
            for (String r : s.getStringList("regex")) add(c.regex, () -> Pattern.compile(r), name, r, log);
            for (String r : s.getStringList("regex-raw")) add(c.regexRaw, () -> Pattern.compile(r), name, r, log);
            for (String r : s.getStringList("squashed-regex")) add(c.squashedRegex, () -> Pattern.compile(r), name, r, log);
            categories.put(name, c);
        }
    }

    private interface PatternSupplier {
        Pattern get();
    }

    private static void add(List<Pattern> into, PatternSupplier sup, String category, String source, Logger log) {
        try {
            if (source == null || source.isBlank()) return;
            into.add(sup.get());
        } catch (Exception ex) {
            log.warning("Skipping invalid entry in category '" + category + "': " + source + " (" + ex.getMessage() + ")");
        }
    }

    public Info info(String category) {
        Category c = categories.get(category);
        return c == null ? null : new Info(c.name, c.severity, c.duration, c.reason);
    }

    public boolean has(String category) {
        return categories.containsKey(category);
    }

    /** Returns the most severe match, or null when the text is fine. Safe to call from any thread. */
    public Match scan(String message) {
        if (message == null || message.isBlank()) return null;
        String text = TextNormalizer.pre(message);
        for (Pattern ig : ignored) text = ig.matcher(text).replaceAll(" ");

        Match best = null;
        // "1" and "|" can mean i or l, so everything is checked with both readings
        for (boolean oneAsL : new boolean[]{false, true}) {
            for (Category c : categories.values()) {
                TextNormalizer.Views v = TextNormalizer.views(text, oneAsL, c.allowed);
                Match m = find(c, v);
                if (m != null && (best == null || m.severity() > best.severity())) best = m;
            }
        }
        return best;
    }

    private static String first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group().trim() : null;
    }

    private static Match find(Category c, TextNormalizer.Views v) {
        for (Pattern p : c.words) {
            String hit = first(p, v.clean());
            if (hit == null) hit = first(p, v.cleanMerged());
            if (hit != null) return hit(c, hit);
        }
        for (Pattern p : c.contains) {
            String hit = first(p, v.squashed());
            if (hit != null) return hit(c, hit);
        }
        for (Pattern p : c.regex) {
            String hit = first(p, v.clean());
            if (hit == null) hit = first(p, v.squash3());
            if (hit != null) return hit(c, hit);
        }
        for (Pattern p : c.regexRaw) {
            String hit = first(p, v.rawLower());
            if (hit == null) hit = first(p, v.leetRaw());
            if (hit != null) return hit(c, hit);
        }
        for (Pattern p : c.squashedRegex) {
            String hit = first(p, v.squashed());
            if (hit != null) return hit(c, hit);
        }
        return null;
    }

    private static Match hit(Category c, String text) {
        String t = text.length() > 40 ? text.substring(0, 40) : text;
        return new Match(c.name, t, c.severity);
    }
}

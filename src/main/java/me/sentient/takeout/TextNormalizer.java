package me.sentient.takeout;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a chat message into several "views" so bypass attempts all collapse to the same text:
 * capitals, accents, fullwidth / math fonts, look-alike cyrillic + greek letters, zero-width characters,
 * leetspeak (j0in s3rv3r), symbols between letters (j.o.i.n), spaced letters (j o i n) and stretched letters (jooooin).
 */
public final class TextNormalizer {
    /**
     * rawLower: lowercase, symbols and digits kept (ips, links).  leetRaw: same with leetspeak turned into letters.
     * clean: letters and single spaces only.  cleanMerged: like clean but "f u c k" becomes "fuck".
     * squash3: clean with runs of 3+ identical letters reduced to one.  squashed: letters only, no spaces at all.
     */
    public record Views(String rawLower, String leetRaw, String clean, String cleanMerged, String squash3, String squashed) {}

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200F\\u2060\\uFEFF\\u00AD\\u202A-\\u202E]");
    private static final Pattern NON_LETTER = Pattern.compile("[^a-z]+");
    private static final Pattern RUNS3 = Pattern.compile("(.)\\1{2,}");
    private static final Map<Character, Character> HOMOGLYPHS = new HashMap<>();

    static {
        String[] pairs = {
                "\u0430=a", "\u0432=b", "\u0435=e", "\u043A=k", "\u043C=m", "\u043D=h", "\u043E=o", "\u0440=p",
                "\u0441=c", "\u0442=t", "\u0443=y", "\u0445=x", "\u0456=i", "\u0458=j", "\u0455=s", "\u0501=d",
                "\u04CF=l", "\u03B1=a", "\u03B2=b", "\u03B5=e", "\u03B9=i", "\u03BA=k", "\u03BD=v", "\u03BF=o",
                "\u03C1=p", "\u03C4=t", "\u03C5=u", "\u03C7=x", "\u0131=i", "\u00F8=o", "\u0142=l", "\u0111=d"
        };
        for (String p : pairs) HOMOGLYPHS.put(p.charAt(0), p.charAt(2));
    }

    private TextNormalizer() {}

    /** Unicode folding + lowercase + look-alike letters. No leetspeak yet. */
    public static String pre(String in) {
        if (in == null) return "";
        String s = Normalizer.normalize(in, Normalizer.Form.NFKD);
        s = MARKS.matcher(s).replaceAll("");
        s = INVISIBLE.matcher(s).replaceAll("");
        s = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(HOMOGLYPHS.getOrDefault(c, c));
        }
        return sb.toString();
    }

    private static char leetChar(char c, boolean oneAsL) {
        switch (c) {
            case '0': return 'o';
            case '1': case '|': return oneAsL ? 'l' : 'i';
            case '3': return 'e';
            case '4': case '@': return 'a';
            case '5': case '$': return 's';
            case '7': case '+': return 't';
            case '8': return 'b';
            case '9': return 'g';
            case '!': case '\u00A1': return 'i';
            case '\u20AC': return 'e';
            case '\u00A3': return 'l';
            case '\u00A2': return 'c';
            default: return c;
        }
    }

    public static String leet(String s, boolean oneAsL) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) sb.append(leetChar(s.charAt(i), oneAsL));
        return sb.toString();
    }

    /** `pre` must already be the output of pre(). Tokens fully matching an allowed pattern are dropped. */
    public static Views views(String pre, boolean oneAsL, List<Pattern> allowedTokens) {
        String leetRaw = leet(pre, oneAsL);
        String cleaned = NON_LETTER.matcher(leetRaw).replaceAll(" ").trim();

        List<String> tokens = new ArrayList<>();
        if (!cleaned.isEmpty()) {
            for (String t : cleaned.split(" ")) {
                boolean allowed = false;
                for (Pattern p : allowedTokens) {
                    if (p.matcher(t).matches()) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) tokens.add(t);
            }
        }
        String clean = String.join(" ", tokens);
        String merged = String.join(" ", mergeSingles(tokens));
        String squash3 = RUNS3.matcher(clean).replaceAll("$1");
        String squashed = clean.replace(" ", "");
        return new Views(pre, leetRaw, clean, merged, squash3, squashed);
    }

    /** "f u c k you" -> "fuck you": runs of two or more single-letter tokens are glued together. */
    private static List<String> mergeSingles(List<String> tokens) {
        List<String> out = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        int runLen = 0;
        for (String t : tokens) {
            if (t.length() == 1) {
                run.append(t);
                runLen++;
                continue;
            }
            flush(out, run, runLen);
            run.setLength(0);
            runLen = 0;
            out.add(t);
        }
        flush(out, run, runLen);
        return out;
    }

    private static void flush(List<String> out, StringBuilder run, int runLen) {
        if (runLen == 0) return;
        out.add(run.toString());
    }

    // ---- pattern builders for the config word lists --------------------------------------------------------------

    private static String letters(String term) {
        return NON_LETTER.matcher(leet(pre(term), false)).replaceAll(" ").trim();
    }

    /** Whole word / phrase, every letter may repeat: "fuck" matches f+u+c+k+ (fuuuck). */
    public static Pattern wordPattern(String term) {
        if (letters(term).isEmpty()) throw new IllegalArgumentException("no letters in entry");
        String[] parts = letters(term).split("\\s+");
        StringBuilder rx = new StringBuilder("(?<![a-z])");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) rx.append("\\s+");
            for (char c : parts[i].toCharArray()) rx.append(c).append('+');
        }
        rx.append("(?![a-z])");
        return Pattern.compile(rx.toString());
    }

    /** Substring match on the squashed (no spaces) text, letters may repeat. */
    public static Pattern containsPattern(String term) {
        if (letters(term).isEmpty()) throw new IllegalArgumentException("no letters in entry");
        StringBuilder rx = new StringBuilder();
        for (char c : letters(term).replace(" ", "").toCharArray()) rx.append(c).append('+');
        return Pattern.compile(rx.toString());
    }

    /** One whole token (for allowed-words). */
    public static Pattern tokenPattern(String term) {
        if (letters(term).isEmpty()) throw new IllegalArgumentException("no letters in entry");
        StringBuilder rx = new StringBuilder("^");
        for (char c : letters(term).replace(" ", "").toCharArray()) rx.append(c).append('+');
        rx.append('$');
        return Pattern.compile(rx.toString());
    }
}

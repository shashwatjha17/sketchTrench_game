package com.sketchtrench.guest;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Profanity handling for chat, nicknames and room names. Words come from
 * {@code classpath:words/blocked.txt}; a token matches after folding case, accents and
 * leetspeak, either exactly or with repeated letters collapsed ("fuuuck"). Roots marked
 * with {@code *} also match inside longer tokens ("fucking", "bullshit").
 */
public final class ChatFilter {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}@$!*]+");
    private static final Set<String> EXACT = new HashSet<>();
    private static final Set<String> EXACT_COLLAPSED = new HashSet<>();
    private static final Set<String> ROOTS = new HashSet<>();

    static {
        for (String line : WordPool.readList("words/blocked.txt")) {
            boolean root = line.startsWith("*");
            String w = fold(root ? line.substring(1) : line);
            if (w.isEmpty()) continue;
            if (root) ROOTS.add(w);
            EXACT.add(w);
            EXACT_COLLAPSED.add(collapse(w));
        }
    }

    private ChatFilter() {
    }

    /** True if any word in {@code text} is blocked. Also catches roots hidden in run-together names ("xXfuckXx"). */
    public static boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) return false;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            if (isBlocked(m.group())) return true;
        }
        return containsRoot(fold(text.replaceAll("[^\\p{L}\\p{N}@$!]", "")));
    }

    /** {@code text} with every blocked word replaced by asterisks of the same length. */
    public static String censor(String text) {
        if (text == null || text.isBlank()) return text;
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String tok = m.group();
            // trailing "!" is punctuation, not leetspeak: censor the word, keep the bang
            String core = tok.replaceFirst("!+$", "");
            String tail = tok.substring(core.length());
            String replaced = !core.isEmpty() && isBlocked(core) ? "*".repeat(core.length()) + tail : tok;
            m.appendReplacement(out, Matcher.quoteReplacement(replaced));
        }
        m.appendTail(out);
        return out.toString();
    }

    static boolean isBlocked(String token) {
        String f = fold(token);
        if (f.isEmpty()) return false;
        if (EXACT.contains(f)) return true;
        String c = collapse(f);
        // only stretched spellings use the collapsed form, so plain "as" never matches "ass"
        if (!c.equals(f) && EXACT_COLLAPSED.contains(c)) return true;
        return containsRoot(f);
    }

    /**
     * Roots match inside the folded text; roots without doubled letters also match the
     * collapsed text ("fuuuuck"). Doubled-letter roots don't, so "niger"/"Nigeria" stay clean.
     */
    private static boolean containsRoot(String folded) {
        String c = collapse(folded);
        for (String root : ROOTS) {
            if (folded.contains(root)) return true;
            if (root.equals(collapse(root)) && c.contains(root)) return true;
        }
        return false;
    }

    /** lower-case, strip accents, undo common leetspeak, keep letters only */
    static String fold(String s) {
        String n = Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder b = new StringBuilder(n.length());
        for (char ch : n.toCharArray()) {
            switch (ch) {
                case '0' -> b.append('o');
                case '1', '!' -> b.append('i');
                case '3' -> b.append('e');
                case '4', '@' -> b.append('a');
                case '5', '$' -> b.append('s');
                case '7' -> b.append('t');
                case 'ß' -> b.append("ss");
                default -> {
                    if (Character.isLetter(ch)) b.append(ch);
                }
            }
        }
        return b.toString();
    }

    /** squash runs of the same letter: "fuuuck" → "fuck", "ass" → "as" */
    static String collapse(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            if (i == 0 || s.charAt(i) != s.charAt(i - 1)) b.append(s.charAt(i));
        }
        return b.toString();
    }

    static List<String> roots() {
        return List.copyOf(ROOTS);
    }
}

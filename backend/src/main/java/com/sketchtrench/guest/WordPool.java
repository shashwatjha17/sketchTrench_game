package com.sketchtrench.guest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static word lists per language, loaded once from {@code classpath:words/<lang>.txt}
 * (one word per line, {@code #} comments). Unknown languages fall back to English.
 */
public final class WordPool {

    public static final String DEFAULT_LANGUAGE = "en";
    public static final List<String> LANGUAGES = List.of("en", "de", "fr", "es");

    private static final Map<String, List<String>> WORDS = load();

    private WordPool() {
    }

    private static Map<String, List<String>> load() {
        Map<String, List<String>> out = new java.util.HashMap<>();
        for (String lang : LANGUAGES) {
            out.put(lang, readList("words/" + lang + ".txt"));
        }
        return Map.copyOf(out);
    }

    static List<String> readList(String resource) {
        try (InputStream in = WordPool.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing resource " + resource);
            Set<String> words = new LinkedHashSet<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String w = line.strip().toLowerCase();
                    if (!w.isEmpty() && !w.startsWith("#")) words.add(w);
                }
            }
            return List.copyOf(words);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String normalizeLanguage(String language) {
        return language != null && LANGUAGES.contains(language) ? language : DEFAULT_LANGUAGE;
    }

    public static List<String> words(String language) {
        return WORDS.get(normalizeLanguage(language));
    }

    public static List<String> pick(int n) {
        return pick(DEFAULT_LANGUAGE, n, Set.of());
    }

    /** {@code n} random words in {@code language}, avoiding {@code exclude} while enough fresh words remain. */
    public static List<String> pick(String language, int n, Collection<String> exclude) {
        List<String> fresh = new ArrayList<>(words(language));
        fresh.removeAll(exclude);
        if (fresh.size() < n) fresh = new ArrayList<>(words(language)); // pool exhausted: allow repeats
        Collections.shuffle(fresh);
        return fresh.subList(0, Math.min(n, fresh.size()));
    }
}

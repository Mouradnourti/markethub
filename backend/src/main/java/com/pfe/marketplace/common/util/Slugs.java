package com.pfe.marketplace.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

public final class Slugs {

    private Slugs() {
    }

    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "item" : slug;
    }

    /** Returns {@code slugify(input)}, suffixed with -2, -3 … until {@code taken} rejects it. */
    public static String uniqueSlug(String input, Predicate<String> taken) {
        String base = slugify(input);
        String candidate = base;
        for (int i = 2; taken.test(candidate); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }
}

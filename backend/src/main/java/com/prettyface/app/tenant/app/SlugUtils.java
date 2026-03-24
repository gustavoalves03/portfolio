package com.prettyface.app.tenant.app;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class SlugUtils {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private SlugUtils() {}

    public static String toSlug(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        // Remove diacritics
        String ascii = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Lowercase and replace non-alphanumeric with hyphen
        String slug = NON_ALPHANUMERIC.matcher(ascii.toLowerCase()).replaceAll("-");
        // Trim leading/trailing hyphens
        return slug.replaceAll("^-+|-+$", "");
    }
}

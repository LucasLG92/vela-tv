package tv.vela.launcher;

import java.util.Locale;
import java.util.StringJoiner;

/** Bounded category text shared by the XMLTV importers and discovery queries. */
final class XmlTvCategories {
    private static final int LIMIT = 240;
    private static final String[][] KEYWORDS = {
        {},
        {"movie", "film", "cine", "película", "pelicula"},
        {"sport", "deporte", "football", "soccer"},
        {"documentary", "documental"},
        {"children", "kids", "infantil", "child"}
    };
    private static final int OVERLAP = longestKeyword() - 1;
    private final StringBuilder window = new StringBuilder(LIMIT);
    private String prefix;
    private int genres;

    private static int longestKeyword() {
        int longest = 0;
        for (String[] words : KEYWORDS)
            for (String word : words) longest = Math.max(longest, word.length());
        return longest;
    }

    static String predicate(int genre, String column) {
        if (genre < 1 || genre >= KEYWORDS.length) return "";
        StringJoiner clauses = new StringJoiner(" OR ", " AND (", ")");
        for (String word : KEYWORDS[genre]) clauses.add("lower(" + column + ") LIKE '%" + word + "%'");
        return clauses.toString();
    }

    void clear() {
        window.setLength(0);
        prefix = null;
        genres = 0;
    }

    void beginLabel() {
        if (window.length() > 0) append(" ");
    }

    void append(String text) {
        for (int start = 0; start < text.length();) {
            if (window.length() == LIMIT) {
                if (prefix == null) prefix = window.toString();
                detectGenres();
                // Retain enough text to recognise a keyword crossing this boundary.
                window.delete(0, LIMIT - OVERLAP);
            }
            int end = start + Math.min(LIMIT - window.length(), text.length() - start);
            window.append(text, start, end);
            start = end;
        }
    }

    private void detectGenres() {
        String sample = window.toString().toLowerCase(Locale.ROOT);
        for (int genre = 1; genre < KEYWORDS.length; genre++) {
            for (String keyword : KEYWORDS[genre]) {
                if (sample.contains(keyword)) {
                    genres |= 1 << genre;
                    break;
                }
            }
        }
    }

    String value() {
        if (prefix == null) return window.toString();
        detectGenres();
        // The cache column has room for the recognised genres and the remaining original text.
        StringBuilder result = new StringBuilder(LIMIT);
        for (int genre = 1; genre < KEYWORDS.length; genre++)
            if ((genres & (1 << genre)) != 0) result.append(KEYWORDS[genre][0]).append(' ');
        return result.append(prefix, 0, LIMIT - result.length()).toString();
    }
}

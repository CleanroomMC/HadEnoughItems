package mezz.jei.search;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits filter text into tokens, grouped by an OR clause.
 * <p>
 * A {@code |} outside of quotes starts a new OR clause.
 * Within a clause, tokens are separated by whitespace, unless the whitespace is inside quotes.
 * Quotes may start anywhere in a token, so a prefix can be applied to a quoted phrase ({@code @"had enough items"}),
 * and text following the closing quote stays part of the same token.
 * A leading {@code -} outside of quotes marks the token for exclusion, and {@code \} escapes the next character,
 * so {@code \|} and {@code "|"} both search for a literal pipe.
 */
public class SearchTokenizer {

    public static List<List<Token>> tokenize(String filterText) {
        List<List<Token>> groups = new ArrayList<>();
        if (filterText.isEmpty()) {
            return groups;
        }

        List<Token> currentGroup = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;
        boolean exclusion = false;
        boolean escaped = false;

        for (int i = 0; i < filterText.length(); i++) {
            char c = filterText.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                insideQuotes = !insideQuotes;
                continue;
            }
            if (!insideQuotes && c == '|') {
                addToken(currentGroup, current, exclusion);
                current.setLength(0);
                exclusion = false;
                groups.add(currentGroup);
                currentGroup = new ArrayList<>();
                continue;
            }
            if (!insideQuotes && Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    addToken(currentGroup, current, exclusion);
                    current.setLength(0);
                }
                exclusion = false;
                continue;
            }
            // A '-' means exclusion at the start of a token, outside of quotes
            if (!insideQuotes && current.length() == 0 && c == '-') {
                exclusion = true;
                continue;
            }
            current.append(c);
        }

        addToken(currentGroup, current, exclusion);
        groups.add(currentGroup);

        return groups;
    }

    private static void addToken(List<Token> tokens, StringBuilder content, boolean exclusion) {
        String text = content.toString().trim();
        if (!text.isEmpty()) {
            tokens.add(new Token(text, exclusion));
        }
    }

    public static class Token {

        public final String text;
        public final boolean exclusion;

        public Token(String text, boolean exclusion) {
            this.text = text;
            this.exclusion = exclusion;
        }

    }

}

package processor;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Clasa care extrage cuvintele cheie din textul unui articol.
 * Exclude linking words si pastreaza doar termeni relevanti.
 */
public class KeywordExtractor {
    // Pattern folosit pentru eliminarea la orice caracter care nu e litera
    private static final Pattern CLEAN_PATTERN = Pattern.compile("[^a-zA-Z]");
    
    /**
     * Extrage cuvinte cheie dintr un text si:
     * Converteste textul la lowercase
     * Desparte pe spatii
     * Curata fiecare cuvant eliminand caractere care nu sunt litere
     * Exclude linking words
     */
    public static Set<String> extractKeywords(String text, Set<String> linkingWords) {
        Set<String> keywords = new HashSet<>();

        if (text == null || text.isEmpty()) {
            return keywords;
        }

        String[] words = text.toLowerCase().split("\\s+");

        for (String raw : words) {
            if (raw.isEmpty()) {
                continue;
            }

            String cleaned = CLEAN_PATTERN.matcher(raw).replaceAll("");

            // ignora cuvinte goale sau cele care se afla-n lista de linking words
            if (cleaned.isEmpty() || linkingWords.contains(cleaned)) {
                continue;
            }
            keywords.add(cleaned);
        }

        return keywords;
    }
}



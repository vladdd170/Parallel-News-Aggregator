package writer;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import model.Article;
import processor.StatisticsCollector;

/**
 * Clasa pentru generarea tuturor fisierelor de output
 */
public class OutputWriter {

    /**
     * Scrie fisierul all_articles.txt.
     */
    public static void writeAllArticles(String filename, List<Article> articles) throws IOException {
        List<Article> sorted = new ArrayList<>(articles);

        sorted.sort((a, b) -> {
            Instant pa = a.getPublished();
            Instant pb = b.getPublished();

            // Daca ambele articole nu au data - sortam doar dupa UUID
            if (pa == null && pb == null) {
                return a.getUuid().compareTo(b.getUuid());
            }
            // Articol fara data este considerat "mai vechi"
            if (pa == null) return 1;
            if (pb == null) return -1;

            // Sortare descrescatoare dupa timp
            int timeCompare = pb.compareTo(pa);
            if (timeCompare != 0) {
                return timeCompare;
            }

            // Tie-breaker: UUID lexicografic
            return a.getUuid().compareTo(b.getUuid());
        });

        try (FileWriter writer = new FileWriter(filename)) {
            for (Article article : sorted) {
                if (article.getPublished() != null) {
                    writer.write(article.getUuid() + " " +
                            article.getPublished().toString() + "\n");
                }
            }
        }
    }

    /**
     * Scrie fisierul pentru o limba: un UUID pe linie, sortat lexicografic.
     */
    public static void writeLanguageFile(String filename, List<String> uuids) throws IOException {
        writeUuidList(filename, uuids);
    }

    /**
     * Scrie fisierul pentru o categorie: un UUID pe linie, sortat lexicografic.
     */
    public static void writeCategoryFile(String filename, List<String> uuids) throws IOException {
        writeUuidList(filename, uuids);
    }

    /**
     * Scrie keywords_count.txt.
     *  Sortare: descrescator dupa frecventa sau alfabetica
     */
    public static void writeKeywordsCount(String filename, Map<String, Integer> keywordCount) throws IOException {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(keywordCount.entrySet());

        sorted.sort((a, b) -> {
            int cmp = b.getValue().compareTo(a.getValue());
            if (cmp != 0) return cmp;
            return a.getKey().compareTo(b.getKey());
        });

        try (FileWriter writer = new FileWriter(filename)) {
            for (Map.Entry<String, Integer> entry : sorted) {
                writer.write(entry.getKey() + " " + entry.getValue() + "\n");
            }
        }
    }

    /**
     * Scrie fisierul reports.txt, adunand toate statisticile colectate.
     */
    public static void writeReports(String filename,
                                    int duplicatesFound,
                                    int uniqueArticles,
                                    StatisticsCollector stats,
                                    Map<String, Integer> englishKeywords) throws IOException {

        try (FileWriter writer = new FileWriter(filename)) {

            writer.write("duplicates_found - " + duplicatesFound + "\n");
            writer.write("unique_articles - " + uniqueArticles + "\n");

            // Autorul cu cele mai multe articole
            String bestAuthor = stats.getBestAuthor();
            if (bestAuthor != null) {
                AtomicInteger count = stats.getAuthorCountMap().get(bestAuthor);
                if (count != null) {
                    writer.write("best_author - " + bestAuthor + " " + count.get() + "\n");
                }
            }

            // Limba cea mai frecventa
            String topLanguage = stats.getTopLanguage();
            if (topLanguage != null) {
                AtomicInteger count = stats.getLanguageCountMap().get(topLanguage);
                if (count != null) {
                    writer.write("top_language - " + topLanguage + " " + count.get() + "\n");
                }
            }

            // Categoria cea mai folosita
            String topCategory = stats.getTopCategory();
            if (topCategory != null) {
                AtomicInteger count = stats.getCategoryCountMap().get(topCategory);
                if (count != null) {
                    writer.write("top_category - " + topCategory + " " + count.get() + "\n");
                }
            }

            // Cel mai recent articol
            Article mostRecent = stats.getMostRecentArticle();
            if (mostRecent != null && mostRecent.getPublished() != null) {
                writer.write("most_recent_article - " +
                        mostRecent.getPublished().toString() + " " +
                        mostRecent.getUrl() + "\n");
            }

            // Cel mai frecvent keyword englezesc
            String topKeywordEn = findTopKeyword(englishKeywords);
            if (topKeywordEn != null) {
                writer.write("top_keyword_en - " +
                        topKeywordEn + " " +
                        englishKeywords.get(topKeywordEn) + "\n");
            }
        }
    }

    /**
     * Scrie un fisier simplu cu UUID-uri sortate (un UUID pe linie).
     */
    private static void writeUuidList(String filename, List<String> uuids) throws IOException {
        if (uuids == null || uuids.isEmpty()) {
            return;
        }

        List<String> sorted = new ArrayList<>(uuids);
        sorted.sort(String::compareTo);

        try (FileWriter writer = new FileWriter(filename)) {
            for (String uuid : sorted) {
                writer.write(uuid + "\n");
            }
        }
    }

    /**
     * Gaseste cuvantul-cheie in engleza cu cea mai mare frecventa.
     */
    private static String findTopKeyword(Map<String, Integer> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }

        String topKeyword = null;
        int maxCount = -1;

        for (Map.Entry<String, Integer> entry : keywords.entrySet()) {
            int count = entry.getValue();
            if (count > maxCount ||
                    (count == maxCount &&
                            (topKeyword == null || entry.getKey().compareTo(topKeyword) < 0))) {

                maxCount = count;
                topKeyword = entry.getKey();
            }
        }

        return topKeyword;
    }
}

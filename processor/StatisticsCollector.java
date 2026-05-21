package processor;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import model.Article;

/**
 * Colector de statistici thread safe pentru articole
 * Foloseste structuri concurente (ConcurrentHashMap, AtomicInteger)
 * astfel incat update-urile din thread-uri multiple sa fie sigure
 */
public class StatisticsCollector {

    // Numarul articolelor pentru fiecare autor
    private final Map<String, AtomicInteger> authorCount = new ConcurrentHashMap<>();

    // Numarul articolelor pentru fiecare limba normalizata
    private final Map<String, AtomicInteger> languageCount = new ConcurrentHashMap<>();

    // Numarul articolelor pentru fiecare categorie normalizata
    private final Map<String, AtomicInteger> categoryCount = new ConcurrentHashMap<>();

    // Retine articolul cel mai recent (determinare thread-safe)
    private final AtomicReference<Article> mostRecentArticle = new AtomicReference<>();

    /**
     * Proceseaza un articol si actualizeaza toate statisticile relevante
     * Parametrii language si categories trebuie sa fie deja normalizati
     */
    public void processArticle(Article article, String language, Iterable<String> categories) {
        if (article == null) {
            return;
        }

        if (article.getAuthor() != null && !article.getAuthor().isEmpty()) {
            authorCount.computeIfAbsent(article.getAuthor(), k -> new AtomicInteger(0))
                    .incrementAndGet();
        }

        if (language != null && !language.isEmpty()) {
            languageCount.computeIfAbsent(language, k -> new AtomicInteger(0))
                    .incrementAndGet();
        }

        if (categories != null) {
            for (String category : categories) {
                if (category != null && !category.isEmpty()) {
                    categoryCount.computeIfAbsent(category, k -> new AtomicInteger(0))
                            .incrementAndGet();
                }
            }
        }

        updateMostRecent(article);
    }

    /**
     * Actualizeaza articolul cel mai recent cu regulile urmatoare:
     * 1. Articolul cu timestamp mai mare castiga
     * 2. La egalitate, castiga articolul cu UUID lexicografic mai mic
     */
    private void updateMostRecent(Article article) {
        Instant published = article.getPublished();
        if (published == null) {
            return;
        }

        mostRecentArticle.updateAndGet(current -> {
            if (current == null || current.getPublished() == null) {
                return article;
            }

            Instant currentPublished = current.getPublished();
            int timeCompare = published.compareTo(currentPublished);

            if (timeCompare > 0) {
                return article;
            }

            if (timeCompare == 0 && article.getUuid().compareTo(current.getUuid()) < 0) {
                return article;
            }

            return current;
        });
    }

    public String getBestAuthor() {
        return findMaxEntry(authorCount);
    }

    public String getTopLanguage() {
        return findMaxEntry(languageCount);
    }

    public String getTopCategory() {
        return findMaxEntry(categoryCount);
    }

    public Article getMostRecentArticle() {
        return mostRecentArticle.get();
    }

    public Map<String, AtomicInteger> getAuthorCountMap() {
        return authorCount;
    }

    public Map<String, AtomicInteger> getLanguageCountMap() {
        return languageCount;
    }

    public Map<String, AtomicInteger> getCategoryCountMap() {
        return categoryCount;
    }

    /**
     * Gaseste cheia cu valoarea maxima dintr-o mapa AtomicInteger.
     */
    private String findMaxEntry(Map<String, AtomicInteger> map) {
        String maxKey = null;
        int maxValue = -1;

        for (Map.Entry<String, AtomicInteger> entry : map.entrySet()) {
            int value = entry.getValue().get();

            if (value > maxValue || (value == maxValue &&
                    (maxKey == null || entry.getKey().compareTo(maxKey) < 0))) {

                maxValue = value;
                maxKey = entry.getKey();
            }
        }

        return maxKey;
    }
}

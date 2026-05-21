package parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import model.Article;
import processor.KeywordExtractor;
import processor.StatisticsCollector;
import reader.ArticleReader;

/**
 * Clasa pentru procesarea paralela a articolelor.
 * Implementarea foloseste paralelizare pe chunk-uri (task-uri mici)
 */
public class ParallelProcessor {

    // Unitatiile de munca sunt luate de 800 pentru a se putea face mai multe
    // task-uri in paralel, pentru cresterea scalabilitatii
    private static final int CHUNK_SIZE = 800;

    /**
     * Structura ce retine articolul impreuna cu limba si categoriile normalizate
     * Folosita pentru etapa de generare output.
     */
    public static class ProcessedArticle {
        public final Article article;
        public final String language;
        public final List<String> categories;

        public ProcessedArticle(Article article, String language, List<String> categories) {
            this.article = article;
            this.language = language;
            this.categories = categories;
        }
    }

    /**
     * Rezultatul final al procesarii: articole procesate, statistici colectate
     * cuvinte cheie cu nr lor de aparaitii
     */
    public static class ProcessResult {
        public final List<ProcessedArticle> processedArticles;
        public final StatisticsCollector stats;
        public final Map<String, Integer> englishKeywords;

        public ProcessResult(List<ProcessedArticle> processedArticles,
                             StatisticsCollector stats,
                             Map<String, Integer> englishKeywords) {
            this.processedArticles = processedArticles;
            this.stats = stats;
            this.englishKeywords = englishKeywords;
        }
    }

    /**
     * Incarca articolele din fiecare fisier JSON in paralel
     */
    public static List<Article> loadArticles(List<String> articleFilePaths, int numThreads, String baseDir) throws Exception {
        int poolSize = Math.max(1, Math.min(numThreads, articleFilePaths.size()));
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        List<Future<List<Article>>> futures = new ArrayList<>();

        for (String filePath : articleFilePaths) {
            futures.add(executor.submit(() -> processSingleFile(filePath, baseDir)));
        }

        List<Article> combined = new ArrayList<>();
        for (Future<List<Article>> future : futures) {
            List<Article> partial = future.get();
            if (partial != null && !partial.isEmpty()) {
                combined.addAll(partial);
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        return combined;
    }

    /**
     * Proceseaza articolele in paralel prin impartirea lor in chunk-uri.
     * Fiecare chunk este procesat de un thread independent.
     */
    public static ProcessResult processArticles(List<Article> articles,
                                                Map<String, String> languageMap,
                                                Map<String, String> categoryMap,
                                                Set<String> linkingWords,
                                                int numThreads) throws Exception {

        // Obiect de statistici pentru thread-safe
        StatisticsCollector stats = new StatisticsCollector();

        // Mapa unde numaram cuvintele din articolele englezesti
        ConcurrentHashMap<String, Integer> englishKeywords = new ConcurrentHashMap<>();

        // Coada unde salvam articolele procesate
        ConcurrentLinkedQueue<ProcessedArticle> processed = new ConcurrentLinkedQueue<>();

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Void>> futures = new ArrayList<>();

        int total = articles.size();

        // Impartim articolele in chunk-uri si asignam fiecare chunk unui thread
        for (int start = 0; start < total; start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, total);
            List<Article> chunk = articles.subList(start, end);

            futures.add(executor.submit(() -> {
                processChunk(chunk, languageMap, categoryMap, linkingWords, stats, englishKeywords, processed);
                return null;
            }));
        }

        // Asteptam ca toate task urile sa fie finalizate
        for (Future<Void> future : futures) {
            future.get();
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        return new ProcessResult(new ArrayList<>(processed), stats, englishKeywords);
    }

    /**
     * Proceseaza un chunk de articole, pentru fiecare articol:
     *  normalizam limba, se extrag categriile valide, actualizam statisticile globale
     *  adaugam artcolul procesat in coada, extragem cuvinte cheie daca articolul este
     *  in limba engleza
     */
    private static void processChunk(List<Article> chunk,
                                     Map<String, String> languageMap,
                                     Map<String, String> categoryMap,
                                     Set<String> linkingWords,
                                     StatisticsCollector stats,
                                     ConcurrentHashMap<String, Integer> englishKeywords,
                                     ConcurrentLinkedQueue<ProcessedArticle> processedOut) {

        for (Article article : chunk) {
            String normalizedLanguage = resolveLanguage(article.getLanguage(), languageMap);
            List<String> validCategories = extractValidCategories(article, categoryMap);

            // Actualizam statistici
            stats.processArticle(article, normalizedLanguage, validCategories);

            // Salvam articolul procesat
            processedOut.add(new ProcessedArticle(article, normalizedLanguage, validCategories));

            // Daca este articol in engleza, extragem cuvinte cheie
            if (normalizedLanguage != null && "english".equalsIgnoreCase(normalizedLanguage)) {
                for (String keyword : KeywordExtractor.extractKeywords(article.getText(), linkingWords)) {
                    englishKeywords.merge(keyword, 1, Integer::sum);
                }
            }
        }
    }

    /**
     * Citeste un fisier individual de articole.
     */
    private static List<Article> processSingleFile(String filePath, String baseDir) {
        try {
            return ArticleReader.readArticlesFromFile(filePath, baseDir);
        } catch (Exception e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Normalizeaza limba articolului folosind mapa de limbi
     */
    private static String resolveLanguage(String language, Map<String, String> languageMap) {
        if (language == null) {
            return null;
        }
        return languageMap.get(language.trim().toLowerCase());
    }

    /**
     * Extrage categoriile valide ale articolului pe baza mapei de categorii
     * Eliminam duplicatele din acelasi articol folosind un set local
     */
    private static List<String> extractValidCategories(Article article, Map<String, String> categoryMap) {
        List<String> valid = new ArrayList<>();
        if (article.getCategories() == null) {
            return valid;
        }

        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String category : article.getCategories()) {
            if (category == null) {
                continue;
            }
            String normalized = categoryMap.get(category.trim().toLowerCase());
            if (normalized != null && seen.add(normalized)) {
                valid.add(normalized);
            }
        }
        return valid;
    }
}

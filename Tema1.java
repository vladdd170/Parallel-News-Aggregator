import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import model.Article;
import parallel.ParallelProcessor;
import processor.StatisticsCollector;
import reader.InputFileReader;
import writer.OutputWriter;

public class Tema1 {

    // Structura care retine rezultatele deduplicarii:
    // lista de articole unice si numarul de duplicate eliminate
    private static class DedupResult {
        final List<Article> uniqueArticles;
        final int duplicatesRemoved;

        DedupResult(List<Article> uniqueArticles, int duplicatesRemoved) {
            this.uniqueArticles = uniqueArticles;
            this.duplicatesRemoved = duplicatesRemoved;
        }
    }

    public static void main(String[] args) {
        // Verificam numarul argumentelor
        if (args.length != 3) {
            System.err.println("Usage: java Tema1 <threads> <articles_file> <inputs_file>");
            System.exit(1);
        }

        int numThreads = Integer.parseInt(args[0]);
        String articlesFile = args[1];
        String inputsFile = args[2];

        try {
            // Citim lista fisierelor auxiliare (limbi, categorii, linking words)
            List<String> inputFiles = InputFileReader.readListFile(inputsFile, null);
            if (inputFiles.size() < 3) {
                System.err.println("Error: inputs file must contain at least 3 files");
                System.exit(1);
            }

            String languagesFile = inputFiles.get(0);
            String categoriesFile = inputFiles.get(1);
            String linkingWordsFile = inputFiles.get(2);

            // Directorul de baza pentru rezolvarea path-urilor relative
            String baseDir = getBaseDirectory(inputsFile);

            // Incarcam listele de limbi, categorii si linking words
            List<String> languages = InputFileReader.readListFile(languagesFile, baseDir);
            List<String> categories = InputFileReader.readListFile(categoriesFile, baseDir);
            Set<String> linkingWords = InputFileReader.readLinkingWordsFile(linkingWordsFile, baseDir);

            // Construim maparile pentru normalizare
            Map<String, String> languageMap = buildLanguageMap(languages);
            Map<String, String> categoryMap = buildCategoryMap(categories);

            // Citim lista fisierelor cu articole
            List<String> articleFilePaths = readArticleFileList(articlesFile);
            String articlesBaseDir = getBaseDirectory(articlesFile);

            // Incarcam articolele din fisiere (I/O paralelizat)
            List<Article> parsedArticles = ParallelProcessor.loadArticles(articleFilePaths, numThreads, articlesBaseDir);

            // Eliminam duplicatele pe baza UUID si titlu
            DedupResult dedupResult = removeDuplicates(parsedArticles);
            List<Article> uniqueArticles = dedupResult.uniqueArticles;

            // Procesam articolele in paralel (chunk-based parallelism)
            ParallelProcessor.ProcessResult processed = ParallelProcessor.processArticles(
                    uniqueArticles, languageMap, categoryMap, linkingWords, numThreads);

            // Generam fisierele de output
            generateOutputs(processed.processedArticles, languages, categoryMap,
                    processed.stats, dedupResult.duplicatesRemoved, processed.englishKeywords);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Construieste mapa de limbi: lower_case -> forma originala
    private static Map<String, String> buildLanguageMap(List<String> languages) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String language : languages) {
            String trimmed = language.trim();
            if (!trimmed.isEmpty()) {
                map.put(trimmed.toLowerCase(), trimmed);
            }
        }
        return map;
    }

    // Construieste mapa de categorii: lower_case -> nume normalizat
    private static Map<String, String> buildCategoryMap(List<String> categories) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String category : categories) {
            String trimmed = category.trim();
            if (!trimmed.isEmpty()) {
                map.put(trimmed.toLowerCase(), normalizeCategoryName(trimmed));
            }
        }
        return map;
    }

    // Eliminam duplicatele dupa UUID si titlu
    private static DedupResult removeDuplicates(List<Article> articles) {
        Map<String, Integer> uuidCount = new HashMap<>();
        Map<String, Integer> titleCount = new HashMap<>();

        // Calculam aparitiile fiecarui UUID si titlu
        for (Article article : articles) {
            String uuidKey = safeKey(article.getUuid());
            if (!uuidKey.isEmpty()) {
                uuidCount.merge(uuidKey, 1, Integer::sum);
            }
            String titleKey = safeKey(article.getTitle());
            if (!titleKey.isEmpty()) {
                titleCount.merge(titleKey, 1, Integer::sum);
            }
        }

        // Selectam doar articolele care nu sunt duplicate
        List<Article> unique = new ArrayList<>();
        for (Article article : articles) {
            String uuidKey = safeKey(article.getUuid());
            String titleKey = safeKey(article.getTitle());
            boolean uuidDuplicate = !uuidKey.isEmpty() && uuidCount.getOrDefault(uuidKey, 0) > 1;
            boolean titleDuplicate = !titleKey.isEmpty() && titleCount.getOrDefault(titleKey, 0) > 1;

            if (!uuidDuplicate && !titleDuplicate) {
                unique.add(article);
            }
        }

        return new DedupResult(unique, articles.size() - unique.size());
    }

    // Returneaza cheia sigura (string gol daca valoarea este null)
    private static String safeKey(String value) {
        return value == null ? "" : value;
    }

    private static String getBaseDirectory(String filePath) {
        java.nio.file.Path path = java.nio.file.Paths.get(filePath);
        if (!path.isAbsolute()) {
            path = java.nio.file.Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        path = path.normalize();
        java.nio.file.Path parent = path.getParent();
        if (parent != null) {
            return parent.toAbsolutePath().toString();
        }
        return System.getProperty("user.dir");
    }

    private static List<String> readArticleFileList(String articlesFile) throws IOException {
        return InputFileReader.readListFile(articlesFile, null);
    }

    /**
     * Genereaza toate fisierele de output conform cerintelor
     */
    private static void generateOutputs(List<ParallelProcessor.ProcessedArticle> processedArticles,
                                        List<String> languages,
                                        Map<String, String> categoryMap,
                                        StatisticsCollector stats,
                                        int duplicatesFound,
                                        Map<String, Integer> englishKeywords) throws IOException {

        // Extragem doar articolele brute pentru all_articles.txt
        List<Article> articles = new ArrayList<>(processedArticles.size());
        for (ParallelProcessor.ProcessedArticle pa : processedArticles) {
            articles.add(pa.article);
        }

        OutputWriter.writeAllArticles("all_articles.txt", articles);

        // Fisiere pe limbi
        for (String language : languages) {
            String normalized = language.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            List<String> uuids = new ArrayList<>();
            for (ParallelProcessor.ProcessedArticle pa : processedArticles) {
                if (normalized.equals(pa.language)) {
                    uuids.add(pa.article.getUuid());
                }
            }
            OutputWriter.writeLanguageFile(normalized + ".txt", uuids);
        }

        // Fisiere pe categorii
        for (String normalizedCategory : categoryMap.values()) {
            List<String> uuids = new ArrayList<>();
            for (ParallelProcessor.ProcessedArticle pa : processedArticles) {
                if (pa.categories.contains(normalizedCategory)) {
                    uuids.add(pa.article.getUuid());
                }
            }
            OutputWriter.writeCategoryFile(normalizedCategory + ".txt", uuids);
        }

        // Fisierul de cuvinte cheie si rapoartele finale
        OutputWriter.writeKeywordsCount("keywords_count.txt", englishKeywords);
        OutputWriter.writeReports("reports.txt", duplicatesFound, articles.size(), stats, englishKeywords);
    }

    // Normalizeaza numele unei categorii (elimina virgule si spatii multiple)
    private static String normalizeCategoryName(String category) {
        String withoutCommas = category.replace(",", "");
        return withoutCommas.trim().replaceAll("\\s+", "_");
    }
}

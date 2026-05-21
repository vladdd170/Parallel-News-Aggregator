package reader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import model.Article;

/**
 * Clasa care citește fisierele de articole si parseaza continutul JSON.
 */
public class ArticleReader {

    /**
     * Citeste un fisier care contine o lista de fisiere de articole.
     * Prima linie reprezinta numarul de fisiere, restul sunt path-uri.
     */
    public static List<Article> readArticlesFromFileList(String articlesListPath) throws IOException {
        String baseDir = getBaseDirectory(articlesListPath);
        List<String> articleFilePaths = readFileList(articlesListPath, baseDir);
        List<Article> allArticles = new ArrayList<>();

        // Parcurgem fiecare fisier listat si adaugam articolele gasite
        for (String filePath : articleFilePaths) {
            List<Article> articles = readArticlesFromFile(filePath, baseDir);
            allArticles.addAll(articles);
        }

        return allArticles;
    }

    private static String getBaseDirectory(String filePath) {
        Path path = Paths.get(filePath);

        // Daca este relativ, il interpretam relativ la working directory
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }

        path = path.normalize();
        Path parent = path.getParent();

        return parent != null ? parent.toAbsolutePath().toString()
                : System.getProperty("user.dir");
    }

    /**
     * Citeste un fisier care contine o lista de path-uri (prima linie este count).
     * Returneaza doar path-urile, nu numarul.
     */
    private static List<String> readFileList(String filePath, String baseDir) throws IOException {
        List<String> result = new ArrayList<>();
        Path path = resolvePath(filePath, baseDir);

        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String firstLine = reader.readLine(); // count (ignorat)
            if (firstLine != null) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        result.add(line);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Citeste si parseaza un fisier JSON de articole.
     */
    public static List<Article> readArticlesFromFile(String filePath) throws IOException {
        return readArticlesFromFile(filePath, null);
    }

    /**
     * La fel, citeste si parseaza dar fisierul contine un array JSON cu obiecte.
     */
    public static List<Article> readArticlesFromFile(String filePath, String baseDir) throws IOException {
        List<Article> articles = new ArrayList<>();
        Path path = resolvePath(filePath, baseDir);

        // Citim fisierul JSON complet
        StringBuilder jsonContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line).append("\n");
            }
        }

        // Parsam array-ul JSON
        List<SimpleJsonParser.JsonObject> jsonObjects =
                SimpleJsonParser.parseArray(jsonContent.toString());

        // Convertim fiecare obiect JSON intr-un Article
        for (SimpleJsonParser.JsonObject jsonObject : jsonObjects) {
            Article article = parseArticle(jsonObject);
            if (article != null) {
                articles.add(article);
            }
        }

        return articles;
    }

    /**
     * Converteste un obiect JSON parsat intr-un obiect Article.
     */
    private static Article parseArticle(SimpleJsonParser.JsonObject json) {
        try {
            // Pastram exact string-urile (fara normalizare) pentru detectarea corecta a duplicatelor
            String uuid = json.optString("uuid", "");
            String title = json.optString("title", "");
            String author = json.optString("author", "").trim();
            String url = json.optString("url", "").trim();
            String text = json.optString("text", "");
            String publishedStr = json.optString("published", "");
            String language = json.optString("language", "").trim();

            // Parsam data ISO 8601
            Instant published = null;
            if (!publishedStr.isEmpty()) {
                try {
                    published = Instant.parse(publishedStr);
                } catch (Exception e) {
                    // Daca data este invalida, o lasam null
                }
            }

            // Parsam lista de categorii
            List<String> categories = new ArrayList<>();
            for (String category : json.getStringList("categories")) {
                if (category != null) {
                    String trimmed = category.trim();
                    if (!trimmed.isEmpty()) {
                        categories.add(trimmed);
                    }
                }
            }

            // Construim articolul
            return new Article(uuid, title, author, url, text, published, language, categories);

        } catch (Exception e) {
            // Orice eroare la parsing - articolul e ignorat
            return null;
        }
    }

    /**
     * Rezolva path-uri relative folosind un director baza, daca este furnizat
     * Normalizeaza path-ul pentru a elimina partile redundante
     */
    private static Path resolvePath(String filePath, String baseDir) {
        Path path = Paths.get(filePath);

        // Daca path-ul este relativ, il rezolvam fata de baseDir sau working dir
        if (!path.isAbsolute()) {
            if (baseDir != null) {
                Path base = Paths.get(baseDir);

                // Daca baseDir nu era absolut, il aducem in forma absoluta
                if (!base.isAbsolute()) {
                    base = Paths.get(System.getProperty("user.dir"))
                            .resolve(base).normalize();
                }

                path = base.resolve(path).normalize();
            } else {
                path = Paths.get(System.getProperty("user.dir"))
                        .resolve(path).normalize();
            }
        } else {
            path = path.normalize();
        }

        return path;
    }
}

package reader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reader pentru fisiere de input
 * Clasa nu pastreaza stare interna, deci poate fi folosita simultan din mai multe thread-uri
 */
public class InputFileReader {

    public static List<String> readListFile(String filePath) throws IOException {
        return readListFile(filePath, null);
    }

    /**
     * Varianta cu baseDir, folosit pentru a rezolva path-uri relative.
     * Citeste liniile din fisier si returneaza valorile (ignorand count-ul).
     */
    public static List<String> readListFile(String filePath, String baseDir) throws IOException {
        List<String> result = new ArrayList<>();
        Path path = resolvePath(filePath, baseDir);

        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {

            String firstLine = reader.readLine();
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

    public static Set<String> readLinkingWordsFile(String filePath) throws IOException {
        return readLinkingWordsFile(filePath, null);
    }

    /**
     * Varianta cu baseDir pentru linking words
     * Folosim Set deoarece linking words sunt doar verificate pentru existenta
     */
    public static Set<String> readLinkingWordsFile(String filePath, String baseDir) throws IOException {
        Set<String> result = new HashSet<>();
        List<String> words = readListFile(filePath, baseDir);
        for (String word : words) {
            result.add(word.toLowerCase());
        }
        return result;
    }

    /**
     * Functie de rezolvare a path-urilor
     */
    private static Path resolvePath(String filePath, String baseDir) {
        Path path = Paths.get(filePath);

        // Path absolut - doar normalize
        if (path.isAbsolute()) {
            return path.normalize();
        }

        // Daca avem baseDir, tratam path-ul relativ fata de acest director
        if (baseDir != null && !baseDir.isEmpty()) {
            Path base = Paths.get(baseDir);

            // Daca baseDir nu este absolut, il facem absolut relativ la working dir
            if (!base.isAbsolute()) {
                base = Paths.get(System.getProperty("user.dir"))
                        .resolve(base)
                        .normalize();
            }

            return base.resolve(path).normalize();
        }

        // path relativ
        return Paths.get(System.getProperty("user.dir"))
                .resolve(path)
                .normalize();
    }
}

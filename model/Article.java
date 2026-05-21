package model;

import java.time.Instant;
import java.util.List;

/**
 * Reprezinta un articol de stiri cu toate metadatele sale
 */
public class Article {
    private final String uuid;
    private final String title;
    private final String author;
    private final String url;
    private final String text;
    private final Instant published;
    private final String language;
    private final List<String> categories;

    public Article(String uuid, String title, String author, String url, 
                   String text, Instant published, String language, 
                   List<String> categories) {
        this.uuid = uuid;
        this.title = title;
        this.author = author;
        this.url = url;
        this.text = text;
        this.published = published;
        this.language = language;
        this.categories = categories != null ? List.copyOf(categories) : List.of();
    }

    public String getUuid() {
        return uuid;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getUrl() {
        return url;
    }

    public String getText() {
        return text;
    }

    public Instant getPublished() {
        return published;
    }

    public String getLanguage() {
        return language;
    }

    public List<String> getCategories() {
        return categories;
    }
}



package com.pgoogol.kitchen.recipe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * To, z czego powstał przepis: adres strony, wklejony tekst, zdjęcia albo
 * wiadomość z czatu. Niezmienne — poprawki idą do przepisu, nie do źródła.
 *
 * <p>Kolumny wypełniane przez import (surowa treść, JSON-LD, pliki) dochodzą
 * razem z importem. Przepis pisany ręcznie ma tu {@link SourceKind#MANUAL}.</p>
 */
@Entity
@Table(name = "recipe_source")
public class RecipeSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceKind kind;

    @Column
    private String url;

    @Column(name = "url_normalized")
    private String urlNormalized;

    @Column(name = "site_name", length = 200)
    private String siteName;

    @Column(length = 200)
    private String author;

    @Column(name = "source_language", length = 5)
    private String sourceLanguage;

    @Column(name = "raw_text")
    private String rawText;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RecipeSource() {

    }

    public RecipeSource(SourceKind kind) {

        this.kind = Objects.requireNonNull(kind, "kind");
        this.createdAt = Instant.now();
    }

    public Long getId() {

        return id;
    }

    public SourceKind getKind() {

        return kind;
    }

    public String getUrl() {

        return url;
    }

    public void describeUrl(String url, String urlNormalized, String siteName, String author) {

        this.url = url;
        this.urlNormalized = urlNormalized;
        this.siteName = siteName;
        this.author = author;
        this.fetchedAt = Instant.now();
    }

    public String getUrlNormalized() {

        return urlNormalized;
    }

    public String getSiteName() {

        return siteName;
    }

    public String getAuthor() {

        return author;
    }

    public String getSourceLanguage() {

        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {

        this.sourceLanguage = sourceLanguage;
    }

    public String getRawText() {

        return rawText;
    }

    public void setRawText(String rawText) {

        this.rawText = rawText;
    }

    public Instant getFetchedAt() {

        return fetchedAt;
    }

    public Instant getCreatedAt() {

        return createdAt;
    }
}

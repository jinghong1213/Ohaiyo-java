package com.dailyresume.core;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One row from a browser's history table — a URL the user visited.
 *
 * <p>This is a Java <em>record</em>: a compact way to declare an
 * immutable data class. The compiler auto-generates the constructor,
 * getters (named url(), title(), etc.), equals(), hashCode() and toString().
 *
 * <p>The {@code @JsonProperty} annotations tell Jackson what JSON field name
 * to use when reading/writing — we use snake_case in JSON (browser, last_visit)
 * to match the Python version's data files.
 */
public record Visit(
        @JsonProperty("browser") String browser,
        @JsonProperty("url") String url,
        @JsonProperty("title") String title,
        @JsonProperty("visit_count") int visitCount,
        @JsonProperty("last_visit") String lastVisit
) {}

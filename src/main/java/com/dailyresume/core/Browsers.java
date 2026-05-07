package com.dailyresume.core;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * Reads browser history from Chromium-family browsers (Chrome, Edge).
 *
 * <p><strong>Why we copy the file first:</strong> Chrome locks its
 * {@code History} SQLite database while running, so we can't open it
 * directly. Standard trick: copy it to a temp file, query the copy,
 * delete the copy.
 *
 * <p><strong>Time format:</strong> Chrome stores {@code last_visit_time}
 * as <em>microseconds since 1601-01-01 UTC</em>. Unix time is millis
 * since 1970-01-01 UTC. We convert in helper methods below.
 */
public final class Browsers {

    /**
     * Number of seconds between 1601-01-01 and 1970-01-01 (the Unix epoch).
     * Hard-coded because it's a fixed historical constant.
     */
    private static final long CHROME_EPOCH_DELTA_SECONDS = 11_644_473_600L;

    private Browsers() {} // utility class — no instances

    /**
     * Convert a Chrome "microseconds since 1601" timestamp to a regular Instant.
     */
    private static Instant chromeTimeToInstant(long chromeUs) {
        long unixMillis = (chromeUs / 1000) - (CHROME_EPOCH_DELTA_SECONDS * 1000);
        return Instant.ofEpochMilli(unixMillis);
    }

    /**
     * Convert an Instant back to Chrome's microseconds-since-1601 format.
     * Used when building the WHERE clause: "last_visit_time >= since".
     */
    private static long instantToChromeTime(Instant instant) {
        long unixMillis = instant.toEpochMilli();
        return (unixMillis + CHROME_EPOCH_DELTA_SECONDS * 1000) * 1000;
    }

    /**
     * Replace Windows-style %ENV% placeholders with their actual values.
     * E.g. "%LOCALAPPDATA%\\Google\\..." → "C:\\Users\\You\\AppData\\Local\\Google\\..."
     */
    private static Path expandEnv(String path) {
        String result = path;
        // Pattern is %NAME% — pull each one and substitute.
        while (true) {
            int start = result.indexOf('%');
            if (start < 0) break;
            int end = result.indexOf('%', start + 1);
            if (end < 0) break;
            String name = result.substring(start + 1, end);
            String value = System.getenv(name);
            if (value == null) value = "";
            result = result.substring(0, start) + value + result.substring(end + 1);
        }
        return Paths.get(result);
    }

    /**
     * Read history for a single browser. Returns empty list if the DB
     * doesn't exist or anything fails — capture is best-effort.
     *
     * @param browserName    label saved into each Visit (e.g. "Chrome")
     * @param historyPath    config path to the History file (with %ENV% allowed)
     * @param since          only include rows visited at or after this time
     * @param minVisitCount  filter out URLs seen fewer than this many times
     */
    public static List<Visit> readHistory(
            String browserName,
            String historyPath,
            Instant since,
            int minVisitCount) {

        Path src = expandEnv(historyPath);
        if (!Files.exists(src)) return List.of();

        Path tmp;
        try {
            tmp = Files.createTempFile("history-", ".sqlite");
            // Copy because the original is locked by the running browser.
            Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[browsers] failed to copy " + browserName + ": " + e.getMessage());
            return List.of();
        }

        List<Visit> out = new ArrayList<>();
        // The temp copy is ours alone, so we don't need read-only flags.
        // Plain JDBC URL keeps the xerial driver happy on weird Windows paths.
        String url = "jdbc:sqlite:" + tmp.toAbsolutePath();

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT url, COALESCE(title, ''), visit_count, last_visit_time " +
                     "  FROM urls " +
                     " WHERE last_visit_time >= ? AND visit_count >= ? " +
                     " ORDER BY last_visit_time DESC")) {

            ps.setLong(1, instantToChromeTime(since));
            ps.setInt(2, minVisitCount);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Visit(
                            browserName,
                            rs.getString(1),
                            rs.getString(2),
                            rs.getInt(3),
                            chromeTimeToInstant(rs.getLong(4)).toString()
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[browsers] failed to query " + browserName + ": " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }

        return out;
    }

    /**
     * Read history for every browser configured in config.json.
     *
     * @param browserConfigs  the "browsers" array from config.json,
     *                        already parsed into Maps
     */
    public static List<Visit> readAll(
            List<Map<String, Object>> browserConfigs,
            Instant since,
            int minVisitCount) {

        List<Visit> all = new ArrayList<>();
        for (Map<String, Object> b : browserConfigs) {
            String name = String.valueOf(b.get("name"));
            String path = String.valueOf(b.get("history_path"));
            try {
                all.addAll(readHistory(name, path, since, minVisitCount));
            } catch (RuntimeException e) {
                System.err.println("[browsers] failed " + name + ": " + e.getMessage());
            }
        }
        return all;
    }
}

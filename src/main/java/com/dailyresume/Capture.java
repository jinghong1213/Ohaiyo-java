package com.dailyresume;

import com.dailyresume.core.*;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Capture daemon — write a snapshot of current state to {@code data/}.
 *
 * <p>Run on a Task Scheduler trigger (at logon, every 15 min, on lock).
 * Each run produces one {@code session_*.json} file plus a line in
 * today's capture log.
 */
public final class Capture {

    /** Project root — resolved from the working directory at startup. */
    private static final Path ROOT = Paths.get("").toAbsolutePath();
    private static final Path CONFIG_PATH = ROOT.resolve("config.json");
    private static final Path DATA_DIR    = ROOT.resolve("data");
    private static final Path LOG_DIR     = ROOT.resolve("log");

    public static void main(String[] args) throws Exception {
        // Load config.json into a generic Map<String,Object>. We use
        // TypeReference because Java erases generics at runtime, so
        // Jackson needs an explicit hint to know "Map<String,Object>".
        Map<String, Object> config;
        try {
            config = Storage.MAPPER.readValue(
                    CONFIG_PATH.toFile(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            log("config.json missing or invalid: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Pull settings with sensible defaults.
        int lookbackHours  = ((Number) config.getOrDefault("history_lookback_hours", 18)).intValue();
        int minVisits      = ((Number) config.getOrDefault("min_visit_count", 1)).intValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> browsers =
                (List<Map<String, Object>>) config.getOrDefault("browsers", List.of());
        @SuppressWarnings("unchecked")
        List<String> ignore =
                (List<String>) config.getOrDefault("ignore_processes", List.of());

        // Wall-clock for the snapshot (local zone). Browser history is
        // queried in absolute Instants so timezone doesn't matter for SQL.
        LocalDateTime now = LocalDateTime.now();
        Instant since = now.minusHours(lookbackHours)
                .atZone(ZoneId.systemDefault()).toInstant();

        // Read both data sources.
        List<Visit> visits = Browsers.readAll(browsers, since, minVisits);
        List<AppEntry> apps = Processes.snapshot(ignore);

        // Assemble the session payload and write it.
        Session s = new Session();
        s.capturedAt    = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        s.lookbackSince = since.toString();
        s.apps          = apps;
        s.visits        = visits;

        Path out = Storage.writeSession(DATA_DIR, s, now);
        log("wrote " + out.getFileName() + " — " + apps.size() + " apps, " + visits.size() + " visits");
        System.out.println("Wrote " + out);
    }

    /** Append one line to log/capture_YYYY-MM-DD.log. Best-effort. */
    private static void log(String line) {
        try {
            Files.createDirectories(LOG_DIR);
            String today = LocalDate.now().toString();
            Path file = LOG_DIR.resolve("capture_" + today + ".log");
            String stamp = LocalDateTime.now().withNano(0).toString();
            Files.writeString(
                    file,
                    "[" + stamp + "] " + line + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // If we can't even log, there's nothing useful we can do here.
        }
    }
}

package com.dailyresume.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;

/**
 * Read and write session snapshot files in {@code data/}.
 *
 * <p>One file per capture: {@code session_YYYY-MM-DD_HHMMSS.json}.
 * Jackson handles the actual JSON parsing/writing; this class just owns
 * the file naming, listing, and "find the latest before X" logic the
 * launcher needs.
 */
public final class Storage {

    public static final String SESSION_PREFIX = "session_";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    /** Configured Jackson mapper, shared. Pretty-prints + understands java.time. */
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private Storage() {}

    public static String sessionFilename(LocalDateTime now) {
        return SESSION_PREFIX + STAMP.format(now) + ".json";
    }

    /**
     * Write a session to disk. Creates the data dir if missing.
     * Returns the path it wrote.
     */
    public static Path writeSession(Path dataDir, Session session, LocalDateTime now)
            throws IOException {
        Files.createDirectories(dataDir);
        Path out = dataDir.resolve(sessionFilename(now));
        MAPPER.writeValue(out.toFile(), session);
        return out;
    }

    /** All session files, sorted by name (== sorted by capture time). */
    public static List<Path> listSessions(Path dataDir) throws IOException {
        if (!Files.exists(dataDir)) return List.of();
        try (Stream<Path> s = Files.list(dataDir)) {
            return s.filter(p -> p.getFileName().toString().startsWith(SESSION_PREFIX))
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /** Load and parse one session file. */
    public static Session loadSession(Path file) throws IOException {
        return MAPPER.readValue(file.toFile(), Session.class);
    }

    /**
     * Find the most recent session captured strictly before {@code before}.
     * The launcher uses this with {@code before = midnight today}, so it
     * picks up "yesterday's last snapshot" automatically.
     */
    public static Optional<Path> latestSessionBefore(Path dataDir, LocalDateTime before)
            throws IOException {
        Path winner = null;
        LocalDateTime winnerStamp = null;
        for (Path p : listSessions(dataDir)) {
            String stem = p.getFileName().toString();
            // Strip "session_" prefix and ".json" suffix to get the timestamp
            String stamp = stem.substring(SESSION_PREFIX.length(), stem.length() - ".json".length());
            LocalDateTime when;
            try {
                when = LocalDateTime.parse(stamp, STAMP);
            } catch (Exception ignored) {
                continue;
            }
            if (when.isBefore(before) && (winnerStamp == null || when.isAfter(winnerStamp))) {
                winner = p;
                winnerStamp = when;
            }
        }
        return Optional.ofNullable(winner);
    }
}

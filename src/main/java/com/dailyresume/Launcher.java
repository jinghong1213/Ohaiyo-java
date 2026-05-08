package com.dailyresume;

import com.dailyresume.core.*;
import com.dailyresume.ui.LauncherFrame;
import com.fasterxml.jackson.core.type.TypeReference;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * Morning launcher entry point.
 *
 * <p>Loads config, finds yesterday's most recent snapshot, then hands
 * everything to the Swing window in {@link LauncherFrame}.
 *
 * <p>Swing rule: anything that touches UI must run on the
 * <em>Event Dispatch Thread</em>. We use {@code SwingUtilities.invokeLater}
 * to switch threads before constructing the window.
 */
public final class Launcher {

    private static final Path ROOT = Paths.get("").toAbsolutePath();
    private static final Path CONFIG_PATH = ROOT.resolve("config.json");
    private static final Path DATA_DIR    = ROOT.resolve("data");

    public static void main(String[] args) throws Exception {
        // 1. Read config.
        Map<String, Object> config;
        try {
            config = Storage.MAPPER.readValue(
                    CONFIG_PATH.toFile(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            System.err.println("config.json missing or invalid: " + e.getMessage());
            System.exit(1);
            return;
        }

        // 2. Find the most recent capture from before midnight today.
        //    "Yesterday's last snapshot" by definition.
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Optional<Path> latest = Storage.latestSessionBefore(DATA_DIR, todayStart);

        if (latest.isEmpty()) {
            // Fall back to "any latest session" so the GUI still has something
            // to show on day one (before you've had a real "yesterday").
            List<Path> all = Storage.listSessions(DATA_DIR);
            if (all.isEmpty()) {
                JOptionPane.showMessageDialog(
                        null,
                        "No session snapshots found in " + DATA_DIR + "\n\n" +
                        "Run capture first:\n  java -jar ohayo.jar capture",
                        "Ohayo",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            latest = Optional.of(all.get(all.size() - 1));
        }

        Path sessionPath = latest.get();
        Session session = Storage.loadSession(sessionPath);

        // 3. Show the window on the Swing thread.
        final Map<String, Object> finalConfig = config;
        final Path finalPath = sessionPath;
        SwingUtilities.invokeLater(() -> new LauncherFrame(finalPath, session, finalConfig).setVisible(true));
    }
}

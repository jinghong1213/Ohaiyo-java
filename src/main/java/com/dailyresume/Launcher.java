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
        // 0. Tiny argument parsing — just one flag.
        boolean latestFlag = false;
        for (String a : args) {
            if ("--latest".equals(a)) latestFlag = true;
        }

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

        // 2. Pick which snapshot to load.
        //    Default = the newest from BEFORE midnight today (true "yesterday").
        //    --latest = the newest of any age — used for same-day demos where you
        //               capture, close some apps, then relaunch immediately.
        Optional<Path> latest;
        if (latestFlag) {
            List<Path> all = Storage.listSessions(DATA_DIR);
            latest = all.isEmpty() ? Optional.empty()
                                   : Optional.of(all.get(all.size() - 1));
        } else {
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            latest = Storage.latestSessionBefore(DATA_DIR, todayStart);

            // Day-one fallback: if there's no "yesterday" yet, take whatever exists.
            if (latest.isEmpty()) {
                List<Path> all = Storage.listSessions(DATA_DIR);
                if (!all.isEmpty()) {
                    latest = Optional.of(all.get(all.size() - 1));
                }
            }
        }

        if (latest.isEmpty()) {
            JOptionPane.showMessageDialog(
                    null,
                    "No session snapshots found in " + DATA_DIR + "\n\n" +
                    "Run capture first:\n  java -jar ohaiyo.jar capture",
                    "Ohaiyo",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Path sessionPath = latest.get();
        Session session = Storage.loadSession(sessionPath);

        // 3. Show the cat splash, then build the main window when it closes.
        //    Optional GIF at assets/cat.gif overrides the ASCII cat fallback.
        final Map<String, Object> finalConfig = config;
        final Path finalPath = sessionPath;
        final java.io.File catGif = ROOT.resolve("assets").resolve("cat.gif").toFile();

        SwingUtilities.invokeLater(() -> {
            Runnable showLauncher = () ->
                    new LauncherFrame(finalPath, session, finalConfig).setVisible(true);
            new com.dailyresume.ui.CatSplash(showLauncher, 1800, catGif).setVisible(true);
        });
    }
}

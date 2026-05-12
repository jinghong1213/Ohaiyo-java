package com.dailyresume.ui;

import com.dailyresume.core.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.stream.*;

/**
 * The Swing window the user sees in the morning.
 *
 * <p>Top: a read-only summary panel.
 * Middle: two scrollable columns of checkboxes — sites on the left, apps on the right.
 * Bottom: select-all / skip-all / launch buttons.
 *
 * <p>"Launch selected" shells out to the OS:
 * <ul>
 *   <li>URLs → use the configured {@code launch_command} for the matching browser</li>
 *   <li>Apps → run the saved exe path; fall back to the bare exe name if missing</li>
 * </ul>
 */
public class LauncherFrame extends JFrame {

    /** Cap on number of URLs shown — history is noisy, top-N keeps it scannable. */
    private static final int MAX_URLS_SHOWN = 25;

    private final Path sessionPath;
    private final Session session;
    private final Map<String, Object> config;
    private final Map<String, String> browserCmdByName;
    private final String defaultBrowserCmd;

    /** Each entry: the checkbox + the data row it represents. */
    private final List<Map.Entry<JCheckBox, Visit>> urlBoxes = new ArrayList<>();
    private final List<Map.Entry<JCheckBox, AppEntry>> appBoxes = new ArrayList<>();

    private final JLabel status = new JLabel(
            "( =^.^= )  tick what you want, untick what was noise.");

    @SuppressWarnings("unchecked")
    public LauncherFrame(Path sessionPath, Session session, Map<String, Object> config) {
        super("Ohaiyo — yesterday's session");
        this.sessionPath = sessionPath;
        this.session = session;
        this.config = config;

        // Build a quick lookup of "Chrome" → "cmd /c start chrome \"{url}\"".
        this.browserCmdByName = new HashMap<>();
        List<Map<String, Object>> browsers =
                (List<Map<String, Object>>) config.getOrDefault("browsers", List.of());
        for (Map<String, Object> b : browsers) {
            browserCmdByName.put(
                    String.valueOf(b.get("name")),
                    String.valueOf(b.getOrDefault("launch_command", "cmd /c start \"\" \"{url}\"")));
        }
        this.defaultBrowserCmd = browserCmdByName.values().stream()
                .findFirst().orElse("cmd /c start \"\" \"{url}\"");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(960, 640);
        setLocationRelativeTo(null); // center on screen
        buildUi();
    }

    // -----------------------------------------------------------------
    // UI construction
    // -----------------------------------------------------------------

    private void buildUi() {
        JPanel outer = new JPanel(new BorderLayout(8, 8));
        outer.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(outer);

        // ----- header: brand row + snapshot info, stacked -----
        // JLabel with HTML lets us italicize part of the text inline.
        // The CSS color makes the "ai" pop without shouting.
        JLabel brand = new JLabel(
                "<html>Oh<i><font color='#5b8def'>ai</font></i>yo</html>");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 26));

        JLabel snapshotLine = new JLabel("Loaded snapshot: " + sessionPath.getFileName());
        snapshotLine.setFont(snapshotLine.getFont().deriveFont(Font.PLAIN, 12f));
        snapshotLine.setForeground(new Color(0x666666));

        JPanel headerStack = new JPanel();
        headerStack.setLayout(new BoxLayout(headerStack, BoxLayout.Y_AXIS));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        snapshotLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerStack.add(brand);
        headerStack.add(snapshotLine);
        outer.add(headerStack, BorderLayout.NORTH);

        // ----- center: summary on top, two columns below -----
        JPanel center = new JPanel(new BorderLayout(8, 8));

        JTextArea summaryArea = new JTextArea(Summary.build(session));
        summaryArea.setEditable(false);
        summaryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane summaryScroll = new JScrollPane(summaryArea);
        summaryScroll.setPreferredSize(new Dimension(0, 180));
        center.add(summaryScroll, BorderLayout.NORTH);

        JPanel cols = new JPanel(new GridLayout(1, 2, 8, 0));
        cols.add(buildUrlPanel());
        cols.add(buildAppPanel());
        center.add(cols, BorderLayout.CENTER);

        outer.add(center, BorderLayout.CENTER);

        // ----- footer -----
        outer.add(buildFooter(), BorderLayout.SOUTH);
    }

    /** Left column — URLs grouped by domain, deduped, capped at MAX_URLS_SHOWN. */
    private JComponent buildUrlPanel() {
        List<Visit> urls = pickTopUrls(session.visits != null ? session.visits : List.of());

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        if (urls.isEmpty()) {
            inner.add(new JLabel("(no history found)"));
        } else {
            for (Visit v : urls) {
                JCheckBox cb = new JCheckBox(labelForVisit(v), true);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                inner.add(cb);
                urlBoxes.add(Map.entry(cb, v));
            }
        }

        JScrollPane scroll = new JScrollPane(inner);
        scroll.setBorder(BorderFactory.createTitledBorder("Sites to reopen"));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** Right column — apps the user had open. */
    private JComponent buildAppPanel() {
        List<AppEntry> apps = session.apps != null ? session.apps : List.of();

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        if (apps.isEmpty()) {
            inner.add(new JLabel("(no apps captured)"));
        } else {
            for (AppEntry a : apps) {
                String hint = (a.windowTitles != null && !a.windowTitles.isEmpty())
                        ? " — " + a.windowTitles.get(0) : "";
                JCheckBox cb = new JCheckBox(a.name + hint, true);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                inner.add(cb);
                appBoxes.add(Map.entry(cb, a));
            }
        }

        JScrollPane scroll = new JScrollPane(inner);
        scroll.setBorder(BorderFactory.createTitledBorder("Apps to reopen"));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** Bottom strip — status text on the left, action buttons on the right. */
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(status, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton selAll = new JButton("Select all");
        JButton skipAll = new JButton("Skip all");
        JButton launch = new JButton("Launch selected");
        selAll.addActionListener(e -> setAllChecked(true));
        skipAll.addActionListener(e -> setAllChecked(false));
        launch.addActionListener(e -> onLaunch());
        buttons.add(selAll);
        buttons.add(skipAll);
        buttons.add(launch);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private void setAllChecked(boolean v) {
        for (var p : urlBoxes) p.getKey().setSelected(v);
        for (var p : appBoxes) p.getKey().setSelected(v);
    }

    /**
     * Pick "likely tabs" from raw history: keep the highest-visit-count URL
     * per domain, then sort by recency. Caps at {@link #MAX_URLS_SHOWN}.
     */
    private static List<Visit> pickTopUrls(List<Visit> visits) {
        Map<String, Visit> byDomain = new LinkedHashMap<>();
        for (Visit v : visits) {
            String d = domainOf(v.url());
            if (d == null || d.isBlank()) continue;
            Visit cur = byDomain.get(d);
            if (cur == null || v.visitCount() > cur.visitCount()) {
                byDomain.put(d, v);
            }
        }
        return byDomain.values().stream()
                .sorted(Comparator.comparing(Visit::lastVisit).reversed())
                .limit(MAX_URLS_SHOWN)
                .collect(Collectors.toList());
    }

    private static String domainOf(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return url;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return url;
        }
    }

    private static String labelForVisit(Visit v) {
        String title = v.title() != null ? v.title() : "";
        if (title.length() > 80) title = title.substring(0, 80) + "…";
        return domainOf(v.url()) + " — " + (title.isBlank() ? v.url() : title);
    }

    // -----------------------------------------------------------------
    // Launch logic
    // -----------------------------------------------------------------

    // Cat frames used in the status bar while launches play out.
    private static final String CAT_BUSY = "( =^o^= )";
    private static final String CAT_IDLE = "( =^.^= )";
    /** Delay between successive launches so the cat status reads visibly. */
    private static final int LAUNCH_STEP_MS = 300;

    private void onLaunch() {
        // Build a single ordered queue of (label, action) pairs. Running it
        // one step per Swing-Timer tick is what makes the launch feel
        // animated — the cat status updates between each item.
        java.util.List<Map.Entry<String, Runnable>> queue = new ArrayList<>();

        for (var pair : urlBoxes) {
            if (!pair.getKey().isSelected()) continue;
            Visit v = pair.getValue();
            String tmpl = browserCmdByName.getOrDefault(v.browser(), defaultBrowserCmd);
            String label = v.title() != null && !v.title().isBlank() ? v.title() : v.url();
            if (label.length() > 40) label = label.substring(0, 40) + "…";
            String finalLabel = label;
            queue.add(Map.entry(finalLabel, () -> {
                try {
                    runShell(tmpl.replace("{url}", v.url()));
                } catch (IOException ex) {
                    System.err.println("failed url " + v.url() + ": " + ex.getMessage());
                }
            }));
        }
        for (var pair : appBoxes) {
            if (!pair.getKey().isSelected()) continue;
            AppEntry a = pair.getValue();
            queue.add(Map.entry(a.name, () -> {
                try {
                    if (a.exePath != null && Files.exists(Path.of(a.exePath))) {
                        new ProcessBuilder(a.exePath).start();
                    } else {
                        runShell("cmd /c start \"\" \"" + a.name + "\"");
                    }
                } catch (IOException ex) {
                    System.err.println("failed app " + a.name + ": " + ex.getMessage());
                }
            }));
        }

        runLaunchQueue(queue);
    }

    /**
     * Step through the launch queue one item per timer tick.
     *
     * <p>Each tick: pull (label, action) off the queue head, update the cat
     * status, run the action, then schedule the next tick. When the queue
     * empties we settle the status into "idle cat — done".
     */
    private void runLaunchQueue(java.util.List<Map.Entry<String, Runnable>> queue) {
        final int total = queue.size();
        if (total == 0) {
            status.setText(CAT_IDLE + "  nothing selected");
            return;
        }
        // Mutable counter inside a single-element array — lambdas need final.
        final int[] idx = {0};
        javax.swing.Timer t = new javax.swing.Timer(LAUNCH_STEP_MS, null);
        t.addActionListener(e -> {
            if (idx[0] >= total) {
                t.stop();
                status.setText(CAT_IDLE + "  done — launched " + total + " item(s).");
                return;
            }
            var entry = queue.get(idx[0]);
            status.setText(CAT_BUSY + "  launching " + entry.getKey() + "…  ("
                    + (idx[0] + 1) + "/" + total + ")");
            entry.getValue().run();
            idx[0]++;
        });
        // Fire the first step immediately so there's no awkward 300ms wait.
        t.setInitialDelay(0);
        t.start();
    }

    /** Run a shell command. Used for "cmd /c start ..." style launches. */
    private static void runShell(String cmd) throws IOException {
        // We pass the whole string to cmd.exe so {@code start} keyword works.
        new ProcessBuilder("cmd.exe", "/c", cmd).start();
    }
}

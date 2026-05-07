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

    private final JLabel status = new JLabel("Tick what you want, untick what was noise.");

    @SuppressWarnings("unchecked")
    public LauncherFrame(Path sessionPath, Session session, Map<String, Object> config) {
        super("Daily Resume — yesterday's session");
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

        // ----- header -----
        JLabel header = new JLabel("Loaded snapshot: " + sessionPath.getFileName());
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        outer.add(header, BorderLayout.NORTH);

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

    private void onLaunch() {
        int urls = 0, apps = 0;
        for (var pair : urlBoxes) {
            if (!pair.getKey().isSelected()) continue;
            Visit v = pair.getValue();
            String tmpl = browserCmdByName.getOrDefault(v.browser(), defaultBrowserCmd);
            try {
                runShell(tmpl.replace("{url}", v.url()));
                urls++;
            } catch (IOException ex) {
                System.err.println("failed url " + v.url() + ": " + ex.getMessage());
            }
        }
        for (var pair : appBoxes) {
            if (!pair.getKey().isSelected()) continue;
            AppEntry a = pair.getValue();
            try {
                if (a.exePath != null && Files.exists(Path.of(a.exePath))) {
                    new ProcessBuilder(a.exePath).start();
                } else {
                    runShell("cmd /c start \"\" \"" + a.name + "\"");
                }
                apps++;
            } catch (IOException ex) {
                System.err.println("failed app " + a.name + ": " + ex.getMessage());
            }
        }
        status.setText("Launched " + urls + " URL(s) and " + apps + " app(s) at "
                + LocalDateTime.now().withNano(0));
    }

    /** Run a shell command. Used for "cmd /c start ..." style launches. */
    private static void runShell(String cmd) throws IOException {
        // We pass the whole string to cmd.exe so {@code start} keyword works.
        new ProcessBuilder("cmd.exe", "/c", cmd).start();
    }
}

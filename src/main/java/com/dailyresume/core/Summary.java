package com.dailyresume.core;

import java.net.URI;
import java.util.*;
import java.util.stream.*;

/**
 * Builds the human-readable "yesterday at a glance" text the
 * morning launcher displays at the top of its window.
 *
 * <p>Pure formatting — no I/O. Takes a {@link Session} and
 * returns a multi-line String.
 */
public final class Summary {

    private Summary() {}

    /** "https://www.linkedin.com/foo" → "linkedin.com" (best-effort). */
    private static String domainOf(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return url;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return url;
        }
    }

    public static String build(Session s) {
        List<AppEntry> apps = s.apps != null ? s.apps : List.of();
        List<Visit> visits = s.visits != null ? s.visits : List.of();

        // Count visits per domain, then keep the top 5.
        Map<String, Long> byDomain = visits.stream()
                .map(v -> domainOf(v.url()))
                .filter(d -> d != null && !d.isBlank())
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));

        List<Map.Entry<String, Long>> top5 = byDomain.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("Yesterday's last snapshot — ").append(s.capturedAt).append('\n');
        sb.append('\n');
        sb.append("Apps with windows: ").append(apps.size()).append('\n');
        sb.append("URLs in history (filtered): ").append(visits.size()).append('\n');
        sb.append('\n');
        sb.append("Top sites:\n");
        if (top5.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (var e : top5) sb.append("  • ").append(e.getKey())
                    .append(" (").append(e.getValue()).append(")\n");
        }
        sb.append('\n');
        sb.append("Apps:\n");
        int shown = 0;
        for (AppEntry a : apps) {
            if (shown >= 10) break;
            String title = (a.windowTitles != null && !a.windowTitles.isEmpty())
                    ? a.windowTitles.get(0) : "";
            sb.append("  • ").append(a.name);
            if (!title.isEmpty()) sb.append(" — ").append(title);
            sb.append('\n');
            shown++;
        }
        if (apps.size() > 10) sb.append("  …and ").append(apps.size() - 10).append(" more\n");

        return sb.toString();
    }
}

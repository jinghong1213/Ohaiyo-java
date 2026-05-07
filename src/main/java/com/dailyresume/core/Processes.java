package com.dailyresume.core;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC;
import com.sun.jna.ptr.IntByReference;

import java.util.*;

/**
 * Snapshots running applications that have a visible top-level window.
 *
 * <p>Java doesn't ship a "list windows" API, so we use <strong>JNA</strong>
 * (Java Native Access) to call three Win32 functions:
 * <ul>
 *   <li>{@code EnumWindows} — visit every top-level window once</li>
 *   <li>{@code GetWindowText} — fetch each window's title bar text</li>
 *   <li>{@code GetWindowThreadProcessId} — find which process owns a window</li>
 * </ul>
 * Then for each unique process we open a handle and call
 * {@code QueryFullProcessImageName} to learn the .exe path.
 *
 * <p>The result is collapsed to <em>one entry per executable</em>, which
 * matches what a person calls "an open app" (Chrome with 12 tabs is one
 * Chrome, not 12 windows).
 */
public final class Processes {

    private Processes() {} // utility class

    /**
     * Take the snapshot.
     *
     * @param ignoreProcesses  exe names to skip (e.g. "explorer.exe").
     *                         Match is case-insensitive.
     */
    public static List<AppEntry> snapshot(List<String> ignoreProcesses) {
        Set<String> ignore = new HashSet<>();
        for (String p : ignoreProcesses) ignore.add(p.toLowerCase(Locale.ROOT));

        // Step 1 — collect (pid → titles) for every visible top-level window.
        Map<Integer, List<String>> pidToTitles = enumerateVisibleWindows();

        // Step 2 — for each unique pid, build an AppEntry.
        // LinkedHashMap keeps insertion order so output is stable.
        Map<String, AppEntry> byExe = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<String>> e : pidToTitles.entrySet()) {
            int pid = e.getKey();
            String exePath = queryProcessImagePath(pid);          // may be null
            String exeName = exeNameFromPath(exePath, pid);       // fallback to "pid-1234"
            if (ignore.contains(exeName.toLowerCase(Locale.ROOT))) continue;

            // Multiple windows can belong to the same exe (e.g. several
            // Chrome windows). Merge their titles into one AppEntry.
            AppEntry app = byExe.computeIfAbsent(exeName,
                    k -> new AppEntry(exeName, exePath, new ArrayList<>(), pid));
            for (String title : e.getValue()) {
                if (!app.windowTitles.contains(title)) app.windowTitles.add(title);
            }
        }

        // Sort alphabetically so the morning launcher shows a consistent list.
        List<AppEntry> result = new ArrayList<>(byExe.values());
        result.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
        return result;
    }

    // -----------------------------------------------------------------
    // JNA helpers — wrap the Win32 calls so the snapshot() method stays clean.
    // -----------------------------------------------------------------

    /**
     * Walk every visible top-level window with a non-empty title and
     * return a map of {pid → list of window titles}.
     *
     * <p>{@code EnumWindows} is a callback API: we hand Windows a function,
     * Windows calls it once per window. Inside the callback we keep the
     * accumulator alive via the closure on {@code result}.
     */
    private static Map<Integer, List<String>> enumerateVisibleWindows() {
        Map<Integer, List<String>> result = new LinkedHashMap<>();

        WNDENUMPROC callback = (HWND hwnd, com.sun.jna.Pointer data) -> {
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) return true; // continue

            // Pull window title (bounded buffer, 512 chars is plenty).
            char[] buf = new char[512];
            int len = User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
            if (len == 0) return true;
            String title = new String(buf, 0, len);

            // Find which process owns this window.
            IntByReference pidRef = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
            int pid = pidRef.getValue();
            if (pid == 0) return true;

            result.computeIfAbsent(pid, k -> new ArrayList<>()).add(title);
            return true;
        };

        User32.INSTANCE.EnumWindows(callback, null);
        return result;
    }

    /**
     * Open the process and ask Windows for its full executable path.
     * Returns null if we can't access it (some system processes refuse).
     */
    private static String queryProcessImagePath(int pid) {
        // PROCESS_QUERY_LIMITED_INFORMATION (0x1000) is the lightweight
        // permission needed for QueryFullProcessImageName.
        HANDLE h = Kernel32.INSTANCE.OpenProcess(
                0x1000,            // PROCESS_QUERY_LIMITED_INFORMATION
                false,
                pid);
        if (h == null || h.equals(WinNT.INVALID_HANDLE_VALUE)) return null;
        try {
            char[] buf = new char[1024];
            IntByReference size = new IntByReference(buf.length);
            boolean ok = Kernel32.INSTANCE.QueryFullProcessImageName(h, 0, buf, size);
            if (!ok || size.getValue() == 0) return null;
            return new String(buf, 0, size.getValue());
        } finally {
            Kernel32.INSTANCE.CloseHandle(h); // always release the handle
        }
    }

    /**
     * Pull the file name out of a full path. If we don't have a path at all
     * (Windows refused), invent a synthetic name from the pid so we still
     * have something to show in the UI.
     */
    private static String exeNameFromPath(String path, int pid) {
        if (path == null || path.isBlank()) return "pid-" + pid;
        int slash = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}

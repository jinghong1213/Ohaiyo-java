# Ohayo — Java edition

> *Ohayo* (おはよう) — Japanese for "good morning."

Same idea as the Python version, written in Java so you can read it as a learning
project. Comments are deliberately heavy in the source files — focus on the
"why", not the syntax.

## How the pieces fit

```
                          ┌─────────────────────┐
                          │    config.json      │  (browsers, ignore list, etc.)
                          └─────────┬───────────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              │                     │                     │
        ┌───────────┐         ┌───────────┐         ┌───────────┐
        │ Capture   │  →      │ Storage   │   ←     │ Launcher  │
        │ (daemon)  │  writes │ data/*.   │  reads  │ (Swing)   │
        │           │  JSON   │ json      │         │           │
        └─────┬─────┘         └───────────┘         └─────┬─────┘
              │                                           │
   ┌──────────┴──────────┐                       ┌────────┴────────┐
   │ Browsers (sqlite)   │                       │ LauncherFrame    │
   │ Processes (JNA)     │                       │   (the UI)       │
   └─────────────────────┘                       └──────────────────┘
```

## Project layout

```
Ohayo-java/
├── pom.xml                              ← Maven build config
├── config.json
├── src/main/java/com/dailyresume/
│   ├── Main.java                        ← entry dispatcher (capture vs. launcher)
│   ├── Capture.java                     ← daemon entry
│   ├── Launcher.java                    ← GUI entry
│   ├── core/
│   │   ├── Visit.java                   ← record (one history row)
│   │   ├── AppEntry.java                ← one open app
│   │   ├── Session.java                 ← whole snapshot
│   │   ├── Browsers.java                ← Chrome/Edge SQLite reader
│   │   ├── Processes.java               ← visible-window snapshot via JNA
│   │   ├── Storage.java                 ← JSON read/write
│   │   └── Summary.java                 ← human-readable digest
│   └── ui/
│       └── LauncherFrame.java           ← Swing window
├── scripts/run_capture.bat              ← Task Scheduler entry
├── data/                                ← session JSON files (one per capture)
└── log/                                 ← daily activity logs
```

## Build

```
mvn package
```

This produces `target/ohayo.jar` — a "fat jar" containing every dependency,
so you only need Java to run it.

## Run

```
java -jar target/ohayo.jar capture     # take a snapshot
java -jar target/ohayo.jar launcher    # open the morning GUI
```

## Demo / same-day testing

By default the launcher only shows snapshots from **before midnight today**
(the "yesterday" definition). For demos or quick iteration, pass `--latest`
to load whatever's newest:

```
java -jar target/ohayo.jar capture
# close a few apps to simulate "shutting down"
java -jar target/ohayo.jar launcher --latest
```

## Key things to read first (for learning)

| File | Why it's interesting |
|---|---|
| `Browsers.java` | SQLite from Java; copying a locked DB; Chrome's weird epoch |
| `Processes.java` | JNA basics — calling Win32 API from Java without writing C |
| `LauncherFrame.java` | Swing layout patterns — BorderLayout, BoxLayout, JScrollPane |
| `Storage.java` | Jackson JSON, plus a tiny "find latest before X" finder |
| `Main.java` | Single-jar dispatch trick (one main, multiple "modes") |

## Phase 1 vs Phase 2 (same as Python version)

- **Phase 1 (this code):** browser *history* + visible-window snapshot. Easy and noisy.
- **Phase 2 (later):** parse Chrome's `Sessions/` files for actual open tabs. Cleaner.

# Daily Resume — Java edition

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
daily-resume-java/
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

This produces `target/daily-resume.jar` — a "fat jar" containing every dependency,
so you only need Java to run it.

## Run

```
java -jar target/daily-resume.jar capture     # take a snapshot
java -jar target/daily-resume.jar launcher    # open the morning GUI
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

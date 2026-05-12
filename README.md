# Oh*ai*yo — Java edition

> Pronounced like *ohayō* (おはよう), Japanese for "good morning." The
> stylized "*ai*" is a nod to the AI-assist that helps build it.

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
Ohaiyo-java/
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

This produces `target/ohaiyo.jar` — a "fat jar" containing every dependency,
so you only need Java to run it.

## Run

```
java -jar target/ohaiyo.jar capture     # take a snapshot
java -jar target/ohaiyo.jar launcher    # open the morning GUI
```

## Demo / same-day testing

By default the launcher only shows snapshots from **before midnight today**
(the "yesterday" definition). For demos or quick iteration, pass `--latest`
to load whatever's newest:

```
java -jar target/ohaiyo.jar capture
# close a few apps to simulate "shutting down"
java -jar target/ohaiyo.jar launcher --latest
```

## The cat 🐱

The launcher opens with a small splash window: a blinking ASCII cat
`( =^.^= )` plus the *Oh*ai*yo!* greeting. After ~1.8 seconds (or one
click) the main window appears. While launches play out, the same cat
"purrs" through the status bar one item at a time.

**Want a real animated cat?** Drop a GIF at `assets/cat.gif`. Swing's
`ImageIcon` animates it natively in `CatSplash.java`, no extra code
needed.

## Key things to read first (for learning)

| File | Why it's interesting |
|---|---|
| `CatSplash.java` | Undecorated JWindow, Swing Timer for animation, click-to-dismiss |
| `Browsers.java` | SQLite from Java; copying a locked DB; Chrome's weird epoch |
| `Processes.java` | JNA basics — calling Win32 API from Java without writing C |
| `LauncherFrame.java` | Swing layout patterns — BorderLayout, BoxLayout, JScrollPane |
| `Storage.java` | Jackson JSON, plus a tiny "find latest before X" finder |
| `Main.java` | Single-jar dispatch trick (one main, multiple "modes") |

## Phase 1 vs Phase 2 (same as Python version)

- **Phase 1 (this code):** browser *history* + visible-window snapshot. Easy and noisy.
- **Phase 2 (later):** parse Chrome's `Sessions/` files for actual open tabs. Cleaner.

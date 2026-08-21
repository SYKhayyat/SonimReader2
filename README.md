# Sonim Reader

A keypad-driven plain-text and Org-mode file reader for Sonim rugged phones (built and tuned for the **Sonim XP5s**, AOSP Android 8.1). It opens `.txt` and `.org` files from device storage and lets you read, navigate, search, bookmark, and edit them entirely with the phone's physical keys — no touchscreen required.

## Status

Working and in use. Two features that this README used to list were unreachable
on the device, and both are fixed:

- **OK / Center now reaches the reader**, so in-file search and the editor open.
  The focused `ListView` is an `AbsListView`, which claims
  `KEYCODE_DPAD_CENTER` and `KEYCODE_ENTER` for an item click and consumed them
  before the key ever reached `KeyCommandRouter`. `MainActivity` now hands that
  key to its own callbacks first, keeping the tracking that makes tap-versus-hold
  work.
- **Night mode has moved from `5` to a long press of `#`.** It was written as a
  `case` below a guard clause that returns for every number key, so it was
  unreachable from the day it was written — and `5` was never free anyway,
  because `1`–`9` are the nine bookmark slots. Night mode defaults to *on*, so
  the practical effect was that it could not be turned off.

`KeyCommandRouterTest` now covers the key map. It is the first test the `ui`
package has had, and the second bug is exactly the kind it catches.

## Documentation

| | |
| --- | --- |
| [docs/ONBOARDING.md](docs/ONBOARDING.md) | Build, install, drive it, the architecture, the key-dispatch path, and the first contributions worth making. |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Symptom-first: keys, storage, encoding, Org, bookmarks, building and on-device debugging. |

## Features

Verified from the source:

- **Reads `.txt` and `.org` files** picked from internal storage or SD card via a focus/D-pad file browser.
- **Automatic encoding detection** (BOM, strict UTF-8 validation, Windows-1255 for Hebrew, ISO-8859-1 fallback) with manual cycling between **UTF-8 → Windows-1255 → ISO-8859-1**.
- **RTL/LTR support** — auto-detected per file (so Hebrew and other right-to-left text renders correctly) and manually toggleable.
- **Adjustable font size** (10–40 pt) and **night mode** (dark background, on by default; toggle with a long press of `#`).
- **Line-by-line scrolling and paging**, plus **auto-scroll** at a fixed interval.
- **In-file search** with next/previous match navigation and a match count.
- **Nine bookmark slots** (keys 1–9), each holding multiple positions per file; tapping jumps to the next bookmark in a slot.
- **Jump to percentage** of the document.
- **Heads-up display (HUD)** overlay showing clock, battery percentage, and reading progress.
- **Org-mode outline support** for `.org` files: fold/unfold sections, jump between headings, and open a Contents list to navigate by heading.
- **In-place editing** — a full-screen editor lets you modify the file with the T9 keypad and D-pad caret movement, then overwrite the original file (with confirmation and unsaved-changes prompts). Edits are saved atomically.
- **Remembers per-file state** across sessions: last reading position, chosen encoding, RTL setting, and a list of up to 15 recent files (stored in `SharedPreferences`). The screen is kept on while reading.

## Target device

Designed for **Sonim keypad phones**, specifically the **Sonim XP5s** running AOSP Android 8.1 (API 27). The build deliberately targets SDK 28 to keep the legacy file-based external-storage model (rather than scoped storage), and the entire UI is operated from the physical keypad — the app declares the touchscreen as not required.

## Key mappings

All navigation is by physical key. Behaviour differs slightly between plain-text and `.org` files.

### Plain text

| Key | Action |
| --- | --- |
| Volume + / − | Increase / decrease font size |
| OK / Center (tap) | Toggle the search box |
| OK / Center (hold) | Enter the editor (after a confirm prompt) |
| D-pad up / down | Scroll line by line |
| D-pad left / right | Page up / down (in search: previous / next match) |
| # (tap) | Cycle text encoding |
| # (hold) | Toggle night mode |
| * | Toggle time / battery / progress HUD |
| Menu | Toggle auto-scroll |
| 1–9 (tap) | Jump to next bookmark in that slot |
| 1–9 (hold) | Save a bookmark to that slot |
| 0 (tap) | Jump to a percentage |
| 0 (hold) | Toggle reading direction (RTL / LTR) |
| Back | Close search, or prompt to exit the reader |

### Org files (differences from plain text)

| Key | Action |
| --- | --- |
| D-pad left / right | Previous / next heading |
| * (tap) | Close / open the current section |
| * (hold) | Close all / open all sections |
| Menu (tap) | Open the Contents list |
| Menu (hold) | Toggle the HUD |

### Editor (while editing)

| Key | Action |
| --- | --- |
| Back | Leave the editor (prompts to save if there are changes) |
| Menu | Save (overwrite the file in place) |
| OK | Insert a newline at the caret |
| Other keys / D-pad | Type via the T9 keypad and move the caret (handled natively) |

## Tech stack

- **Language:** Java (Android)
- **Package / application ID:** `com.sonim.reader`
- **compileSdk:** 33
- **minSdk:** 24
- **targetSdk:** 28
- **Java compatibility:** 1.8
- **Version:** 1.0 (versionCode 1)
- **Dependencies:** `androidx.appcompat:appcompat:1.6.1`; `junit:junit:4.13.2` for tests
- **Build system:** Gradle (Kotlin DSL), wrapper pinned to Gradle 9.4.1 (which needs JDK 17 or newer to run)
- **Permissions:** `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`

The `core` package is framework-free (no Android imports) so the reader logic — encoding detection, folding, search, bookmarks, index mapping — is covered by pure-JVM unit tests. `ui/` is covered only by `KeyCommandRouterTest`, which asserts that each documented key reaches an action — the failure the two fixed bugs were both instances of.

## Project structure

```
app/src/main/java/com/sonim/reader/
  MainActivity.java          Host screen; builds the object graph, renders, forwards keys
  FilePickerActivity.java    Focus/D-pad file browser (.txt / .org) starting at /sdcard
  core/                      Framework-free reading logic (unit-testable)
    TextLoader, TextWriter, StreamOpener, StreamWriter
    EncodingDetector, RtlDetector
    LineProvider, InMemoryLineProvider, IndexMap
    OrgOutline, OrgHeading, OrgLineProvider, FoldingModel
    Bookmarks, SearchService, ReaderSettings
  data/                      Persistence (SharedPreferences)
    ReadingStateStore / PrefsReadingStateStore
    BookmarkRepository / PrefsBookmarkRepository
  ui/                        Android-facing glue
    ReaderController          Owns all reader behaviour
    ReaderView                View interface implemented by MainActivity
    KeyCommandRouter          Physical-key → controller action mapping
    LineAdapter               ListView adapter that reads ReaderSettings live
    ContentStreamOpener, AtomicFileSaver, DocumentSaver
    MainThread / AndroidMainThread
app/src/main/res/            Layouts, themes, drawables, launcher icons
```

The architecture separates concerns cleanly: `MainActivity` is a thin host that implements `ReaderView`, `KeyCommandRouter` is the single translation point from physical keys to actions, and `ReaderController` holds all reader state and logic.

## Building

Requires JDK 17 or newer (Gradle 9.4.1 needs it; the Java 1.8 setting above is the bytecode level, not the JDK) and Android SDK platform 33.

```sh
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Unit tests (the framework-free core package)
./gradlew test
```

On Windows use `gradlew.bat` instead of `./gradlew`.

The built debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Installing

With the Sonim phone connected via USB debugging:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or build and install in one step:

```sh
./gradlew installDebug
```

On first launch the app requests storage permission, then opens the file picker. Browse to a `.txt` or `.org` file and press OK to start reading.

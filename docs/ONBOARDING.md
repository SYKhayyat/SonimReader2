# Onboarding

Getting a build onto a Sonim handset and finding your way around the code.

Two features this README used to list were unreachable on the device, and both
are fixed; if you are looking at an older build, or wondering why night mode
moved off `5`,
[TROUBLESHOOTING has the diagnosis](TROUBLESHOOTING.md#fixed-and-what-to-expect-on-an-old-build).

---

## Contents

- [1. What this is, and what it is built for](#1-what-this-is-and-what-it-is-built-for)
- [2. Set up a build](#2-set-up-a-build)
- [3. Get it onto the phone](#3-get-it-onto-the-phone)
- [4. Drive it](#4-drive-it)
- [5. The architecture](#5-the-architecture)
- [6. Testing, and where the gap is](#6-testing-and-where-the-gap-is)
- [7. Working on the device](#7-working-on-the-device)
- [8. First contributions worth making](#8-first-contributions-worth-making)

---

## 1. What this is, and what it is built for

A keypad-driven plain-text and Org-mode reader for **Sonim rugged phones**,
built and tuned for the **Sonim XP5s** on AOSP Android 8.1 (API 27). It opens
`.txt` and `.org` files from device storage and is operated **entirely with
physical keys** — the manifest declares the touchscreen as not required.

Three constraints follow from the target, and they explain most of the
decisions:

**`targetSdk = 28`, deliberately.** That keeps the legacy file-based
external-storage model the `File`-based picker relies on, and avoids
scoped-storage behaviour the device never had. It is not an oversight and it is
commented as such in `app/build.gradle.kts`.

**Everything is a key.** There is no touch fallback, so a key that does not
arrive is a feature that does not exist. That makes key dispatch the highest-risk
part of this codebase, and it is where both of the bugs that reached a user
came from.

**The whole file is held in memory.** On a device of this class that is what
makes the index map, folding and search straightforward. It is a documented
limitation; there is no streaming path.

## 2. Set up a build

### Prerequisites

| | |
|---|---|
| JDK 17+ | Gradle 9.4.1 is pinned in the wrapper and requires it. The Android compile options target Java 1.8 *bytecode*, which is a separate thing. |
| Android SDK, platform 33 | `compileSdk = 33`. `sdkmanager "platforms;android-33"` |
| `ANDROID_HOME` or `ANDROID_SDK_ROOT` | pointing at the SDK |
| `adb` | for installing and for the on-device debugging in §7 |

No Android Studio required, though it works.

### Build

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                   # the framework-free core package
./gradlew assembleRelease
```

Windows: `gradlew.bat`.

`gradle.properties` enables the configuration cache. If a build fails with a
configuration-cache message — most likely after an AGP bump — retry with
`--no-configuration-cache` before believing the error.

### Dependencies

Two, and that is the whole list:

```kotlin
implementation("androidx.appcompat:appcompat:1.6.1")
testImplementation("junit:junit:4.13.2")
```

Keep it that way. This runs on an eight-year-old budget handset.

## 3. Get it onto the phone

Enable Developer Options (tap the build number seven times), then USB
debugging.

```sh
adb devices                      # must list the phone, not "unauthorized"
./gradlew installDebug           # build and install in one step
```

Or install an APK you already built:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch it requests storage permission, then opens the file picker.

**Grant the permission.** Denying it now gives you a dialog with **Grant** and
**Exit**, or **Settings** and **Exit** once "don't ask again" has been ticked.
On an older build it called `showStatus(...)` and then `finish()` on the next
line, so the message went into a view that vanished in the same frame and the
app simply closed with no reason given.

Browse to a `.txt` or `.org` file and press OK to start reading.

## 4. Drive it

You need a real file on the device. Push one:

```sh
adb push notes.org /sdcard/notes.org
```

### Plain text

| Key | Action |
| --- | --- |
| Volume + / − | Font size (10–40 pt) |
| D-pad up / down | Scroll line by line |
| D-pad left / right | Page up / down (in search: previous / next match) |
| `#` tap | Cycle encoding: UTF-8 → Windows-1255 → ISO-8859-1 |
| `#` hold | Toggle night mode |
| OK tap | Toggle the search box |
| OK hold | Enter the editor (after a confirm prompt) |
| `*` | Toggle the HUD (clock, battery, progress) |
| Menu | Toggle auto-scroll |
| 1–9 tap | Jump to the next bookmark in that slot |
| 1–9 hold | Save a bookmark to that slot |
| `0` tap | Jump to a percentage |
| `0` hold | Toggle reading direction (RTL / LTR) |
| Back | Close search, or prompt to exit |

### Org files

Chosen by file extension. Five keys are remapped:

| Key | Action |
| --- | --- |
| D-pad left / right | Previous / next heading |
| `*` tap | Close / open the current section |
| `*` hold | Close all / open all |
| Menu tap | Open the Contents list |
| Menu hold | Toggle the HUD |

### Editor

| Key | Action |
| --- | --- |
| Back | Leave (prompts to save if changed) |
| Menu | Save — **overwrites the file in place**, atomically |
| OK | Insert a newline at the caret |
| Everything else | T9 typing and D-pad caret movement, handled natively |

### What is remembered

Per file, in `SharedPreferences`: last reading position, chosen encoding, RTL
setting. Plus a list of up to 15 recent files. State is keyed per file, so
moving or renaming a file loses it.

## 5. The architecture

The separation here is genuinely good and worth understanding before you change
anything.

```
app/src/main/java/com/sonim/reader/
  MainActivity.java          Host screen; builds the object graph, renders, forwards keys
  FilePickerActivity.java    Focus/D-pad file browser (.txt / .org) starting at /sdcard

  core/                      Framework-free. No Android imports. Unit-testable.
    TextLoader, TextWriter, StreamOpener, StreamWriter
    EncodingDetector, RtlDetector
    LineProvider, InMemoryLineProvider, IndexMap
    OrgOutline, OrgHeading, OrgLineProvider, FoldingModel
    Bookmarks, SearchService, ReaderSettings

  data/                      Persistence, on SharedPreferences
    ReadingStateStore / PrefsReadingStateStore
    BookmarkRepository / PrefsBookmarkRepository

  ui/                        Android-facing glue
    ReaderController         Owns all reader behaviour and state
    ReaderView               The view interface MainActivity implements
    KeyCommandRouter         The single physical-key -> action translation point
    LineAdapter              ListView adapter, reads ReaderSettings live
    ContentStreamOpener, AtomicFileSaver, DocumentSaver
    MainThread / AndroidMainThread
```

### The three rules it holds to

1. **`core/` imports nothing from Android.** That is what makes encoding
   detection, folding, search, bookmarks and index mapping testable on the JVM
   without an emulator. Do not reach for `android.*` in there; add an interface
   and implement it in `ui/`.
2. **`MainActivity` is a thin host.** It implements `ReaderView`, builds the
   object graph and renders. Behaviour lives in `ReaderController`.
3. **`KeyCommandRouter` is the only place a key becomes an action.** One
   translation point.

### The key-dispatch path, in detail

This is the part to understand, because it is where the OK-key bug came from
and it is not obvious from reading `KeyCommandRouter` alone.

```
hardware key
  -> Activity.dispatchKeyEvent        <- MainActivity overrides this
  -> the focused view hierarchy       <- the ListView is focused, and eats some keys
  -> Activity.onKeyDown / onKeyUp     <- forwards to KeyCommandRouter
```

Two consequences that are not obvious:

**The focused view sees the key first.** `AbsListView` claims
`KEYCODE_DPAD_CENTER` and `KEYCODE_ENTER` for item clicks and consumes them.
The reader's list has no `OnItemClickListener`, so those keys produce nothing
*and* never reach the router. Volume, `*`, `#`, `Menu` and the digits are not
keys `AbsListView` claims, which is why everything else works.

**Tap versus hold is deferred.** `onKeyDown` calls `event.startTracking()` and
returns `true`; the action fires in `onKeyUp` (tap) or `onKeyLongPress` (hold).
That mechanism works — `0` proves it — so a key that does nothing at all is a
dispatch problem, not a tracking one.

One more piece of hard-won knowledge: **this handset's
`soc_matrix_keypad_0.kl` maps the centre key to `key 352 ENTER`, not
`DPAD_CENTER`.** `KeyCommandRouter` correctly handles both everywhere it
handles either. Do not "simplify" that away.

## 6. Testing, and where the gap is

```sh
./gradlew test
```

Seven test classes, all against `core/`: `BookmarksTest`,
`EncodingDetectorTest`, `FoldingModelTest`, `OrgOutlineTest`,
`ReaderModelTest`, `SearchServiceTest`, `TextWriterTest`.

Plus `KeyCommandRouterTest`, which is the only test in `ui/` and was added with
the two key-path fixes. It asserts that each documented key **reaches an
action** — the failure both bugs were instances of, and not the failure a test
of the action itself would catch.

`ui/` had no tests at all before that, and the cost is measurable:

- The night-mode `case` was dead because a guard clause above it returns first.
  The compiler does not warn about an unreachable `case` reached through a
  guard, and no test asked what `5` does. **A router test would have caught it
  the first time it ran.**
- Search and the editor were unreachable because of view focus, which a router
  test would *not* have caught — that one needed the device, and a
  screenshot-byte-size comparison found it.

So one of the two was cheap to catch and nothing was looking. The rest of `ui/`
is still untested.

`KeyCommandRouter` carries a primitive-only overload of each entry point
(`onKeyUp(int, boolean)`, `onImmediateKeyDown(int)`, `onKeyLongPress(int)`,
`onEditKey(int, boolean, int)`) precisely so this is possible: `KeyEvent` cannot
be constructed in a JVM unit test without a mocking framework, which is the
practical reason the class went untested for its whole life. Keep new routing
decisions on that side of the split.

**There is no CI.** `./gradlew test` before you push is the whole safety net.

## 7. Working on the device

The most valuable technique in this project, because "the key does nothing" has
no stack trace.

### Screenshot byte-size

```sh
adb exec-out screencap -p > before.png
# press the key
adb exec-out screencap -p > after.png
ls -l before.png after.png
```

**A byte-identical screenshot means the screen did not change.** That is how
both bugs were confirmed, and it is far more reliable than looking at a
small dark screen and deciding whether something moved.

Always take a control measurement with a key you know works — `0` opens the
percentage dialog. Without it you cannot distinguish "this key is broken" from
"screenshots are not working".

### View hierarchy dump

```sh
adb shell uiautomator dump && adb pull /sdcard/window_dump.xml
```

This is how the missing `EditText` was found: with a document open the whole
hierarchy is a `LinearLayout` and a `ListView`, before and after pressing OK.

### Logs

```sh
adb logcat -s AndroidRuntime:E
```

## 8. First contributions worth making

Roughly in order of value against effort.

1. **Drive it on a handset.** Nothing here has been verified on an XP5s since
   the key-dispatch and night-mode fixes landed; they are covered by unit tests
   and by reading the framework, which is not the same as pressing the key. §7
   is how to check, and it is the single most useful thing anyone can do to this
   repository right now.
2. **Extend `KeyCommandRouterTest` to the rest of the key map.** It currently
   covers the two paths that broke. Every documented binding deserves the same
   assertion, and the primitive overloads make it cheap.
3. **Test the rest of `ui/`.** `ReaderController` is the large untested piece,
   and its four collaborators are all interfaces, so it takes fakes rather than
   an emulator — `KeyCommandRouterTest` has a working set of them to copy.
4. **Add CI.** There is none, so `./gradlew test` before pushing is the entire
   safety net. The `core` and `ui` tests need no SDK beyond a compile.
5. **Delete `app/src/main/output.txt`.** A 140 KB dump of the project's own
   source tree, committed by accident. Not referenced, not packaged.

### Conventions

- Keep `core/` free of Android imports.
- Add a key binding in `KeyCommandRouter` and nowhere else — and put the
  decision in the primitive overload, with a test, not in the `KeyEvent` one.
- A key that needs tap-versus-hold goes in `isDeferred`, which is the single
  definition both key-down and key-up ask. It used to be written out twice.
- Two dependencies. Adding a third needs a reason that survives the target
  device.
- Anything that writes a user's file goes through `AtomicFileSaver`. There is
  no undo and no backup copy.

---

## Where to go next

- [../README.md](../README.md) — features, key tables, project structure.
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) — starting with the two known-broken
  features and their full diagnoses.

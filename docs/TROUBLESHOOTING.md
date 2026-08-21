# Troubleshooting

Two features documented in the README were unreachable on the device until they
were fixed. If you are running an older build, see
[Fixed, and what to expect on an old build](#fixed-and-what-to-expect-on-an-old-build).

## Contents

- [Fixed, and what to expect on an old build](#fixed-and-what-to-expect-on-an-old-build)
- [Keys do nothing](#keys-do-nothing)
- [Files and storage](#files-and-storage)
- [Text and encoding](#text-and-encoding)
- [Editing and saving](#editing-and-saving)
- [Org files](#org-files)
- [Bookmarks and reading state](#bookmarks-and-reading-state)
- [Building](#building)
- [Installing and running on the device](#installing-and-running-on-the-device)

---

## Fixed, and what to expect on an old build

Both were confirmed on a real XP5s, both are fixed, and both are covered by
`KeyCommandRouterTest`. They are recorded here because the symptom on an
unpatched build is "the key does nothing", which is indistinguishable from a
hardware fault until you know.

### Search and the editor could not be opened

**Symptom on an old build.** Pressing OK / Center did nothing at all. No search
box, no editor, no prompt; tap and long-press behaved identically, which is to
say not at all.

**Confirmed by** screenshot byte-size, which changes whenever the screen does:

```
baseline                              33,074 bytes
OK long-press  (KEYCODE_ENTER)        33,074 bytes   <- no change
OK long-press  (KEYCODE_DPAD_CENTER)  33,074 bytes   <- no change
control: 0 tap (percentage dialog)    30,774 bytes   <- changed
```

The control matters. `0` and OK travel the **same deferred path** - key-down
calls `startTracking()` and the action fires on key-up or long-press. `0`
worked. OK did not. So the deferred mechanism was fine and the key itself never
arrived.

Independently: with a document open, `uiautomator dump` reported the whole
hierarchy as `LinearLayout` + `ListView` and **no `EditText` at any point**,
before or after pressing OK.

**Cause.** Android offers a key to the focused view hierarchy **before** the
Activity. The focused view while reading is the `ListView`; `AbsListView` claims
`KEYCODE_DPAD_CENTER` and `KEYCODE_ENTER` for an item click and consumes them.
This list has no `OnItemClickListener`, so the key produced no click *and* never
reached `KeyCommandRouter`. It vanished. Every other key worked because
`AbsListView` does not claim it.

**Not the obvious suspect.** `KeyCommandRouter` handled *both*
`KEYCODE_DPAD_CENTER` and `KEYCODE_ENTER` everywhere it handled either, which is
correct and non-obvious: this handset's `soc_matrix_keypad_0.kl` maps the centre
key to `key 352 ENTER`, not `DPAD_CENTER`. The router had that right. The defect
was one layer up, in who saw the event first.

**Fix.** `MainActivity.dispatchKeyEvent` now hands the centre key to its own key
callbacks before the view hierarchy sees it, via
`event.dispatch(this, decor.getKeyDispatcherState(), this)` - the same call
`Activity` makes as its last step. Passing the dispatcher state is the part that
matters: it is what keeps `startTracking()`, `onKeyLongPress` and `isCanceled()`
working, so tap-versus-hold on this key behaves like tap-versus-hold everywhere
else. Calling `onKeyDown` directly would have delivered the tap and silently
lost the hold, which is half the bug fixed and half of it moved.

It applies only while a document is on screen and the search box is closed -
with the search box open the key belongs to its `EditText`, whose editor-action
listener is how a query is submitted.

### Night mode could not be turned off

**Symptom on an old build.** Pressing `5` did nothing visible; the background
stayed dark. Because night mode defaults to **on**, the practical effect was
that it could never be turned off - the worse direction for a reader used in
daylight.

**Cause.** In `KeyCommandRouter.onKeyDown`:

```java
if (isNumberKey(keyCode)) {          // KEYCODE_0..KEYCODE_9
    event.startTracking();
    return true;                     // <- returns here for '5'
}
...
    case KeyEvent.KEYCODE_5:
        controller.toggleNightMode();  // never runs
```

`isNumberKey` covers `KEYCODE_0` through `KEYCODE_9`, which includes
`KEYCODE_5`, so the `case` below was dead. On key-up, `isSlotKey(5)` was also
true, so `5` called `gotoBookmark(5)` instead.

Confirmed on the device: pressing `5` produced a byte-identical screenshot
(33,074 to 33,074).

**Why nothing caught it.** The compiler does not warn about an unreachable
`case` reached through a guard clause, and there was no test on the router.

**Fix, and the decision inside it.** Moving the call above the guard would not
have been enough, because **`5` was never free**: `1`-`9` are the nine bookmark
slots, so `5` taken for night mode is a slot you can save to and never jump to.
Two rows of the key map claimed one key and one of them had to give.

Night mode is now a **long press of `#`** - the one binding with no other claim
in either mode, since `*` long-press is fold-all in Org files and Menu
long-press is the HUD there. The cost is that `#` now cycles the encoding on
key-**up** rather than key-down, which is what every other tap/hold key here
already does.

If you would rather it were elsewhere, that is a one-line change in
`KeyCommandRouter.onKeyLongPress` plus the `isDeferred` set.

## Keys do nothing

### OK / Center

Fixed - see [above](#search-and-the-editor-could-not-be-opened). If it still
does nothing, you are on an old build.

Note that while the search box is **open**, OK submits the query rather than
closing the box; Back closes it.

### 5 does not toggle night mode

Correct: it is bookmark slot 5. Night mode is a **long press of `#`**.

### # cycles the encoding when I release it, not when I press it

Deliberate, and the consequence of `#` gaining a long press. Every tap/hold key
here acts on key-up.

### A key works in plain text and not in an Org file, or the reverse

Deliberate. Several keys are remapped for `.org` files:

| Key | Plain text | Org |
| --- | --- | --- |
| D-pad left / right | Page up / down | Previous / next heading |
| `*` tap | Toggle HUD | Close / open current section |
| `*` hold | — | Close all / open all |
| Menu tap | Toggle auto-scroll | Open the Contents list |
| Menu hold | — | Toggle the HUD |

Org mode is chosen by file extension. A `.txt` file containing Org markup gets
plain-text bindings.

### D-pad left / right pages instead of moving between search matches

That is correct when the search box is closed. While search is visible, left and
right are previous / next match.

### Nothing responds at all

Check the app has focus and a document is actually open. If the file picker is
showing, the reader bindings are not active — the picker has its own focus/D-pad
navigation.

## Files and storage

### I denied storage permission

You now get a dialog saying what the app needs, with **Grant** and **Exit**. If
the permission has been permanently denied - "don't ask again" - the first
button becomes **Settings** instead, because requesting again in that state
returns denied immediately and would loop for ever.

On an older build this was a `showStatus(...)` followed by `finish()` on the
next line, so the message was written into a view that went away in the same
frame: from outside, the app simply closed with no reason given.

To grant it by hand:

```
Settings > Apps > Sonim Reader > Permissions > Storage
```

### The file picker shows nothing

Three causes:

1. **Permission not granted.** See above.
2. **Wrong extension.** The picker lists `.txt` and `.org` only.
3. **The SD card is not mounted** where the picker starts. It starts at
   `/sdcard`.

### It cannot see files on my SD card

The build deliberately targets **SDK 28** to keep the legacy file-based
external-storage model rather than scoped storage, because the XP5s is Android
8.1 and never had scoped storage.

That works on the target device. On a **newer Android**, the platform may
enforce scoped storage regardless of `targetSdk` and the picker will see much
less. This app is not built for newer Android.

### A very large file is slow or the app runs out of memory

The whole file is held in memory. That is a documented limitation, not a bug —
it is what makes the index map, folding and search straightforward on a device
of this class.

There is no streaming path. A file large enough to be a problem is a file this
app cannot open.

## Text and encoding

### The text is mojibake

Press `#` to cycle the encoding: **UTF-8 → Windows-1255 → ISO-8859-1**.

Detection order is BOM, then strict UTF-8 validation, then Windows-1255 for
Hebrew, then ISO-8859-1 as a fallback. Strict validation means a file that is
*almost* valid UTF-8 falls through to the Hebrew codepage, which is usually
right for this app's corpus and occasionally wrong.

The chosen encoding is remembered per file, so once you fix it you fix it once.

### Hebrew is rendering left-to-right (or English right-to-left)

Hold `0` to toggle reading direction. Direction is auto-detected per file and
remembered.

Note that this is **direction**, not the bidirectional algorithm. A line mixing
scripts will not be reordered.

### The font is too small or too large

Volume up / down, between 10 and 40 pt. Remembered per file.

## Editing and saving

### I cannot get into the editor

Hold OK / Center. If nothing happens at all, you are on a build from before
[the dispatch fix](#search-and-the-editor-could-not-be-opened).

### What happens to my file when I save?

Saving **overwrites the original file in place**, with a confirmation prompt and an unsaved-changes prompt on exit. The
write is atomic (`AtomicFileSaver`), so an interrupted save does not leave a
truncated file.

There is no undo and no backup copy.

## Org files

### Folding does nothing

`*` tap closes or opens the current section; `*` hold closes or opens all. Both
are Org-only — in a plain-text file `*` toggles the HUD instead.

### Headings are not detected

The outline parser reads Org heading syntax (leading asterisks). A file with
`.org` on the end but no headings has an empty outline, and heading navigation
has nowhere to go.

### The Contents list will not open

Menu tap, in an Org file. In plain text, Menu toggles auto-scroll.

## Bookmarks and reading state

### A bookmark key jumped somewhere unexpected

Each of slots 1–9 holds **multiple positions per file**, and tapping jumps to
the *next* one in that slot rather than to a fixed place. That is by design —
it makes a slot a ring of related places rather than one.

Hold the digit to save the current position into that slot.

Note that slot 5 also absorbs the
[night-mode binding that used to be dead here](#night-mode-could-not-be-turned-off).

### My position was not remembered

Per-file state — last position, encoding, RTL setting — lives in
`SharedPreferences`, along with a list of up to 15 recent files. It is lost if
app data is cleared or the app is uninstalled.

State is keyed per file, so moving or renaming a file loses its state.

### The screen keeps turning off

It should not while reading — the app holds the screen on. If it is sleeping,
the reader may not be the foreground Activity.

## Building

### Which JDK?

Gradle **9.4.1** (pinned in `gradle/wrapper/gradle-wrapper.properties`), which
requires **JDK 17 or newer**. The Android compile options target Java 1.8
source and target compatibility — that is the bytecode level, not the JDK you
build with.

`settings.gradle.kts` applies the foojay resolver convention, so Gradle can
provision a toolchain if a build script asks for one. None currently does.

### `compileSdk 33` is not installed

Install it through the Android SDK manager, or from the command line:

```sh
sdkmanager "platforms;android-33" "build-tools;33.0.2"
```

`ANDROID_HOME` (or `ANDROID_SDK_ROOT`) must point at your SDK.

### Configuration cache errors

`gradle.properties` sets `org.gradle.configuration-cache=true`. It speeds up
repeat builds and it is the first thing to disable when a build fails with a
message about configuration-cache incompatibility, particularly after an
Android Gradle Plugin upgrade:

```sh
./gradlew assembleDebug --no-configuration-cache
```

### Build commands

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease
./gradlew test                   # the framework-free core package
./gradlew installDebug           # build and install in one step
```

On Windows use `gradlew.bat`.

### `./gradlew test` runs very few tests

Correct. Unit tests cover the `core` package only — `Bookmarks`,
`EncodingDetector`, `FoldingModel`, `OrgOutline`, `SearchService`,
`TextWriter`, and the reader model. That package is framework-free (no Android
imports) specifically so it can be tested on the JVM.

`ui/` has one test class, `KeyCommandRouterTest`, added with the two fixes
above. It asserts that each documented key **reaches an action** - the failure
both bugs were instances of, and not the failure a test of the action itself
would catch.

`KeyCommandRouter` exposes primitive-only overloads (`onKeyUp(int, boolean)` and
friends) so it runs on the JVM with no emulator and no mocking framework:
`KeyEvent` cannot be constructed in a unit test, which is the practical reason
the class had no test and therefore kept an unreachable binding for its whole
life.

The rest of `ui/` is still untested.

### There is no CI

Nothing runs the suite automatically. `./gradlew test` before you push is the
whole safety net.

### Why does `KeyCommandRouter` have two of every method?

The public ones take a `KeyEvent`; the package-private ones take primitives and
hold the routing. That split is what makes the key map testable at all.

### What is `app/src/main/output.txt`?

A 140 KB dump of the project's own source tree, committed by accident. It is
not referenced by anything and is not packaged into the APK — files directly
under `src/main/` that are not in `res/` or `assets/` are not included.

Harmless, and safe to delete.

## Installing and running on the device

### `adb install` fails

```sh
adb devices          # the phone must appear, and not as "unauthorized"
```

If it is `unauthorized`, accept the USB-debugging prompt on the phone. USB
debugging is under Developer Options, which is unlocked by tapping the build
number seven times.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` reinstalls over an existing copy. If it still refuses, uninstall first —
signature mismatches between a debug and a release build cannot be reinstalled
over each other.

### `INSTALL_FAILED_OLDER_SDK`

The device is below `minSdk` 24 (Android 7.0). The XP5s is 8.1 / API 27, so
this means you are installing to something else.

### It installs but does not launch

Check logcat:

```sh
adb logcat -s AndroidRuntime:E
```

### How do I take a screenshot for a bug report?

```sh
adb exec-out screencap -p > shot.png
```

Byte-size comparison of two screenshots is a genuinely useful test on this
device — it is how both bugs were confirmed. A key that changes nothing
produces a byte-identical image.

```sh
adb shell uiautomator dump && adb pull /sdcard/window_dump.xml
```

That dumps the view hierarchy, which is how the missing `EditText` was found.

---

## Reporting something not on this page

Include the device and Android version, the exact key and whether you tapped or
held it, whether the file is `.txt` or `.org`, and — if the complaint is that a
key does nothing — two screenshots with their byte sizes.

# Troubleshooting

Start with [Known broken](#known-broken). Two documented features do not work,
and the symptom in both cases is "the key does nothing" — which is
indistinguishable from a device problem until you know.

---

## Contents

- [Known broken](#known-broken)
- [Keys do nothing](#keys-do-nothing)
- [Files and storage](#files-and-storage)
- [Text and encoding](#text-and-encoding)
- [Editing and saving](#editing-and-saving)
- [Org files](#org-files)
- [Bookmarks and reading state](#bookmarks-and-reading-state)
- [Building](#building)
- [Installing and running on the device](#installing-and-running-on-the-device)

---

## Known broken

Both were confirmed on a real XP5s and both are still present. Each is
documented here with its mechanism and its fix, so nobody spends an evening
re-deriving them.

### Search and the editor cannot be reached

**Symptom.** Pressing OK / Center does nothing at all. No search box, no
editor, no prompt. Tap and long-press behave identically — that is, not at all.

**Confirmed by** screenshot byte-size, which changes whenever the screen
changes:

```
baseline                              33,074 bytes
OK long-press  (KEYCODE_ENTER)        33,074 bytes   <- no change
OK long-press  (KEYCODE_DPAD_CENTER)  33,074 bytes   <- no change
control: 0 tap (percentage dialog)    30,774 bytes   <- changed
```

The control matters. `0` and OK travel the **same deferred path** —
`onKeyDown` calls `event.startTracking()` and the action fires on key-up or
long-press. `0` works. OK does not. So the deferred mechanism is fine and the
key itself never arrives.

Independently: with a document open, `uiautomator dump` reports the whole
hierarchy as `LinearLayout` + `ListView` and **no `EditText` at any point**,
before or after pressing OK.

**Mechanism.** `MainActivity` gives the list focus deliberately:

```java
public void setSearchVisible(boolean visible) {
    ...
    } else { listView.requestFocus(); }     // MainActivity.java:285
}
public void hideEditor() { ...; listView.requestFocus(); }   // :376
```

Android dispatches a key to the **focused view hierarchy before**
`Activity.onKeyDown` / `onKeyUp`. `AbsListView` handles `KEYCODE_DPAD_CENTER`
and `KEYCODE_ENTER` to perform an item click, and consumes them. The reader's
list has no `OnItemClickListener`, so the key produces no item click *and*
never reaches `KeyCommandRouter`. It vanishes.

Every other key works because volume, `*`, `#`, `Menu` and the digits are not
keys `AbsListView` claims, so they fall through to the Activity.

**Not the obvious suspect.** `KeyCommandRouter` handles **both**
`KEYCODE_DPAD_CENTER` and `KEYCODE_ENTER` everywhere it handles either — in
`onKeyDown`, `onKeyLongPress`, `onKeyUp` and `onEditKey`. That is correct and
non-obvious: this handset's `soc_matrix_keypad_0.kl` maps the centre key to
`key 352 ENTER`, not `DPAD_CENTER`. The router got that right. The defect is
one layer up, in who sees the event first.

**The fix is already half-written.** `MainActivity.dispatchKeyEvent` is
overridden and runs *before* the view hierarchy:

```java
public boolean dispatchKeyEvent(KeyEvent event) {
    if (controller.isEditing() && router.onEditKey(event)) return true;
    return super.dispatchKeyEvent(event);
}
```

Two candidate fixes, both a handful of lines:

1. Extend that guard to intercept the centre key while a document is shown,
   before `super.dispatchKeyEvent` hands it to the list.
2. Or `listView.setFocusable(false)` and drive scrolling from the router —
   which it already does for paging.

Either closes it. Neither has been applied or tested on hardware.

**Workaround until then:** none. Search and the editor are unreachable.

### Night mode cannot be turned off

**Symptom.** Pressing `5` does nothing visible. The background stays dark.

Because night mode **defaults to on**, the practical effect is that it can
never be turned off — the worse direction for a reader used in daylight.

**Mechanism.** `KeyCommandRouter.onKeyDown`:

```java
public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (isNumberKey(keyCode)) {          // KEYCODE_0..KEYCODE_9
        event.startTracking();
        return true;                     // <- returns here for '5'
    }
    ...
    switch (keyCode) {
        ...
        case KeyEvent.KEYCODE_5:
            controller.toggleNightMode();  // never runs
            return true;
```

`isNumberKey` covers `KEYCODE_0` through `KEYCODE_9`, which includes
`KEYCODE_5`, so the `case` below is dead. On key-up, `isSlotKey(5)` is also
true (`KEYCODE_1..KEYCODE_9`), so `5` calls `controller.gotoBookmark(5)`
instead.

Confirmed on the device: pressing `5` produced a byte-identical screenshot
(33,074 to 33,074).

**Why nothing caught it.** The compiler does not warn about an unreachable
`case` reached through a guard clause, and there is no test on
`KeyCommandRouter` — the unit tests cover the framework-free `core` package
only.

**The fix** is to give night mode a key that is not also a bookmark slot, or to
special-case `5` before the `isNumberKey` guard and accept that slot 5 loses
its bookmark binding. That is a product decision, not a mechanical one, which
is why it is not applied here.

**Workaround:** none from the keypad.

## Keys do nothing

### OK / Center

See [above](#search-and-the-editor-cannot-be-reached). Known broken.

### 5

See [above](#night-mode-cannot-be-turned-off). Known broken; it jumps to
bookmark slot 5 instead.

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

That is correct when the search box is closed. While search is visible, left
and right are previous / next match. Since search
[cannot currently be opened](#search-and-the-editor-cannot-be-reached), the
match bindings are unreachable in practice.

### Nothing responds at all

Check the app has focus and a document is actually open. If the file picker is
showing, the reader bindings are not active — the picker has its own focus/D-pad
navigation.

## Files and storage

### The app closed immediately after I denied storage permission

Known and abrupt. On denial the app calls `showStatus("Storage permission
required")` and then `finish()` — so the message is posted and the Activity
ends before anyone can read it. From the user's side it is an app that exits
silently.

To recover, grant the permission in Android settings and relaunch:

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

[Known broken.](#search-and-the-editor-cannot-be-reached)

### If the editor could be reached, what would happen to my file?

Worth knowing before the fix lands. Saving **overwrites the original file in
place**, with a confirmation prompt and an unsaved-changes prompt on exit. The
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
[dead night-mode binding](#night-mode-cannot-be-turned-off).

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

**`ui/` is untested**, including `KeyCommandRouter`. Both bugs in
[Known broken](#known-broken) live there, and the absence of a router test is
why the second one survived.

### There is no CI

Nothing runs the suite automatically. `./gradlew test` before you push is the
whole safety net.

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
device — it is how both known bugs were confirmed. A key that changes nothing
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

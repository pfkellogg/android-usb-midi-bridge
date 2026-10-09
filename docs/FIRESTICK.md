# MIDI Bridge For Firestick

The bridge as a Fire TV app: a Fire TV Stick plays the FM-1 from a USB-only
keyboard, no tablet needed. It's the `firetv` build of this project, shown on
the Fire TV as **MIDI Bridge For Firestick**.

```
M-Audio Keystation 49 MK3 ──USB──┐
                                 ├── powered micro-USB OTG hub ── Fire TV Stick
M-VAVE FM-1 ───────────────USB───┘                 │
                                               wall power
```

Tested on a Fire TV Stick 4K (1st gen, `AFTMM`, Fire OS 6.7.1.1 = Android 7.1).
It reports USB host and Android MIDI support, and sees both the Keystation and
the FM-1 ("Jieli Technology USB Composite Device") through the hub.

## Hardware

The stick has one micro-USB port, and it's also the stick's power input. So
the hub must be a **powered micro-USB OTG hub** that powers the stick *and*
the hub from one adapter. The 1-to-3 Y hub cable in the main README won't
work here: nothing would power the stick.

## Build and install

```sh
export JAVA_HOME=/usr/local/opt/openjdk@17      # Homebrew JDK 17
./gradlew assembleFiretvDebug
adb connect <stick-ip>:5555                     # Developer options → ADB debugging ON
adb install -r app/build/outputs/apk/firetv/debug/app-firetv-debug.apk
```

Open it from **Your Apps & Channels** (may be under "See all") or Settings →
Applications → Manage Installed Applications → MIDI Bridge For Firestick →
Launch application.

The FROM / TO devices are picked automatically; check them, then press
**ON**. Uninstalling (or `adb uninstall`) forgets the chosen devices and the
ON state.

## What's different from the tablet build

All of it lives in the `firetv` flavor, so the `oss` and `play` builds are
unchanged on Android 10+:

- **minSdk 25** (`app/build.gradle`). The tablet builds stay at minSdk 29.
- **Android 7.1 fallbacks** in shared code: no notification channel before
  Android 8 (`BridgeService.java`), and `startService` instead of
  `startForegroundService` (`Bridge.java`).
- **TV launcher entry, banner, no touchscreen required**:
  `app/src/firetv/AndroidManifest.xml`, `app/src/firetv/res/drawable/tv_banner.xml`.
- **Remote focus ring**: on TVs, `MainActivity.showRemoteFocus()` draws a cyan
  ring around the control the D-pad is on (the custom button backgrounds hide
  Android's own).
- **Name**: `manifestPlaceholders appLabel` — "MIDI Bridge For Firestick" here,
  "MIDI Bridge" elsewhere. The screen title follows the app name.

## Icon

A Golden Gate badge (`app/src/main/res/drawable/ic_launcher*.xml`): orange
towers and cables over the bay, with a piano-keyboard roadway, in a cyan ring.
Plain vector on Android 7.1, adaptive icon on 8+.

Fire OS limits for sideloaded apps (seen with Zorro too): the home-screen
banner usually isn't shown, and the "Your Apps & Channels" grid may draw the
icon in a single colour. Settings → Applications shows it in full colour.

## Chords

With 3+ different keys held together on the keyboard, the note popup and the
NOTE card show the chord name and the held notes (e.g. **Am7** over
`A3 C4 E4 G4`); otherwise the single note with octave and frequency, as
before.

- `Chord.java` names it from the held pitch classes: major, minor, dim, aug,
  sus2, sus4, 6, m6, 7, maj7, m7, m7b5, dim7, m(maj7), 7sus4, add9, m(add9),
  9, maj9, m9, and sevenths without the fifth. Inversions get a slash bass
  ("C major / E").
- Only keys from the keyboard count (`Bridge.keysDown()`), not other apps'
  notes. **All notes off** also clears them.
- The keys must stay put for `CHORD_SETTLE_MS` (100 ms) after a key goes down,
  so a legato overlap between two notes never counts. Releasing keys doesn't
  rename the chord.
- 2-note shapes (power chords) are deliberately not named.

## Testing without the keys (debug builds)

`app/src/debug/.../TestPlayer.java` plays notes down the keyboard's path, so
the FM-1 sounds and the display reacts as if they were played:

```sh
adb shell am broadcast -n com.fm1.midibridge/.TestPlayer -a com.fm1.midibridge.TEST_PLAY \
    --es play "'60,64,67;57,60,64,67;57,60,65;55,59,62,65;72'" --ei ms 2500
```

Groups are separated by `;` (one note = a single key, several = a chord),
each held `ms` milliseconds. It logs each group under the `MidiBridge` tag.
Release builds don't include it.

## Known issues

- **Note/chord display doesn't update during TestPlayer runs.** On the stick
  (2026-10-09) the log showed the notes forwarded to the FM-1 (message count
  rising, `forwarding=true`), but the screen didn't change: no popup, and
  **All notes off** stayed dim. Not yet known whether it also happens with
  real keys or whether the screenshots were stale. Next step: play the real
  keyboard and watch the screen.
- Chords haven't been checked with real keys on the stick yet.

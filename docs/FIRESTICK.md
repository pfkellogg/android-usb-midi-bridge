# MIDI Bridge For Firestick

The bridge as a Fire TV app: a Fire TV Stick plays the FM-1 from a USB-only
keyboard, no tablet needed. It's the `firetv` build of this project, shown on
the Fire TV as **MIDI Bridge For Firestick**, with its own app ID
`com.fm1.midibridge.firetv` (v1.23-firetv).

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
**ON**. Uninstalling (or `adb uninstall com.fm1.midibridge.firetv`) forgets
the chosen devices and the ON state.

### If it stops bridging

If the status stays on "Connecting…", or it's ON but the keys don't play,
**unplug the keyboard's USB cable, wait a few seconds, and plug it back in**.
Android's MIDI service on Fire OS can be left holding a stale port (seen after
the app was killed or reinstalled while connected: `dumpsys midi` showed the
FM-1's port marked open with no app connected, and both devices on the same
ALSA card). Replugging resets it; a power cycle of the stick and hub does too.

## What's different from the tablet build

All of it lives in the `firetv` flavor, so the `oss` and `play` builds are
unchanged on Android 10+:

- **minSdk 25** (`app/build.gradle`). The tablet builds stay at minSdk 29.
- **Android 7.1 fallbacks** in shared code: no notification channel before
  Android 8 (`BridgeService.java`), and `startService` instead of
  `startForegroundService` (`Bridge.java`).
- **Own app ID** `com.fm1.midibridge.firetv` (`applicationIdSuffix`), so it
  installs next to the tablet app, and the Fire TV Appstore made a fresh tile
  picture for it (see Icon).
- **TV launcher entry, banner, no touchscreen required**:
  `app/src/firetv/AndroidManifest.xml`, `app/src/firetv/res/drawable-xhdpi/tv_banner.png`.
- **Remote focus ring**: on TVs, `MainActivity.showRemoteFocus()` draws a cyan
  ring around the control the D-pad is on (the custom button backgrounds hide
  Android's own).
- **Ports open off the main thread** (shared code, `Bridge.finishConnect()`):
  opening a MIDI port is a blocking call, and on Fire OS a stuck port froze
  the app on a black screen (ANR). Now the screen comes up and shows
  "Connecting…" instead.
- **Name**: `manifestPlaceholders appLabel` — "MIDI Bridge For Firestick" here,
  "MIDI Bridge" elsewhere. The screen title follows the app name.

## Icon

The Golden Gate's two towers and main cable over a piano-keyboard roadway, on a
navy square.

- Fire TV (Android 7.1) uses the **PNG** icons in `app/src/main/res/mipmap-*/`
  and the PNG banner `app/src/firetv/res/drawable-xhdpi/tv_banner.png`. The
  firetv build has no adaptive icon (`mipmap-anydpi-v26` is only in `src/oss`
  and `src/play`), so the PNGs are all it declares, like Zorro.
- Android 8+ (oss / play) uses the adaptive icon `mipmap-anydpi-v26/ic_launcher.xml`
  with the vector foreground `drawable/ic_launcher_foreground.xml`.
- **How the Fire TV "Your Apps & Channels" grid gets a sideloaded app's
  picture** (read from the Appstore app, `com.amazon.venezia`): when it first
  records a sideloaded package it saves a tile image made from the app's icon
  (`SideloadedAppManagerService.insertSingleSideloadedAppImage`). Updates never
  remake it. With no saved image, the grid loads `file:` + the icon's
  `android.resource://` address, which fails, and shows its grey "apps"
  placeholder (logcat: `Glide: Load failed for file:android.resource://…`).
  - The first `com.fm1.midibridge` record dated from the system play-button
    icon and kept failing across uninstalls and version bumps. Giving the
    Fire TV build its own app ID got a fresh, correct tile.
  - So: get the icon right **before** first installing a new package on a
    stick. After that, changing it may need a new app ID.
- Fire OS limits for sideloaded apps (seen with Zorro too): the home-screen
  banner usually isn't shown.

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
adb shell am broadcast -n com.fm1.midibridge.firetv/com.fm1.midibridge.TestPlayer -a com.fm1.midibridge.TEST_PLAY \
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
- The 4th Fire TV (.100) still has the older `com.fm1.midibridge` build.

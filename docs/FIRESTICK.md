# MIDI Bridge For Firestick

The bridge as a Fire TV app: a Fire TV Stick plays the FM-1 from a USB-only
keyboard, no tablet needed. It's the `firetv` build of this project, shown on
the Fire TV as **MIDI Bridge For Firestick**, with its own app ID
`com.fm1.midibridge.firetv` (v1.23-firetv).

```
M-Audio Keystation 49 MK3 ──USB──┐
                                 ├── USB hub ── powered OTG adapter ── Fire TV Stick
M-VAVE FM-1 ───────────────USB───┘              │      (micro-USB)
                                           wall power
```

Tested on two Fire TV Stick 4K (1st gen, `AFTMM`, Fire OS 6.7.1.1 = Android
7.1) sticks, the 4th (.100) and 5th (.104) Fire TVs. They report USB host and
Android MIDI support, and see both the Keystation and the FM-1 ("Jieli
Technology USB Composite Device") through the hub.

## Why an app

The Keystation 49 MK3 has **no 5-pin DIN MIDI OUT**, only USB. A keyboard with
a DIN MIDI OUT (like the Arturia MiniLab) plays the FM-1 with just a MIDI
cable. Over USB, though, the keyboard and the FM-1 are both USB *devices*, and
two devices can't talk without a USB *host* between them. The Fire TV Stick is
that host, and this app reads the keys and sends them on to the FM-1. More in
the main README, "Why an app?".

## Hardware

The stick has one micro-USB port, and it's also the stick's power input. A
**powered micro-USB OTG adapter** solves both: its micro-USB plug goes into
the stick, it takes the stick's power adapter, and it gives a USB-A port for
the hub. The one used here is a "3 Pack OTG Cable Adapter for Fire TV Stick
4K Max/4K Select/4K Plus/Cube" ([Amazon](https://www.amazon.com/dp/B0BC9Q7ZQK)).

Into the adapter's USB-A port, either hub has worked (4th Fire TV, .100,
2026-10-09):

- a USB hub (also used on .104), or
- the **1-to-3 USB "Y" hub cable** from the main README
  ([Amazon](https://www.amazon.com/dp/B0GH45DKS7)), with the keyboard and the
  FM-1 on two of its sockets.

The stick, the keyboard and the FM-1 all run off the stick's power adapter
through the OTG adapter. If the stick reboots or the devices drop out, use a
hub with its own power supply.

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
**ON**. The first time, Fire OS asks to let the app use each USB device (the
Keystation, then the FM-1): choose **Allow Always**, so it isn't asked again
after a restart. Uninstalling (or `adb uninstall com.fm1.midibridge.firetv`)
forgets the chosen devices, the ON state and those permissions.

After that it just works: **restart the stick with everything plugged in and
the bridge comes back by itself**, without opening the app (see "Direct USB"
and "Start on boot" below). Tested 2026-10-09.

### If it stops bridging

Open the app and read the status card. If it asks to allow a USB device,
choose **Allow Always**. If it's stuck on "Connecting…", unplug the keyboard's
USB cable for a few seconds and plug it back in (that re-registers it with
Fire OS).

### Other apps that open at startup

A Fire TV app can start itself at every boot, and Fire OS doesn't let adb turn
that off for another app. IPTV Smarters Pro does this; its own switch is
**Settings → General Settings → "AutoStart on Bootup"** (its boot receiver
checks the `auto_start` preference, default on).

### "Allow USB debugging?" after a restart

That's adb asking for a computer. Tick **Always allow from this computer**,
then OK, and that computer isn't asked again.

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
- **Direct USB MIDI** (`UsbMidi.java`, `Bridge.connectUsb()`): see below.
- **Start on boot** (`app/src/firetv/.../BootReceiver.java`): after a restart,
  if the bridge was ON it turns itself back on (starts `BridgeService`), so the
  keyboard plays without opening the app.
- **Ports open off the main thread** (shared code, `Bridge.finishConnect()`):
  opening a MIDI port is a blocking call, and on Fire OS a stuck port froze
  the app on a black screen (ANR). Now the screen comes up and shows
  "Connecting…" instead.
- **Name**: `manifestPlaceholders appLabel` — "MIDI Bridge For Firestick" here,
  "MIDI Bridge" elsewhere. The screen title follows the app name.

## Direct USB

**The problem:** when the stick boots with the Keystation and the FM-1 both
already plugged in, Fire OS registers them on the **same ALSA sound card**
(`dumpsys midi` shows `alsa_card=1` for both). Android's MIDI service then
can't open the keyboard's port: the call hangs, nothing plays, and only
unplugging and replugging the keyboard (so it re-registers with its own card)
fixes it. No app can renumber the sound cards.

**The fix:** on Android 7.x (`Bridge.directUsb()`, i.e. only the Fire TV
build), the app drives USB MIDI devices itself with Android's USB host API and
never touches ALSA or the MIDI service's ports:

- `UsbMidi.open()` finds the device's MIDI-streaming interface (USB audio
  class 1, subclass 3), opens the device and claims that interface (taking it
  from the kernel driver).
- A reader thread pulls 4-byte USB MIDI event packets off the bulk IN
  endpoint, keeps the chosen port's (cable's) messages, and hands them to the
  same `Forwarder` as before (channel-1 remap, note display, chords).
- `UsbMidi.sender()` turns a MIDI byte stream (running status and SysEx
  allowed) back into USB MIDI packets for the bulk OUT endpoint, so the FM-1,
  **All notes off** and other apps' notes (the virtual "MIDI Bridge" input)
  all go the same way.
- The MIDI service is still used to **list and name** the devices, so the
  FROM / TO lists, saved choices and status read as before.
- Each device needs a USB permission, asked once (`UsbManager.requestPermission`);
  "Allow Always" saves it for the package.
- Unplugging a MIDI device (`ACTION_USB_DEVICE_DETACHED`) drops the link;
  plugging it back reconnects.

The phone/tablet builds (Android 10+) keep using Android's MIDI service.

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

- **Note/chord display during TestPlayer runs** didn't update in one test
  (2026-10-09) although the notes reached the FM-1. With the real keyboard the
  note popup works (seen: B3, 246.9 Hz).
- Chords haven't been checked with real keys on the stick yet.
- Installed and tested with the keyboard and FM-1 on both sticks (4th Fire
  TV .100, 5th .104).

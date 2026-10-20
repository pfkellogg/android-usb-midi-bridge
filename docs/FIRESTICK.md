# AIKellogg MIDI Bridge: Notes, Chords & Staff — for Fire TV Stick 4K (Android Fire OS, not Vega OS)

The bridge as a Fire TV app: a Fire TV Stick plays the FM-1 from a USB-only
keyboard, no tablet needed. It's the `firetv` build of this project, shown on
the Fire TV as **AIKellogg MIDI Bridge: Notes, Chords & Staff — for Fire TV Stick 4K (Android Fire OS, not Vega OS)**, with its own app ID
`com.fm1.midibridge.firetv` (v1.37-firetv).

▶ **Videos** (AIKellogg channel, Learn Piano series): [Part 1: MIDI Bridge for Fire TV](https://youtu.be/Psc5Abezvuk) · [Part 2: Shuttle Looper](https://youtu.be/ui-dkXiveTU), which loops a section of the lesson using the hub's third port.

```
M-Audio Keystation 49 MK3 ──USB──┐
                                 ├── USB hub ── powered OTG adapter ── Fire TV Stick
M-VAVE FM-1 ───────────────USB───┤              │      (micro-USB)
ShuttleXpress (optional) ──USB───┘          wall power
```

Tested on two Fire TV Stick 4K (1st gen, `AFTMM`, Fire OS 6.7.1.1 = Android
7.1) sticks. They report USB host and
Android MIDI support, and see both the Keystation and the FM-1 ("Jieli
Technology USB Composite Device") through the hub.

**Which Fire TV Stick:** the Android-based ones (Fire OS), like the Fire TV
Stick 4K (1st gen, tested here) and other Android-based models. It does **not** run on Amazon's newer Vega
OS sticks (e.g. the Fire TV Stick 4K Select), which aren't Android and can't
install Android apps.

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

Into the adapter's USB-A port, either hub has worked:

- a USB hub, or
- the **1-to-3 USB "Y" hub cable** from the main README
  ([Amazon](https://www.amazon.com/dp/B0GH45DKS7)), with the keyboard and the
  FM-1 on two of its sockets. The third is free for a ShuttleXpress (below).

The stick, the keyboard and the FM-1 all run off the stick's power adapter
through the OTG adapter. If the stick reboots or the devices drop out, use a
hub with its own power supply.

**Noise ("motorboating") from the FM-1's audio out** when it's also on the
stick's USB and its output goes through a mixer to a stereo: that's a ground
loop (the FM-1 is grounded both through USB and through the audio cable).
A 3.5 mm ground loop isolator between the FM-1's output and the mixer
usually stops it; plugging the stick's power and the mixer / stereo into the
same power strip can help too. Unplugging the FM-1's USB cable for a moment
confirms it (the noise stops).

## The hub's third port: Shuttle Looper

With the keyboard and the FM-1 on two of the hub cable's sockets, the third
can take a **Contour ShuttleXpress** ([Amazon](https://www.amazon.com/dp/B0032Y0OH0))
for [**Shuttle Looper**](https://github.com/pfkellogg/shuttle-looper-simple):
mark IN and OUT on the lesson (Udemy in Silk, YouTube, VLC…), loop the hard
passage, and save it as a named clip, while MIDI Bridge keeps writing what you
play on the staff. No ShuttleXpress? Press Play/Pause twice quickly for an
on-screen one. Both apps run side by side on the same stick.

## Build and install

```sh
export JAVA_HOME=/usr/local/opt/openjdk@17      # Homebrew JDK 17
./gradlew assembleFiretvDebug
adb connect <stick-ip>:5555                     # Developer options → ADB debugging ON
adb install -r app/build/outputs/apk/firetv/debug/app-firetv-debug.apk
```

Open it from **Your Apps & Channels** (may be under "See all") or Settings →
Applications → Manage Installed Applications → AIKellogg MIDI Bridge: Notes, Chords & Staff — for Fire TV Stick 4K (Android Fire OS, not Vega OS) →
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
- **Name**: `manifestPlaceholders appLabel` — "AIKellogg MIDI Bridge: Notes, Chords & Staff — for Fire TV Stick 4K (Android Fire OS, not Vega OS)" here,
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

### No MIDI service: direct USB for everything

Some Android TV devices have USB host but **no MIDI service at all** (`dumpsys
midi` says "Can't find service: midi"). There, `Bridge.directUsb()` is on too,
and the devices are also *listed* over USB (`Bridge.refreshUsbLists()`): every
USB MIDI device plugged in, one port each (cable 0), named "manufacturer +
product" like the MIDI service names them, so the FROM / TO hints and saved
choices still match.

## Onn Google TV: doesn't work (USB controller dies)

The firetv build installs and runs on the **onn. Full HD Streaming Device**
(Google TV, Android 14, `onn_2k_gtv`), which is why the no-MIDI-service mode
above exists. But it can't bridge there: **the Onn's USB host controller
crashes** and takes every USB device with it. Tested 2026-10-11 with the
Keystation on a generic USB 2.1 hub (Realtek `0bda:5411`) that also powers the
Onn:

- **Hub plugged in at boot:** the kernel finds the hub and the Keystation (and
  the FM-1, if it's plugged in), then about 30 seconds later, when Android first
  reads the device, the controller is gone: `xhci-hcd-meson: xHCI host
  controller not responding, assume dead … HC died`. Everything disconnects
  and USB stays dead until a reboot. Same with the keyboard alone, five boots
  in a row, so it isn't the FM-1. MIDI Bridge hasn't even started yet.
- **Hub plugged in after boot:** the Onn never sees it (nothing in the kernel
  log). The Onn has no battery and one USB-C port, so unplugging the hub's
  cable entirely cuts its power and reboots it.

Nothing on the app side can get past this: the Onn exposes no USB port to
Android (`dumpsys usb set-port-roles` → "No such USB port"), and turning off
USB power saving or re-authorizing the bus (`/sys/bus/usb/devices/usb1/power/control`,
`…/authorized`) is "Permission denied" even for adb, let alone an app. A
different power-passthrough OTG hub might behave differently; otherwise use a
Fire TV Stick.

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

## Notes over other apps

With another app in front (e.g. a Udemy piano course in Silk), a small badge
in the top-right corner shows the note (or chord) you play, written on a
grand staff too (see "Staff" below), and fades out
about a second after you stop (`NoteOverlay.java`, run by `BridgeService`).
It never takes the remote's focus. It stays hidden while MIDI Bridge's own
screen is open.

It needs "draw over other apps", which Fire OS has no settings screen for.
Grant it once with adb (v1.24, 2026-10-19):

```sh
adb shell appops set com.fm1.midibridge.firetv SYSTEM_ALERT_WINDOW allow
```

then open MIDI Bridge once so the bridge picks it up.
`… SYSTEM_ALERT_WINDOW deny` turns the badge off again.

**After every restart of the stick, open MIDI Bridge once** (then Back).
Fire OS only shows an overlay window that was added while its app was in
front; one added from the background (the bridge starting by itself at
boot, or when a note comes) stays invisible however it's set. So the window
is added when MIDI Bridge's screen comes up (`MainActivity.onResume` ->
`NoteOverlay.attachFromForeground()`), stays added, and the box only fades
in and out.

### Moving and resizing the box

On MIDI Bridge's screen (with the bridge ON), **Move / resize the note box
over other apps** shows the box with the last note (or A4) and a help box at
the bottom-left: the remote's **arrows** move it, **rewind / fast-forward**
make it smaller / bigger (60–200 %), **OK** or **Back** is done. On a touch
screen, drag the box and use the Smaller / Bigger buttons. Position and size
are remembered (`shared_prefs/overlay.xml`; `NoteOverlay.moveBy/scaleBy`).

## No waveform

A waveform under the staff was tried (2026-10-19) and taken out: to be right
it has to show the voice the FM-1 is actually playing, and the app can't know
that. The FM-1 sends nothing back over USB, not when its knob changes voice
and not in answer to a DX7 voice request (F0 43 20 00 F7). Its USB audio
stream can't stand in either: on the Fire TV Stick it arrives broken (about
0.4 s of sound in 7 s, nearly all zeros). A sine per key, or a voice picked
by hand in the app, was wrong whenever the knob moved.

## Staff

The note popup (all Android builds) and the badge over other apps write what
you play on a grand staff too: treble and bass clef, whole notes, ledger
lines and sharps (`StaffView.java`, from v1.25 / tablet v1.23). Middle C and
up go on the treble staff, B3 and down on the bass staff. In a chord, a
second's upper note moves right of the stem line, and sharps that would clash
step further left. The view shrinks to fit when notes go past two ledger
lines.

The clefs, sharp and note head are outlines taken from the **Noto Music**
font (SIL Open Font License), so they look the same on any device whatever
its fonts. `tools/gen_staff_glyphs.py` makes `StaffGlyphs.java` from the font
(and the Mac app's `staff_glyphs.py`); the Mac bridge draws the same staff.

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

- **The box over other apps doesn't show in screenshots** (`adb exec-out
  screencap`) while Silk plays a video, and Fire OS hides it while the stick
  is idle (screensaver / home-screen ambient mode). Look at the TV instead.
- **TestPlayer notes don't open MIDI Bridge's own popup** (real keys do); the
  box over other apps does show them. Chords were checked that way over a
  Udemy lesson: the "Imagine" progression (C, Cmaj7, F/C, Am, Dm, F, G, E7)
  all named right (2026-10-19).
- Every install or restart asks again to allow USB access for the keyboard
  and the FM-1: choose **Allow Always**.
- Installed and tested with the keyboard and FM-1 on both sticks (4th Fire
  TV, 5th Fire TV).

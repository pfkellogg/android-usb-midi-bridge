# Android USB MIDI Bridge

Play a synth from a **USB-only MIDI keyboard** when the keyboard has no 5-pin DIN MIDI OUT — using an Android tablet or phone as the go-between instead of a computer.

```
USB MIDI keyboard ──USB──┐
                         ├── USB hub ── Android tablet (this app)
USB MIDI interface ──USB─┘
        │
        └── DIN / TRS MIDI OUT ──> synth
```

<img src="docs/screenshot.png" alt="MIDI Bridge switched ON: FROM and TO device pickers, a green ON button, All notes off, and a status card" width="320">

*(Emulator screenshot, so the device pickers are empty — on a tablet they show the keyboard and interface, and the status card shows the note being played with its frequency.)*

Built for an M-Audio Keystation 49 MK3 (USB only) playing an M-VAVE FM-1 through an Arturia interface's MIDI OUT, but it works with any class-compliant USB MIDI devices Android can see.

### Why an app? (A MiniLab plays the FM-1 with just a cable)

A keyboard with a **5-pin DIN MIDI OUT** jack, like the Arturia MiniLab, plays the FM-1 with one MIDI cable into the FM-1's MIDI IN, no app needed. DIN MIDI is a direct one-way link: the keyboard sends, the synth listens, and neither side has to be in charge.

**The Keystation 49 MK3 has no 5-pin DIN MIDI OUT, only USB**, and USB doesn't work that way. Every USB connection has one **host** (a computer, phone or tablet) with **devices** plugged into it. The Keystation and the FM-1 are both devices, so plugging one into the other does nothing, and a hub on its own doesn't help either: a hub only connects devices to a host. Something has to be the host, read the keys from the keyboard and send them on to the synth.

That's what this app does: it turns an Android tablet or phone into the host in the middle.

| Keyboard | MIDI out | How it plays the FM-1 |
|---|---|---|
| Arturia MiniLab | 5-pin DIN | MIDI cable straight to the FM-1's MIDI IN |
| M-Audio Keystation 49 MK3 | USB only | This app on an Android tablet or phone, with both on a USB hub |

### Bare-bones setup: synth on USB, no MIDI interface

If the synth has its own USB MIDI port (the FM-1 does), skip the interface and plug the synth into the tablet directly:

```
USB MIDI keyboard ──USB──┐
                         ├── USB hub (a 1-to-3 "Y" hub cable is enough) ── Android tablet
synth (e.g. FM-1)  ──USB─┘
```

**TO** picks the FM-1 automatically. Android names USB devices "manufacturer + product", and the FM-1's USB chip is made by Jieli, so it shows up as "Jieli Technology USB Composite Device" (on a Mac, just "USB Composite Device"). No MIDI cable goes to the FM-1 at all — the notes reach it over its USB cable.

## Hubs and charging

The tablet needs a **USB hub** between it and the two devices — but it can be tiny. A 1-to-3 USB "Y" hub cable works: it looks like a splitter cable but has a hub chip inside. This is the one used here — a USB 2.0, USB-A 1-in/3-out "Y extension hub cable", about $6 ([Amazon](https://www.amazon.com/dp/B0GH45DKS7)):

<img src="docs/usb-y-hub-cable.png" alt="A braided 1-to-3 USB-A Y hub cable: one USB-A plug splitting into three USB-A sockets" width="360">

 (A plain *power-only* Y splitter would not: two devices can't share one cable's data wires without a hub.) On a USB-C tablet, a USB-C-to-USB-A OTG adapter connects a USB-A hub cable like this one.

**Charging while playing depends on the tablet.** Some tablets charge and run USB devices at the same time through a hub with a PD charging input; others stop charging while they're powering USB devices, or won't do both at once at all. Try it with your tablet; if it won't charge, it still runs on battery, powering the keyboard and the FM-1 from the tablet.

**Tip: use a powered hub for long sessions.** A bus-powered hub (like the Y cable above) makes the tablet's battery power the keyboard *and* the synth as well as itself. A hub with its own power adapter powers the keyboard and the FM-1 from the wall instead, so the tablet only runs itself and the app and its battery lasts far longer — even on tablets that can't charge while using USB. Don't try to wire a charger to the tablet's battery directly: that bypasses its charging circuit and is a fire risk.

## Download

Get the APK from [**Releases**](../../releases/latest), open it on the tablet, and allow installing from that source when Android asks. (It's a debug-signed sideload build, not a Play Store app, so Play Protect may warn — choose *Install anyway*.)

## Use

1. Plug the keyboard and the MIDI interface into a USB hub connected to the tablet. A hub with a **PD charging input** lets the tablet charge at the same time.
2. Open **MIDI Bridge**. **FROM** should show the keyboard, **TO** the interface or the FM-1 — both are picked automatically by name and can be changed (tap either one for a big-text list); pick the keyboard's main port, not a "Transport"/"DAW" port. The TO list leaves out the FROM device's own ports, since a keyboard can't play itself.
3. Tap the big button so it reads **ON**. The first time, allow notifications and allow the app to run in the background without battery limits.
4. Play. The screen shows the note and its frequency (e.g. `C4   261.6 Hz`) and every message forwarded.

It keeps running when you switch to another app or the screen turns off — a "MIDI Bridge is on" notification shows while it's running, with a **Stop** button. It remembers your devices and reconnects by itself when they're plugged back in.

**All notes off** sends sustain off + All Notes Off on all 16 channels, to clear stuck notes. It's lit only while something is sounding (a key or the sustain pedal down — including a note stuck because its release got lost) and dimmed otherwise.

**FM-1 as FROM:** the FM-1 never sends MIDI over USB (not even when its knobs are turned), so if it's chosen as FROM there's nothing to forward: TO greys out, and tapping it explains why and offers to pick FROM again.

### Sharing the synth with other apps

While the bridge is ON it holds the synth's USB input, so other apps can't open it directly. Instead, the app adds a virtual MIDI device, **MIDI Bridge** (manufacturer "Android USB MIDI Bridge"): anything another app sends to it is mixed into the stream going to the TO device. A karaoke or MIDI-file player can play the FM-1 while you keep playing the keyboard, without switching the bridge off.

## What it forwards

Everything from the keyboard's chosen port — notes, sustain, pitch bend, mod wheel, program changes, SysEx — unchanged (same MIDI channel). Only real-time bytes (clock, active sensing) are dropped.

## Build

Plain Java, no AndroidX or other dependencies. Needs JDK 17+ and the Android SDK (platform 34):

```
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

## Code

- `Bridge.java` — device lists, connecting, and the forwarding itself (`android.media.midi`)
- `BridgeService.java` — foreground service + wake lock that keep it running in the background
- `BridgeMidiService.java` — the virtual "MIDI Bridge" input other apps can send to
- `MainActivity.java` — the screen, a remote control for the bridge

## Status

v1.4 is confirmed working on an Android 16 tablet, including in the background (another app in front). v1.11 (All notes off only while sounding, TO list without the keyboard's own ports, FM-1-as-FROM explanation) builds clean but hasn't been tried on the tablet yet.

## License

MIT — see [LICENSE](LICENSE).

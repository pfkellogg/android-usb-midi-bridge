package com.fm1.midibridge;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The MIDI forwarding itself: one device's output (e.g. an M-Audio
 * Keystation) to another device's input (e.g. an Arturia interface whose
 * MIDI OUT drives the FM-1). One instance per app process, owned by nobody
 * in particular — BridgeService keeps the process alive while it's ON, and
 * MainActivity is just a remote control for it. All methods run on the main
 * thread except the Forwarder, which runs on a MIDI binder thread.
 *
 * The chosen pair and the on/off state are remembered; while ON, it
 * (re)connects by itself whenever both devices are plugged in.
 */
public final class Bridge {

    /** One port on one device: a "from" (device output) or a "to" (device input). */
    public static final class PortRef {
        final MidiDeviceInfo info;
        final int port;
        final String label;

        PortRef(MidiDeviceInfo info, int port, String label) {
            this.info = info;
            this.port = port;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final String[] FROM_HINTS = {"keystation", "m-audio"};
    // MIDI interfaces first, then the FM-1 itself on USB. Its USB chip is made by Jieli, and
    // Android names it "<manufacturer> <product>" = "Jieli Technology USB Composite Device".
    private static final String[] TO_HINTS = {"arturia", "minifuse", "audiofuse", "fm-1", "fm1", "m-vave", "cuvave", "jieli", "composite"};
    // Devices that only receive: the FM-1 sends no MIDI over USB, even when its knobs are turned.
    // "USB Composite Device" alone is a stock chip name, so only count it with Jieli's name too.
    private static final String[] SILENT_HINTS = {"fm-1", "fm1"};
    public static final String SILENT_FROM = "The FM-1 is all ears, no mouth — it plays the MIDI it gets, "
            + "but never sends any. Choose your keyboard as FROM.";
    private static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    private static Bridge instance;

    public static Bridge get(Context ctx) {
        if (instance == null) instance = new Bridge(ctx.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final MidiManager midi;
    private final SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new ArrayList<>();

    private final List<PortRef> sources = new ArrayList<>();
    private final List<PortRef> dests = new ArrayList<>();

    private boolean on;
    private boolean connecting;
    private MidiDevice fromDev, toDev;
    private MidiOutputPort fromPort;
    private volatile MidiInputPort toPort;
    private String connectedFrom, connectedTo;
    private String error;

    private volatile long msgCount = 0;
    private volatile String lastText = "";
    private volatile String lastNote = "—";

    // What's sounding on the TO device: held notes per channel, and sustain.
    // Fed from both the keyboard and other apps, so it covers everything we send.
    private final int[][] held = new int[16][128];
    private final boolean[] sustain = new boolean[16];
    private int heldCount;
    private final Tracker keysTracker = new Tracker(), appTracker = new Tracker();

    private Bridge(Context ctx) {
        this.ctx = ctx;
        midi = ctx.getSystemService(MidiManager.class);
        prefs = ctx.getSharedPreferences("bridge", Context.MODE_PRIVATE);
        on = prefs.getBoolean("on", false);
        if (midi != null) {
            midi.registerDeviceCallback(deviceCallback, ui);
            refreshLists();
            tryConnect();  // e.g. Android restarted BridgeService after killing the app
        }
    }

    // ------------------------------------------------------------------
    // Public API (main thread)
    // ------------------------------------------------------------------

    public boolean hasMidi() {
        return midi != null;
    }

    public boolean isOn() {
        return on;
    }

    /** Switches the bridge on/off, and starts/stops the foreground service that keeps it running in the background. */
    public void setOn(boolean want) {
        on = want;
        prefs.edit().putBoolean("on", want).apply();
        Intent svc = new Intent(ctx, BridgeService.class);
        if (want) {
            ctx.startForegroundService(svc);
            tryConnect();
        } else {
            disconnect(true);
            ctx.stopService(svc);
        }
        changed();
    }

    public List<PortRef> sources() {
        return sources;
    }

    /** Places to send to — minus the FROM device's own ports (the keyboard can't play itself). */
    public List<PortRef> dests() {
        PortRef from = from();
        if (from == null) return dests;
        List<PortRef> out = new ArrayList<>();
        for (PortRef r : dests) if (r.info.getId() != from.info.getId()) out.add(r);
        return out;
    }

    public PortRef from() {
        return pick(sources, prefs.getString("from", null), FROM_HINTS, null);
    }

    public PortRef to() {
        return pick(dests, prefs.getString("to", null), TO_HINTS, from());
    }

    public void choose(PortRef from, PortRef to) {
        String oldFrom = from() != null ? from().label : null;
        String oldTo = to() != null ? to().label : null;
        SharedPreferences.Editor e = prefs.edit();
        if (from != null) e.putString("from", from.label);
        if (to != null) e.putString("to", to.label);
        e.apply();
        boolean same = from != null && from.label.equals(oldFrom) && to != null && to.label.equals(oldTo);
        if (on && !same) {
            disconnect(false);
            tryConnect();
        }
        changed();
    }

    /** FROM is a device that never sends MIDI (the FM-1), so there's nothing to forward. */
    public boolean fromIsSilent() {
        PortRef from = from();
        if (from == null) return false;
        String l = from.label.toLowerCase(Locale.US);
        for (String h : SILENT_HINTS) if (l.contains(h)) return true;
        return l.contains("jieli") && l.contains("composite");
    }

    public String status() {
        if (midi == null) return "This device has no MIDI support.";
        if (fromIsSilent()) return SILENT_FROM;
        if (toPort != null && fromPort != null) return "Forwarding\n" + connectedFrom + "\n→ " + connectedTo;
        if (error != null) return error;
        if (sources.isEmpty() && dests.isEmpty()) return "No MIDI devices found — check the hub.";
        if (to() == null) return "Only the keyboard is plugged in.\nThe tablet doesn't see the FM-1 — check its USB cable, "
                + "that it's switched on, and the hub's power.";
        if (!on) return "Stopped.";
        if (connecting) return "Connecting…";
        return "Waiting for both devices to be plugged in…";
    }

    /** One line for the notification. */
    public String shortStatus() {
        if (toPort != null && fromPort != null) return connectedFrom + " → " + connectedTo;
        if (error != null) return error;
        return connecting ? "Connecting…" : "Waiting for both devices…";
    }

    public long msgCount() {
        return msgCount;
    }

    public String lastText() {
        return lastText;
    }

    public String lastNote() {
        return lastNote;
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    /**
     * MIDI from another app (via BridgeMidiService), mixed into the stream to
     * the TO device. Dropped while the bridge isn't connected.
     */
    public void sendFromApp(byte[] msg, int offset, int count) throws IOException {
        MidiInputPort out = toPort;
        if (out == null) return;
        out.send(msg, offset, count);
        appTracker.feed(msg, offset, count);
    }

    /** True while a note or the sustain pedal is down on the TO device (or a note got stuck). */
    public synchronized boolean isSounding() {
        if (heldCount > 0) return true;
        for (boolean s : sustain) if (s) return true;
        return false;
    }

    private synchronized void clearSounding() {
        for (int[] ch : held) java.util.Arrays.fill(ch, 0);
        java.util.Arrays.fill(sustain, false);
        heldCount = 0;
    }

    private synchronized void track(int status, int d1, int d2) {
        int ch = status & 0x0F;
        switch (status & 0xF0) {
            case 0x90:
                if (d2 > 0) {
                    held[ch][d1]++;
                    heldCount++;
                    break;
                }
                // velocity 0 = note off
            case 0x80:
                if (held[ch][d1] > 0) {
                    held[ch][d1]--;
                    heldCount--;
                }
                break;
            case 0xB0:
                if (d1 == 64) sustain[ch] = d2 >= 64;
                else if (d1 == 120 || d1 == 123) {  // all sound off / all notes off
                    for (int n = 0; n < 128; n++) heldCount -= held[ch][n];
                    java.util.Arrays.fill(held[ch], 0);
                    if (d1 == 120) sustain[ch] = false;
                }
                break;
        }
    }

    /** Splits one source's byte stream (running status allowed) into messages for track(). */
    private class Tracker {
        private int status, d1, have;

        void feed(byte[] msg, int offset, int count) {
            for (int i = offset; i < offset + count; i++) {
                int b = msg[i] & 0xFF;
                if (b >= 0xF8) continue;              // real-time
                if (b >= 0x80) {                      // new status (SysEx etc. ignored)
                    status = b < 0xF0 ? b : 0;
                    have = 0;
                    continue;
                }
                if (status == 0) continue;
                int type = status & 0xF0;
                if (type == 0xC0 || type == 0xD0) continue;  // one data byte, nothing to track
                if (have == 0) {
                    d1 = b;
                    have = 1;
                } else {
                    track(status, d1, b);
                    have = 0;                         // running status: next pair reuses it
                }
            }
        }
    }

    private boolean isOwnDevice(MidiDeviceInfo info) {
        Bundle p = info.getProperties();
        return info.getType() == MidiDeviceInfo.TYPE_VIRTUAL
                && "MIDI Bridge".equals(p.getString(MidiDeviceInfo.PROPERTY_PRODUCT))
                && "Android USB MIDI Bridge".equals(p.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER));
    }

    /** Sustain off + all notes off on every channel, so nothing's left hanging. */
    public void allNotesOff() {
        MidiInputPort out = toPort;
        if (out == null) return;
        byte[] b = new byte[6];
        try {
            for (int ch = 0; ch < 16; ch++) {
                b[0] = (byte) (0xB0 | ch); b[1] = 64; b[2] = 0;
                b[3] = (byte) (0xB0 | ch); b[4] = 123; b[5] = 0;
                out.send(b, 0, b.length);
            }
        } catch (IOException ignored) {
        }
        clearSounding();
    }

    private void changed() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    // ------------------------------------------------------------------
    // Forwarding (runs on a MIDI binder thread)
    // ------------------------------------------------------------------

    private class Forwarder extends MidiReceiver {
        private final byte[] buf = new byte[1024];

        @Override
        public void onSend(byte[] msg, int offset, int count, long timestamp) throws IOException {
            MidiInputPort out = toPort;
            if (out == null) return;
            // Drop real-time bytes (clock, active sensing, etc.) — they can
            // appear anywhere in the stream and the FM-1 has no use for them.
            int n = 0;
            for (int i = offset; i < offset + count && n < buf.length; i++) {
                int b = msg[i] & 0xFF;
                if (b >= 0xF8) continue;
                buf[n++] = msg[i];
            }
            if (n == 0) return;
            out.send(buf, 0, n);
            keysTracker.feed(buf, 0, n);
            msgCount++;
            lastText = describe(buf, n);
            if ((buf[0] & 0xF0) == 0x90 && n > 2 && buf[2] != 0) {
                int note = buf[1] & 0x7F;
                lastNote = String.format(Locale.US, "%s   %.1f Hz", noteName(note), noteHz(note));
            }
        }
    }

    static String describe(byte[] b, int n) {
        int s = b[0] & 0xFF;
        int ch = (s & 0x0F) + 1;
        int d1 = n > 1 ? b[1] & 0x7F : 0;
        int d2 = n > 2 ? b[2] & 0x7F : 0;
        switch (s & 0xF0) {
            case 0x90:
                if (d2 > 0) return String.format(Locale.US, "Note on   %s  %.1f Hz  vel %d  (ch %d)", noteName(d1), noteHz(d1), d2, ch);
                // velocity 0 = note off
            case 0x80:
                return String.format(Locale.US, "Note off  %s  %.1f Hz  (ch %d)", noteName(d1), noteHz(d1), ch);
            case 0xB0:
                if (d1 == 64) return "Sustain " + (d2 >= 64 ? "ON" : "off");
                if (d1 == 1) return "Mod wheel " + d2;
                return String.format(Locale.US, "CC %d = %d  (ch %d)", d1, d2, ch);
            case 0xC0:
                return String.format(Locale.US, "Program change %d", d1 + 1);
            case 0xE0:
                return "Pitch bend " + (((d2 << 7) | d1) - 8192);
            case 0xD0:
                return "Aftertouch " + d1;
            default:
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(n, 8); i++) sb.append(String.format(Locale.US, "%02X ", b[i] & 0xFF));
                return sb.toString().trim();
        }
    }

    /** Equal-tempered pitch, A4 (note 69) = 440 Hz. */
    static double noteHz(int note) {
        return 440.0 * Math.pow(2, (note - 69) / 12.0);
    }

    static String noteName(int note) {
        return NOTE_NAMES[note % 12] + (note / 12 - 1);
    }

    // ------------------------------------------------------------------
    // Device lists
    // ------------------------------------------------------------------

    private final MidiManager.DeviceCallback deviceCallback = new MidiManager.DeviceCallback() {
        @Override
        public void onDeviceAdded(MidiDeviceInfo device) {
            refreshLists();
            tryConnect();
            changed();
        }

        @Override
        public void onDeviceRemoved(MidiDeviceInfo device) {
            refreshLists();
            if (fromDev != null && device.getId() == fromDev.getInfo().getId()
                    || toDev != null && device.getId() == toDev.getInfo().getId()) {
                disconnect(false);
            }
            changed();
        }
    };

    private static String deviceName(MidiDeviceInfo info) {
        Bundle p = info.getProperties();
        String name = p.getString(MidiDeviceInfo.PROPERTY_NAME);
        if (name == null) name = p.getString(MidiDeviceInfo.PROPERTY_PRODUCT);
        return name != null ? name : "MIDI device " + info.getId();
    }

    private static String portLabel(MidiDeviceInfo info, int type, int index, int portCount) {
        String name = deviceName(info);
        if (portCount <= 1) return name;
        String portName = null;
        for (MidiDeviceInfo.PortInfo pi : info.getPorts()) {
            if (pi.getType() == type && pi.getPortNumber() == index) portName = pi.getName();
        }
        return name + " — port " + (index + 1)
                + (portName != null && !portName.isEmpty() ? " (" + portName + ")" : "");
    }

    @SuppressWarnings("deprecation")  // getDevices() still lists USB devices on every version
    private void refreshLists() {
        sources.clear();
        dests.clear();
        for (MidiDeviceInfo info : midi.getDevices()) {
            if (isOwnDevice(info)) continue;   // our own "MIDI Bridge" input for other apps
            int outs = info.getOutputPortCount();
            for (int i = 0; i < outs; i++) {
                sources.add(new PortRef(info, i, portLabel(info, MidiDeviceInfo.PortInfo.TYPE_OUTPUT, i, outs)));
            }
            int ins = info.getInputPortCount();
            for (int i = 0; i < ins; i++) {
                dests.add(new PortRef(info, i, portLabel(info, MidiDeviceInfo.PortInfo.TYPE_INPUT, i, ins)));
            }
        }
    }

    /** The saved choice if it's plugged in (and not on `avoid`'s device), else the first port whose name matches a hint, else any other device. */
    private static PortRef pick(List<PortRef> list, String saved, String[] hints, PortRef avoid) {
        if (saved != null) {
            // Never reuse a saved TO that's on the FROM device (e.g. the keyboard's own input port).
            for (PortRef r : list)
                if (r.label.equals(saved) && (avoid == null || r.info.getId() != avoid.info.getId())) return r;
        }
        for (String h : hints) {
            for (PortRef r : list) {
                if (avoid != null && r.info.getId() == avoid.info.getId()) continue;
                if (r.label.toLowerCase(Locale.US).contains(h)) return r;
            }
        }
        for (PortRef r : list) {
            if (avoid == null || r.info.getId() != avoid.info.getId()) return r;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Connect / disconnect
    // ------------------------------------------------------------------

    private void tryConnect() {
        if (!on || connecting || fromPort != null || midi == null) return;
        PortRef from = from();
        PortRef to = to();
        error = null;
        if (from == null || to == null) return;
        if (fromIsSilent()) return;
        if (from.info.getId() == to.info.getId()) {
            error = "Pick different devices for From and To.";
            return;
        }
        connecting = true;
        midi.openDevice(from.info, fDev -> {
            if (fDev == null) {
                failConnect("Couldn't open " + from.label);
                return;
            }
            fromDev = fDev;
            midi.openDevice(to.info, tDev -> {
                if (tDev == null) {
                    failConnect("Couldn't open " + to.label);
                    return;
                }
                finishConnect(from, to, tDev);
            }, ui);
        }, ui);
    }

    private void finishConnect(PortRef from, PortRef to, MidiDevice tDev) {
        toDev = tDev;
        MidiInputPort in = tDev.openInputPort(to.port);
        fromPort = fromDev.openOutputPort(from.port);
        toPort = in;
        connecting = false;
        if (toPort == null || fromPort == null) {
            String busy = toPort == null ? to.label : from.label;
            disconnect(false);
            error = "Couldn't open " + busy + " (another app may be using it).";
            changed();
            return;
        }
        connectedFrom = from.label;
        connectedTo = to.label;
        fromPort.connect(new Forwarder());
        if (!on) disconnect(true);  // switched off while we were opening
        changed();
    }

    private void failConnect(String why) {
        connecting = false;
        disconnect(false);
        error = why;
        changed();
    }

    private void disconnect(boolean releaseNotes) {
        if (releaseNotes) allNotesOff();
        clearSounding();
        MidiInputPort in = toPort;
        toPort = null;
        closeQuietly(fromPort);
        closeQuietly(in);
        if (toDev != null && toDev != fromDev) closeQuietly(toDev);
        closeQuietly(fromDev);
        fromPort = null;
        fromDev = null;
        toDev = null;
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }
}

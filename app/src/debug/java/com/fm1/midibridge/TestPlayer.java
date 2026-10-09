package com.fm1.midibridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Debug builds only: plays notes as if from the keyboard, so the bridge, the
 * FM-1 and the note/chord display can be tested without touching the keys.
 *
 *   adb shell am broadcast -a com.fm1.midibridge.TEST_PLAY -n com.fm1.midibridge/.TestPlayer \
 *       --es play "60,64,67;57,60,64,67;62" --ei ms 1500
 *
 * "play": groups separated by ';' (one note = a single key, several = a chord),
 * each held for "ms" milliseconds (default 1500) with a short gap between.
 */
public class TestPlayer extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String play = intent.getStringExtra("play");
        int ms = intent.getIntExtra("ms", 1500);
        Log.i("MidiBridge", "TEST_PLAY play=" + play + " ms=" + ms);
        if (play == null) return;
        Bridge bridge = Bridge.get(ctx.getApplicationContext());
        PendingResult done = goAsync();
        new Thread(() -> {
            try {
                for (String group : play.split(";")) {
                    String[] keys = group.trim().split(",");
                    for (String k : keys) send(bridge, 0x90, Integer.parseInt(k.trim()), 90);
                    Log.i("MidiBridge", "TEST_PLAY sent " + group + ", forwarding=" + bridge.canSendFromApp() + ", msgs=" + bridge.msgCount());
                    Thread.sleep(ms);
                    for (String k : keys) send(bridge, 0x80, Integer.parseInt(k.trim()), 0);
                    Thread.sleep(250);
                }
            } catch (Exception e) {
                Log.w("MidiBridge", "TEST_PLAY failed", e);
            } finally {
                done.finish();
            }
        }).start();
    }

    private static void send(Bridge bridge, int status, int note, int vel) throws Exception {
        bridge.playAsKeyboard(new byte[]{(byte) status, (byte) note, (byte) vel});
    }
}

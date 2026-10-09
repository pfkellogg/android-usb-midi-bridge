package com.fm1.midibridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fire TV: after a restart, carry on bridging if it was ON, without opening
 * the app. (On a tablet you open the app; a Fire TV stick just sits by the TV.)
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Bridge bridge = Bridge.get(ctx);
        if (bridge.isOn()) bridge.setOn(true);  // starts BridgeService, connects when both are plugged in
    }
}

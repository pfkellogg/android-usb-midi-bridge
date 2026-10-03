package com.fm1.midibridge;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * The screen: shows and controls the Bridge (see Bridge.java), which does
 * the actual forwarding and keeps running in BridgeService after you leave
 * this screen for another app.
 */
public class MainActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Bridge bridge;

    private Button fromBtn, toBtn;
    private Button toggle;
    private TextView status, lastMsg, bigNote;
    private final Runnable onChange = this::refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bridge = Bridge.get(this);
        buildUi();
        if (!bridge.hasMidi()) toggle.setEnabled(false);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        // Was ON last time (e.g. the app was closed or the tablet restarted): carry on.
        if (bridge.isOn()) bridge.setOn(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bridge.addListener(onChange);
        refresh();
        ui.post(ticker);
    }

    @Override
    protected void onStop() {
        // Only the screen stops updating — the bridge itself keeps running.
        bridge.removeListener(onChange);
        ui.removeCallbacks(ticker);
        super.onStop();
    }

    /**
     * Cheap tablets often kill background apps to save battery even when they
     * have a foreground service. Ask once to be exempt ("Unrestricted").
     */
    private void askToRunInBackground() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
        }
    }

    private void refresh() {
        Bridge.PortRef from = bridge.from();
        Bridge.PortRef to = bridge.to();
        fromBtn.setText((from != null ? from.label : "(no keyboard found)") + "   ▾");
        toBtn.setText((to != null ? to.label : "(nothing found — tap to see devices)") + "   ▾");
        status.setText(bridge.status());
        updateToggle();
    }

    /** Big-text device list in a dialog — works at any font size, unlike a Spinner. */
    private void pickPort(boolean isFrom) {
        List<Bridge.PortRef> items = isFrom ? bridge.sources() : bridge.dests();
        String title = isFrom ? "FROM — the keyboard you play" : "TO — the FM-1 (or MIDI interface)";
        if (items.isEmpty()) {
            new AlertDialog.Builder(this).setTitle(title)
                    .setMessage("No MIDI devices found. Check the hub and cables.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        ArrayAdapter<Bridge.PortRef> ad = new ArrayAdapter<Bridge.PortRef>(this, android.R.layout.simple_list_item_1, items) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextSize(20);
                t.setSingleLine(false);
                t.setTextColor(TEXT);
                t.setPadding(dp(20), dp(16), dp(20), dp(16));
                return t;
            }
        };
        new AlertDialog.Builder(this).setTitle(title)
                .setAdapter(ad, (d, which) -> {
                    Bridge.PortRef picked = items.get(which);
                    if (isFrom) bridge.choose(picked, bridge.to());
                    else bridge.choose(bridge.from(), picked);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Palette: white on black, high contrast. Every color is set explicitly so
    // the tablet's own theme can't produce grey-on-grey text.
    private static final int BG = Color.BLACK;
    private static final int CARD = Color.BLACK;
    private static final int BORDER = Color.rgb(90, 90, 90);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(200, 200, 200);
    private static final int ON_GREEN = Color.rgb(0, 150, 60);
    private static final int OFF_GREY = Color.rgb(60, 60, 60);

    private void buildUi() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(page);

        // One centered column, at most 640dp wide so it doesn't sprawl on a tablet.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int screen = getResources().getDisplayMetrics().widthPixels;
        root.setLayoutParams(new LinearLayout.LayoutParams(Math.min(screen - dp(32), dp(640)),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.setPadding(0, dp(32), 0, dp(32));
        page.addView(root);

        TextView title = text("MIDI Bridge", 30, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(title);
        TextView subtitle = text("Keyboard → FM-1      v" + versionName(), 16, MUTED);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        root.addView(subtitle);

        LinearLayout devices = card();
        devices.addView(label("FROM  ·  keyboard"));
        fromBtn = pickerButton(v -> pickPort(true));
        devices.addView(fromBtn);
        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dlp.topMargin = dp(12);
        divider.setLayoutParams(dlp);
        devices.addView(divider);
        devices.addView(label("TO  ·  FM-1 (USB) or MIDI interface"));
        toBtn = pickerButton(v -> pickPort(false));
        devices.addView(toBtn);
        root.addView(devices);

        toggle = new Button(this);
        toggle.setTextSize(22);
        toggle.setAllCaps(false);
        toggle.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76));
        lp.topMargin = dp(20);
        toggle.setLayoutParams(lp);
        toggle.setOnClickListener(v -> {
            boolean want = !bridge.isOn();
            if (want) askToRunInBackground();
            bridge.setOn(want);
        });
        root.addView(toggle);

        TextView bgNote = text("Keeps running when you switch apps or the screen turns off. "
                + "Stop it here or from the notification.", 14, MUTED);
        bgNote.setPadding(0, dp(8), 0, 0);
        root.addView(bgNote);

        Button panic = new Button(this);
        panic.setText("All notes off");
        panic.setAllCaps(false);
        panic.setTextSize(17);
        panic.setTextColor(TEXT);
        panic.setStateListAnimator(null);
        panic.setBackground(rounded(CARD, BORDER, 14));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        plp.topMargin = dp(12);
        panic.setLayoutParams(plp);
        panic.setOnClickListener(v -> bridge.allNotesOff());
        root.addView(panic);

        LinearLayout info = card();
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(20);
        info.setLayoutParams(ilp);
        info.addView(label("STATUS"));
        status = text("", 20, TEXT);
        status.setLineSpacing(0, 1.15f);
        info.addView(status);
        info.addView(label("NOTE"));
        bigNote = text("—", 44, TEXT);
        bigNote.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        info.addView(bigNote);
        info.addView(label("LAST MESSAGE"));
        lastMsg = text("—", 20, TEXT);
        lastMsg.setTypeface(Typeface.MONOSPACE);
        lastMsg.setLineSpacing(0, 1.15f);
        info.addView(lastMsg);
        root.addView(info);

        setContentView(scroll);
    }

    /** A full-width button showing the chosen device; text wraps at any font size. */
    private Button pickerButton(View.OnClickListener l) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextSize(19);
        b.setTextColor(TEXT);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setSingleLine(false);
        b.setStateListAnimator(null);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setBackground(rounded(Color.rgb(28, 28, 30), BORDER, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 15, MUTED);
        t.setLetterSpacing(0.08f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(CARD, BORDER, 16));
        c.setPadding(dp(20), dp(6), dp(20), dp(18));
        return c;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void updateToggle() {
        boolean on = bridge.isOn();
        toggle.setText(on ? "●  ON  —  tap to stop" : "OFF  —  tap to start");
        toggle.setBackground(rounded(on ? ON_GREEN : OFF_GREY, 0, 16));
        toggle.setTextColor(Color.WHITE);
    }

    private final Runnable ticker = new Runnable() {
        private long shownCount = -1;

        @Override
        public void run() {
            long count = bridge.msgCount();
            if (count != shownCount) {
                shownCount = count;
                lastMsg.setText(count > 0 ? bridge.lastText() + "\n" + count + " messages forwarded" : "—");
                bigNote.setText(bridge.lastNote());
            }
            ui.postDelayed(this, 100);
        }
    };
}

package dev.scw.clockbridge;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import org.json.JSONObject;

/**
 * Receives events only from Samsung Clock (see res/xml/accessibility.xml).
 * When the alarm list settles on screen, reads it and hands it to {@link Sender}.
 */
public class ClockReaderService extends AccessibilityService {
    private static final String TAG = "ClockBridge";
    private static final long SETTLE_MS = 500;
    private static final long MAX_WAIT_MS = 2000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable settle = this::settle;
    private JSONObject latest;      // newest list seen since the last submit
    private long pendingSince = -1;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Read now, while Clock is still on screen: if the user leaves right after
        // a change, the delayed read below finds another app and would lose it.
        JSONObject read = read();
        if (read != null) latest = read;

        long now = SystemClock.uptimeMillis();
        if (pendingSince < 0) pendingSince = now;
        handler.removeCallbacks(settle);
        // A list that keeps changing must not postpone the send forever.
        handler.postDelayed(settle, Math.max(0, Math.min(SETTLE_MS, pendingSince + MAX_WAIT_MS - now)));
    }

    private void settle() {
        pendingSince = -1;
        JSONObject read = read();
        JSONObject payload = read != null ? read : latest;
        latest = null;
        if (payload == null) { Log.d(TAG, "not on the alarm list"); return; }
        Log.d(TAG, (read != null ? "read " : "left Clock, using last read of ")
                + payload.optJSONArray("alarms").length() + " alarms: " + Sender.signature(payload));
        Sender.submit(this, payload);
    }

    private JSONObject read() {
        try {
            return AlarmListReader.read(getRootInActiveWindow());
        } catch (Exception e) {
            Log.w(TAG, "reading alarm list failed", e);
            return null;
        }
    }

    @Override public void onInterrupt() {}
}

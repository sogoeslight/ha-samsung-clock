package dev.scw.clockbridge;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import org.json.JSONObject;

/**
 * Receives events only from Samsung Clock (see res/xml/accessibility.xml).
 * When the alarm list settles on screen, reads it and hands it to {@link Sender}.
 */
public class ClockReaderService extends AccessibilityService {
    private static final String TAG = "ClockBridge";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable readNow = this::readList;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        handler.removeCallbacks(readNow);
        handler.postDelayed(readNow, 1000);  // let the list finish animating
    }

    private void readList() {
        try {
            JSONObject payload = AlarmListReader.read(getRootInActiveWindow());
            if (payload == null) { Log.d(TAG, "not on the alarm list"); return; }
            Log.d(TAG, "read " + payload.optJSONArray("alarms").length() + " alarms: " + Sender.signature(payload));
            Sender.submit(this, payload);
        } catch (Exception e) {
            Log.w(TAG, "reading alarm list failed", e);
        }
    }

    @Override public void onInterrupt() {}
}

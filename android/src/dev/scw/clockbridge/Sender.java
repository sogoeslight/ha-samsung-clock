package dev.scw.clockbridge;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Sends the latest alarm list to the Home Assistant webhook; retries via JobScheduler when offline. */
final class Sender {
    static final String PREFS = "clockbridge";
    static final String KEY_URL = "webhook_url";
    static final String KEY_PENDING = "pending";      // payload not yet delivered
    static final String KEY_SENT_SIG = "sent_sig";    // alarm list last delivered
    static final String KEY_STATUS = "status";
    static final String KEY_LAST = "last_payload";
    static final String KEY_OK_AT = "ok_at";          // epoch millis of last successful delivery
    static final String KEY_ERROR = "error";          // current problem, removed on success
    private static final String TAG = "ClockBridge";
    private static final int JOB_ID = 1;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private Sender() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Signature that ignores read_at, so re-opening Clock without changes sends nothing. */
    static String signature(JSONObject p) {
        return p.optJSONArray("alarms") + "|" + p.optBoolean("complete") + "|" + p.optString("clock_next");
    }

    /** Queue a freshly read list; sends only if it differs from what HA already has. */
    static void submit(Context ctx, JSONObject payload) {
        Context c = ctx.getApplicationContext();
        SharedPreferences sp = prefs(c);
        sp.edit().putString(KEY_LAST, payload.toString()).apply();
        if (signature(payload).equals(sp.getString(KEY_SENT_SIG, null)) && !sp.contains(KEY_PENDING)) {
            Log.d(TAG, "unchanged, not sending");
            return;
        }
        sp.edit().putString(KEY_PENDING, payload.toString()).apply();
        IO.execute(() -> { if (!flush(c)) scheduleRetry(c); });
    }

    /** Re-send the last read list even if unchanged (button in the app). */
    static void resend(Context ctx) {
        Context c = ctx.getApplicationContext();
        SharedPreferences sp = prefs(c);
        String last = sp.getString(KEY_LAST, null);
        if (last == null) { status(c, "Nothing to send yet: open Clock on the Alarm tab"); return; }
        sp.edit().putString(KEY_PENDING, last).apply();
        IO.execute(() -> { if (!flush(c)) scheduleRetry(c); });
    }

    /** Deliver the pending payload. Returns true when nothing is left to send. Blocking. */
    static synchronized boolean flush(Context c) {
        SharedPreferences sp = prefs(c);
        String pending = sp.getString(KEY_PENDING, null);
        if (pending == null) return true;
        String url = sp.getString(KEY_URL, "").trim();
        if (url.isEmpty()) {
            sp.edit().putString(KEY_ERROR, "Add your Home Assistant webhook URL below.").apply();
            status(c, "Webhook URL is not set");
            return true;
        }
        HttpURLConnection conn = null;
        try {
            byte[] body = pending.getBytes(StandardCharsets.UTF_8);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = conn.getOutputStream()) { os.write(body); }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                JSONObject sent = new JSONObject(pending);
                // Only clear if nothing newer was queued meanwhile.
                SharedPreferences.Editor e = sp.edit().putString(KEY_SENT_SIG, signature(sent))
                        .putLong(KEY_OK_AT, System.currentTimeMillis()).remove(KEY_ERROR);
                if (pending.equals(sp.getString(KEY_PENDING, null))) e.remove(KEY_PENDING);
                e.apply();
                status(c, "Sent " + sent.optJSONArray("alarms").length() + " alarms");
                return !sp.contains(KEY_PENDING) || flush(c);
            }
            String err = code == 404 || code == 405
                    ? "Home Assistant doesn't know this webhook (HTTP " + code + "). Check the URL."
                    : "Home Assistant error: HTTP " + code;
            sp.edit().putString(KEY_ERROR, err).apply();
            status(c, err);
            return code >= 400 && code < 500;  // bad URL / payload: retrying will not help
        } catch (Exception ex) {
            Log.i(TAG, "send failed, will retry: " + ex);
            sp.edit().putString(KEY_ERROR, "Can't reach Home Assistant. Will retry when online.").apply();
            status(c, "Cannot reach Home Assistant, will retry (" + ex.getClass().getSimpleName() + ")");
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    static void scheduleRetry(Context c) {
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, RetryJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(false)
                .build();
        c.getSystemService(JobScheduler.class).schedule(job);
    }

    private static void status(Context c, String s) {
        String t = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        prefs(c).edit().putString(KEY_STATUS, t + " — " + s).apply();
        Log.i(TAG, s);
    }
}

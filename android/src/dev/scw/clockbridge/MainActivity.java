package dev.scw.clockbridge;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Settings screen: webhook URL, accessibility status, last send result. */
public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private EditText url;
    private TextView serviceState, status;
    private SharedPreferences sp;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = Sender.prefs(this);
        // Setup shortcut: adb shell am start -n dev.scw.clockbridge/.MainActivity --es webhook_url <url>
        String fromIntent = getIntent().getStringExtra("webhook_url");
        if (fromIntent != null && fromIntent.startsWith("http")) {
            sp.edit().putString(Sender.KEY_URL, fromIntent.trim()).apply();
        }
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad * 3, pad, pad);

        col.addView(label("Webhook URL from Home Assistant\n(Settings → Devices & services → Samsung Clock Alarm → add a phone)"));
        url = new EditText(this);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://…/api/webhook/…");
        url.setText(sp.getString(Sender.KEY_URL, ""));
        col.addView(url);
        col.addView(button("Save", () -> {
            sp.edit().putString(Sender.KEY_URL, url.getText().toString().trim()).apply();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        }));

        serviceState = label("");
        col.addView(serviceState);
        col.addView(button("Open Accessibility settings",
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        col.addView(button("Open Clock",
                () -> {
                    Intent i = getPackageManager().getLaunchIntentForPackage(AlarmListReader.CLOCK);
                    if (i != null) startActivity(i);
                }));
        col.addView(button("Send again", () -> Sender.resend(this)));

        status = label("");
        col.addView(status);
        col.addView(label("The alarm list is sent automatically whenever Clock shows the Alarm tab. "
                + "If an alarm was changed outside Clock (voice, watch, widget), open Clock to update Home Assistant."));

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }

    @Override protected void onResume() {
        super.onResume();
        sp.registerOnSharedPreferenceChangeListener(this);
        refresh();
    }

    @Override protected void onPause() {
        super.onPause();
        sp.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        runOnUiThread(this::refresh);
    }

    private void refresh() {
        serviceState.setText(serviceEnabled()
                ? "✅ Alarm reader service is on"
                : "⚠️ Service is off: Accessibility → Installed apps → Clock Bridge");
        status.setText("Status: " + sp.getString(Sender.KEY_STATUS, "nothing sent yet"));
    }

    private boolean serviceEnabled() {
        String list = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String me = new ComponentName(this, ClockReaderService.class).flattenToString();
        return list != null && list.contains(me);
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setPadding(0, 24, 0, 8);
        return t;
    }

    private Button button(String s, Runnable r) {
        Button b = new Button(this);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(v -> r.run());
        return b;
    }
}

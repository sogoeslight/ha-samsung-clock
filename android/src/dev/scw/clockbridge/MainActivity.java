package dev.scw.clockbridge;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

/** One screen: reader status, the alarms last read from Clock, and the Home Assistant webhook. */
public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SharedPreferences sp;
    private boolean dark;
    private float dp;
    private int cPage, cCard, cText, cMuted, cAccent, cOnAccent, cGood, cWarn, cTonal;

    private TextView dot, serviceLine, sendLine, nextLine;
    private Button enableButton;
    private LinearLayout alarmsBox;
    private EditText url;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = Sender.prefs(this);
        // Setup shortcut: adb shell am start -n dev.scw.clockbridge/.MainActivity --es webhook_url <url>
        String fromIntent = getIntent().getStringExtra("webhook_url");
        if (fromIntent != null && fromIntent.startsWith("http")) {
            sp.edit().putString(Sender.KEY_URL, fromIntent.trim()).apply();
        }
        initColors();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(20), px(56), px(20), px(32));

        // Header
        TextView title = text("Clock Bridge", 30, cText, true);
        col.addView(title);
        TextView subtitle = text("Samsung Clock alarms → Home Assistant", 15, cMuted, false);
        subtitle.setPadding(0, px(4), 0, px(20));
        col.addView(subtitle);

        // Status card
        LinearLayout status = card(col);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        dot = text("●", 16, cGood, false);
        dot.setPadding(0, 0, px(10), 0);
        row.addView(dot);
        serviceLine = text("", 17, cText, true);
        row.addView(serviceLine);
        status.addView(row);
        sendLine = text("", 14, cMuted, false);
        sendLine.setPadding(px(20), px(6), 0, 0);
        status.addView(sendLine);
        nextLine = text("", 14, cMuted, false);
        nextLine.setPadding(px(20), px(2), 0, 0);
        status.addView(nextLine);
        enableButton = button("Turn on in Accessibility", true,
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        status.addView(enableButton, buttonParams(px(14)));

        // Actions
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, px(12), 0, px(12));
        Button open = button("Open Clock", true, this::openClock);
        Button again = button("Send again", false, () -> {
            Sender.resend(this);
            Toast.makeText(this, "Sending…", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, px(48), 1f);
        actions.addView(open, half);
        View gap = new View(this);
        actions.addView(gap, new LinearLayout.LayoutParams(px(10), 1));
        actions.addView(again, new LinearLayout.LayoutParams(0, px(48), 1f));
        col.addView(actions);

        // Alarms card
        col.addView(sectionLabel("Alarms in Samsung Clock"));
        LinearLayout alarmsCard = card(col);
        alarmsBox = new LinearLayout(this);
        alarmsBox.setOrientation(LinearLayout.VERTICAL);
        alarmsCard.addView(alarmsBox);

        // Home Assistant card
        col.addView(sectionLabel("Home Assistant"));
        LinearLayout ha = card(col);
        ha.addView(text("Webhook URL", 14, cMuted, false));
        url = new EditText(this);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setImeOptions(EditorInfo.IME_ACTION_DONE);
        url.setHint("https://…/api/webhook/…");
        url.setTextSize(15);
        url.setTextColor(cText);
        url.setHintTextColor(cMuted);
        url.setBackgroundTintList(ColorStateList.valueOf(cAccent));
        url.setText(sp.getString(Sender.KEY_URL, ""));
        url.setSingleLine(true);
        ha.addView(url);
        Button save = button("Save", false, this::saveUrl);
        ha.addView(save, buttonParams(px(8)));
        TextView where = text("Get it in Home Assistant: Settings → Devices & services → "
                + "Add integration → Samsung Clock Alarm.", 13, cMuted, false);
        where.setPadding(0, px(12), 0, 0);
        ha.addView(where);

        // Footer
        TextView foot = text("The list is sent automatically whenever Clock shows the Alarm tab. "
                + "If you change an alarm elsewhere (voice, watch, widget), open Clock once to update "
                + "Home Assistant. Only Samsung Clock is read.", 13, cMuted, false);
        foot.setPadding(px(4), px(8), px(4), 0);
        col.addView(foot);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(cPage);
        sv.setFillViewport(true);
        sv.addView(col);
        setContentView(sv);
        getWindow().setStatusBarColor(cPage);
        getWindow().setNavigationBarColor(cPage);
        if (!dark) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
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

    // ---- actions ----

    private void openClock() {
        Intent i = getPackageManager().getLaunchIntentForPackage(AlarmListReader.CLOCK);
        if (i == null) {
            Toast.makeText(this, "Samsung Clock is not installed", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(i);
    }

    private void saveUrl() {
        String u = url.getText().toString().trim();
        Uri parsed = Uri.parse(u);
        if (!u.isEmpty() && (parsed.getScheme() == null || !parsed.getScheme().startsWith("http") || parsed.getHost() == null)) {
            Toast.makeText(this, "That doesn't look like a URL", Toast.LENGTH_SHORT).show();
            return;
        }
        sp.edit().putString(Sender.KEY_URL, u).remove(Sender.KEY_ERROR).apply();
        url.clearFocus();
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        if (!u.isEmpty()) Sender.resend(this);
    }

    // ---- state ----

    private void refresh() {
        boolean on = serviceEnabled();
        String error = sp.getString(Sender.KEY_ERROR, null);
        boolean noUrl = sp.getString(Sender.KEY_URL, "").trim().isEmpty();

        if (!on) {
            dot.setTextColor(cWarn);
            serviceLine.setText("Alarm reader is off");
        } else if (noUrl || error != null) {
            dot.setTextColor(cWarn);
            serviceLine.setText(noUrl ? "Webhook URL is missing" : "Not delivered yet");
        } else {
            dot.setTextColor(cGood);
            serviceLine.setText("Working");
        }
        enableButton.setVisibility(on ? View.GONE : View.VISIBLE);

        long okAt = sp.getLong(Sender.KEY_OK_AT, 0);
        String sent = okAt == 0 ? "Nothing sent to Home Assistant yet"
                : "Sent to Home Assistant " + DateUtils.getRelativeTimeSpanString(
                        okAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        sendLine.setText(error != null ? error + "\n" + sent : sent);

        JSONObject last = null;
        try {
            String raw = sp.getString(Sender.KEY_LAST, null);
            if (raw != null) last = new JSONObject(raw);
        } catch (Exception ignored) {
        }
        String next = last == null ? null : last.optString("clock_next", null);
        nextLine.setText(next == null || next.isEmpty() ? "" : "Next alarm: " + next);
        nextLine.setVisibility(nextLine.getText().length() == 0 ? View.GONE : View.VISIBLE);

        renderAlarms(last);
    }

    private void renderAlarms(JSONObject last) {
        alarmsBox.removeAllViews();
        JSONArray alarms = last == null ? null : last.optJSONArray("alarms");
        if (alarms == null || alarms.length() == 0) {
            TextView empty = text(alarms == null
                    ? "Open Clock once so the app can read your alarms."
                    : "No alarms in Clock.", 15, cMuted, false);
            empty.setPadding(0, px(4), 0, px(4));
            alarmsBox.addView(empty);
            return;
        }
        for (int i = 0; i < alarms.length(); i++) {
            JSONObject a = alarms.optJSONObject(i);
            if (a == null) continue;
            boolean enabled = a.optBoolean("enabled");
            if (i > 0) alarmsBox.addView(divider());

            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, px(10), 0, px(10));

            TextView time = text(a.optString("time"), 26, enabled ? cText : cMuted, false);
            time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            time.setMinWidth(px(92));
            r.addView(time);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            String name = a.optString("name").trim();
            if (!name.isEmpty()) info.addView(text(name, 15, enabled ? cText : cMuted, true));
            info.addView(text(shortSchedule(a.optString("schedule")), 13, cMuted, false));
            r.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView pill = text(enabled ? "On" : "Off", 12, enabled ? cOnAccent : cMuted, true);
            pill.setPadding(px(10), px(3), px(10), px(3));
            pill.setBackground(rounded(enabled ? cAccent : cTonal, px(12)));
            r.addView(pill);

            r.setAlpha(enabled ? 1f : 0.7f);
            alarmsBox.addView(r);
        }
        if (!last.optBoolean("complete", true)) {
            TextView partial = text("Some alarms were off screen. Scroll through the list in Clock to send them all.",
                    13, cMuted, false);
            partial.setPadding(0, px(8), 0, 0);
            alarmsBox.addView(partial);
        }
    }

    /** "Every Monday, Every Tuesday, …" → "Mon, Tue, …"; leaves dates and "Every day" alone. */
    static String shortSchedule(String s) {
        if (s == null || !s.startsWith("Every ") || s.equals("Every day")) return s == null ? "" : s;
        String[] parts = s.split(",\\s*");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            String d = p.replaceFirst("^Every\\s+", "");
            if (out.length() > 0) out.append(", ");
            out.append(d.length() > 3 ? d.substring(0, 3) : d);
        }
        String r = out.toString();
        if (r.equals("Mon, Tue, Wed, Thu, Fri")) return "Weekdays";
        if (r.equals("Sat, Sun") || r.equals("Sun, Sat")) return "Weekends";
        return r;
    }

    private boolean serviceEnabled() {
        String list = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String me = new ComponentName(this, ClockReaderService.class).flattenToString();
        return list != null && list.contains(me);
    }

    // ---- look ----

    private void initColors() {
        dp = getResources().getDisplayMetrics().density;
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        cPage = dark ? Color.rgb(0, 0, 0) : Color.rgb(244, 244, 246);
        cCard = dark ? Color.rgb(28, 28, 30) : Color.WHITE;
        cText = dark ? Color.rgb(242, 242, 245) : Color.rgb(20, 20, 24);
        cMuted = dark ? Color.rgb(150, 150, 158) : Color.rgb(110, 110, 118);
        // Material You accent from the wallpaper palette.
        cAccent = getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
        cOnAccent = getColor(dark ? android.R.color.system_accent1_800 : android.R.color.white);
        cTonal = getColor(dark ? android.R.color.system_accent2_800 : android.R.color.system_accent2_100);
        cGood = dark ? Color.rgb(76, 217, 100) : Color.rgb(36, 160, 70);
        cWarn = dark ? Color.rgb(255, 179, 64) : Color.rgb(214, 120, 0);
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 14, cMuted, true);
        t.setPadding(px(6), px(14), 0, px(8));
        return t;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(18), px(16), px(18), px(16));
        c.setBackground(rounded(cCard, px(24)));
        parent.addView(c, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(dark ? Color.rgb(44, 44, 48) : Color.rgb(232, 232, 236));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(0.6f))));
        return v;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private Button button(String label, boolean primary, Runnable onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(primary ? cOnAccent : (dark ? cAccent : getColor(android.R.color.system_accent1_700)));
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setMinHeight(px(48));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF & 0xFFFFFFFF | 0x22000000),
                rounded(primary ? cAccent : cTonal, px(24)), null));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private LinearLayout.LayoutParams buttonParams(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(48));
        p.topMargin = top;
        return p;
    }
}

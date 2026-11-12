package dev.scw.clockbridge;

import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import java.time.OffsetDateTime;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Reads Samsung Clock's alarm list from its accessibility tree (Clock 12.x, English UI). */
final class AlarmListReader {
    static final String CLOCK = "com.sec.android.app.clockpackage";
    private static final String ID = CLOCK + ":id/";

    private AlarmListReader() {}

    /** Payload for Home Assistant, or null if the alarm list is not on screen. */
    static JSONObject read(AccessibilityNodeInfo root) throws JSONException {
        if (root == null || root.getPackageName() == null || !CLOCK.contentEquals(root.getPackageName())) return null;
        List<AccessibilityNodeInfo> items = root.findAccessibilityNodeInfosByViewId(ID + "alarm_list_item");
        boolean emptyList = items.isEmpty() && !root.findAccessibilityNodeInfosByViewId(ID + "alarm_app_bar_title").isEmpty();
        if (items.isEmpty() && !emptyList) return null;

        JSONArray alarms = new JSONArray();
        boolean complete = true;
        for (AccessibilityNodeInfo item : items) {
            String time = text(first(item, "alarm_item_time"));
            if (time == null) continue;
            String name = text(first(item, "alarm_list_alarm_name"));
            AccessibilityNodeInfo sw = first(item, "alarm_onoff_switch");
            String desc = sw != null && sw.getContentDescription() != null
                    ? sw.getContentDescription().toString()
                    : stripEditHint(item.getContentDescription());
            JSONObject a = new JSONObject();
            a.put("name", name == null ? "" : name.trim());
            a.put("time", time.trim());
            a.put("enabled", sw != null && sw.isChecked());
            a.put("schedule", schedule(desc));
            alarms.put(a);
            if (complete && canScroll(item.getParent())) complete = false;
        }

        JSONObject p = new JSONObject();
        p.put("alarms", alarms);
        p.put("complete", complete);
        String next = text(first(root, "alarm_app_bar_subtitle"));
        if (next != null) p.put("clock_next", next);
        p.put("read_at", OffsetDateTime.now().toString());
        return p;
    }

    /** "Work, 09:30 , Every Monday, Every Friday" -> "Every Monday, Every Friday". */
    static String schedule(String desc) {
        if (desc == null) return "";
        int i = desc.lastIndexOf(" , ");
        return (i >= 0 ? desc.substring(i + 3) : desc).trim();
    }

    private static String stripEditHint(CharSequence cd) {
        if (cd == null) return null;
        String s = cd.toString();
        int i = s.lastIndexOf(", , ");
        return i >= 0 ? s.substring(0, i) : s;
    }

    private static boolean canScroll(AccessibilityNodeInfo list) {
        if (list == null) return false;
        for (AccessibilityAction a : list.getActionList()) {
            int id = a.getId();
            if (id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) return true;
        }
        return false;
    }

    private static AccessibilityNodeInfo first(AccessibilityNodeInfo n, String id) {
        List<AccessibilityNodeInfo> l = n.findAccessibilityNodeInfosByViewId(ID + id);
        return l.isEmpty() ? null : l.get(0);
    }

    private static String text(AccessibilityNodeInfo n) {
        return n == null || n.getText() == null ? null : n.getText().toString();
    }
}

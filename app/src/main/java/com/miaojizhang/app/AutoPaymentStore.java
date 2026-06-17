package com.miaojizhang.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public final class AutoPaymentStore {
    public static final String PREFS = "miaojizhang_native";
    public static final String KEY_ENABLED = "auto_payment_enabled";
    public static final String KEY_HISTORY = "auto_payment_history";
    public static final String KEY_LAST_NOTIFY_AT = "auto_payment_last_notify_at";

    private AutoPaymentStore() {}

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static String history(Context context) {
        return prefs(context).getString(KEY_HISTORY, "[]");
    }

    public static void clearHistory(Context context) {
        prefs(context).edit().putString(KEY_HISTORY, "[]").apply();
    }

    public static boolean contains(Context context, String id) {
        if (id == null || id.length() == 0) return false;
        try {
            JSONArray arr = new JSONArray(history(context));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && id.equals(o.optString("id"))) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static void add(Context context, PaymentParser.Suggestion s, String status) {
        if (s == null || s.id == null) return;
        try {
            JSONArray old = new JSONArray(history(context));
            JSONArray arr = new JSONArray();
            JSONObject item = s.toJson();
            item.put("status", status == null ? "pending" : status);
            item.put("statusText", statusText(status));
            arr.put(item);
            for (int i = 0; i < old.length() && arr.length() < 40; i++) {
                JSONObject o = old.optJSONObject(i);
                if (o == null) continue;
                if (s.id.equals(o.optString("id"))) continue;
                arr.put(o);
            }
            prefs(context).edit()
                    .putString(KEY_HISTORY, arr.toString())
                    .putLong(KEY_LAST_NOTIFY_AT, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {}
    }

    public static void markStatus(Context context, String id, String status) {
        if (id == null || id.length() == 0) return;
        try {
            JSONArray old = new JSONArray(history(context));
            JSONArray arr = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o == null) continue;
                if (id.equals(o.optString("id"))) {
                    o.put("status", status);
                    o.put("statusText", statusText(status));
                }
                arr.put(o);
            }
            prefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static String statusText(String status) {
        if ("opened".equals(status)) return "已打开";
        if ("ignored".equals(status)) return "已忽略";
        if ("recorded".equals(status)) return "已记录";
        return "待处理";
    }
}

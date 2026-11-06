package com.example.visionassistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// نگهداری حدود ۱۵ خطای آخر برنامه (جدیدترین اول)
public class ErrorLog {
    private static final String PREFS = "error_log";
    private static final String KEY = "entries";
    private static final int MAX = 15;

    public static synchronized void add(Context ctx, String message) {
        if (message == null || message.trim().isEmpty()) return;
        try {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            List<String> all = getAll(ctx);
            all.add(0, time + "\n" + message.trim());
            while (all.size() > MAX) all.remove(all.size() - 1);
            JSONArray arr = new JSONArray();
            for (String s : all) arr.put(s);
            prefs(ctx).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static synchronized List<String> getAll(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(ctx).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    public static synchronized void clear(Context ctx) {
        prefs(ctx).edit().remove(KEY).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

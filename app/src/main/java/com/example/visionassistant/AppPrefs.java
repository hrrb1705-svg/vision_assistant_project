package com.example.visionassistant;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_API_KEY_PREFIX = "api_key_";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getLanguage(Context context) {
        return prefs(context).getString(KEY_LANGUAGE, "fa");
    }

    public static void setLanguage(Context context, String languageCode) {
        prefs(context).edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    // index از ۱ تا ۵
    public static String getApiKey(Context context, int index) {
        return prefs(context).getString(KEY_API_KEY_PREFIX + index, "");
    }

    public static void setApiKey(Context context, int index, String value) {
        prefs(context).edit().putString(KEY_API_KEY_PREFIX + index, value).apply();
    }
}

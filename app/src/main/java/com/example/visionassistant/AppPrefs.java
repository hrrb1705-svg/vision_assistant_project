package com.example.visionassistant;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_API_KEY_PREFIX = "api_key_";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_CUSTOM_ENDPOINT = "custom_endpoint";
    private static final String KEY_CUSTOM_MODEL = "custom_model";
    private static final String KEY_CUSTOM_API_KEY_PREFIX = "custom_api_key_";

    public static final String PROVIDER_GOOGLE = "google";
    public static final String PROVIDER_CUSTOM = "custom";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getLanguage(Context context) {
        return prefs(context).getString(KEY_LANGUAGE, "fa");
    }

    public static void setLanguage(Context context, String languageCode) {
        prefs(context).edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    public static String getProvider(Context context) {
        return prefs(context).getString(KEY_PROVIDER, PROVIDER_GOOGLE);
    }

    public static void setProvider(Context context, String provider) {
        prefs(context).edit().putString(KEY_PROVIDER, provider).apply();
    }

    // index از ۱ تا ۵ - کلیدهای گوگل
    public static String getApiKey(Context context, int index) {
        return prefs(context).getString(KEY_API_KEY_PREFIX + index, "");
    }

    public static void setApiKey(Context context, int index, String value) {
        prefs(context).edit().putString(KEY_API_KEY_PREFIX + index, value).apply();
    }

    public static String getCustomEndpoint(Context context) {
        return prefs(context).getString(KEY_CUSTOM_ENDPOINT, "");
    }

    public static void setCustomEndpoint(Context context, String value) {
        prefs(context).edit().putString(KEY_CUSTOM_ENDPOINT, value).apply();
    }

    public static String getCustomModel(Context context) {
        return prefs(context).getString(KEY_CUSTOM_MODEL, "");
    }

    public static void setCustomModel(Context context, String value) {
        prefs(context).edit().putString(KEY_CUSTOM_MODEL, value).apply();
    }

    // index از ۱ تا ۵ - کلیدهای سرویس دوم
    public static String getCustomApiKey(Context context, int index) {
        return prefs(context).getString(KEY_CUSTOM_API_KEY_PREFIX + index, "");
    }

    public static void setCustomApiKey(Context context, int index, String value) {
        prefs(context).edit().putString(KEY_CUSTOM_API_KEY_PREFIX + index, value).apply();
    }
}

package com.example.visionassistant;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_ACTIVE_PROVIDER = "active_provider";

    public static final String PROVIDER_GOOGLE = "google";
    public static final String PROVIDER_GROQ = "groq";
    public static final String PROVIDER_CUSTOM = "custom";

    public static final String GOOGLE_DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta";
    public static final String GOOGLE_DEFAULT_MODEL = "gemini-3.8-flash";
    public static final String GROQ_DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    public static final String GROQ_DEFAULT_MODEL = "";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getLanguage(Context context) {
        return prefs(context).getString(KEY_LANGUAGE, "fa");
    }

    public static void setLanguage(Context context, String languageCode) {
        prefs(context).edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    public static String getActiveProvider(Context context) {
        return prefs(context).getString(KEY_ACTIVE_PROVIDER, PROVIDER_GOOGLE);
    }

    public static void setActiveProvider(Context context, String provider) {
        prefs(context).edit().putString(KEY_ACTIVE_PROVIDER, provider).apply();
    }

    public static String getEndpoint(Context context, String provider) {
        String saved = prefs(context).getString("endpoint_" + provider, "");
        if (!saved.isEmpty()) {
            return saved;
        }
        // کلیدهای قبلی که با نام‌های قدیمی ذخیره شده بودند از دست نروند
        if (PROVIDER_CUSTOM.equals(provider)) {
            String legacy = prefs(context).getString("custom_endpoint", "");
            if (!legacy.isEmpty()) {
                return legacy;
            }
        }
        if (PROVIDER_GOOGLE.equals(provider)) {
            return GOOGLE_DEFAULT_ENDPOINT;
        }
        if (PROVIDER_GROQ.equals(provider)) {
            return GROQ_DEFAULT_ENDPOINT;
        }
        return "";
    }

    public static void setEndpoint(Context context, String provider, String value) {
        prefs(context).edit().putString("endpoint_" + provider, value).apply();
    }

    public static String getModel(Context context, String provider) {
        String saved = prefs(context).getString("model_" + provider, "");
        if (!saved.isEmpty()) {
            return saved;
        }
        if (PROVIDER_CUSTOM.equals(provider)) {
            String legacy = prefs(context).getString("custom_model", "");
            if (!legacy.isEmpty()) {
                return legacy;
            }
        }
        if (PROVIDER_GOOGLE.equals(provider)) {
            return GOOGLE_DEFAULT_MODEL;
        }
        if (PROVIDER_GROQ.equals(provider)) {
            return GROQ_DEFAULT_MODEL;
        }
        return "";
    }

    public static void setModel(Context context, String provider, String value) {
        prefs(context).edit().putString("model_" + provider, value).apply();
    }

    // index از ۱ تا ۵
    public static String getApiKey(Context context, String provider, int index) {
        String saved = prefs(context).getString("key_" + provider + "_" + index, "");
        if (!saved.isEmpty()) {
            return saved;
        }
        // کلیدهایی که قبلاً برای گوگل یا سرویس سفارشی با نام قدیمی ذخیره شده بودند
        if (PROVIDER_GOOGLE.equals(provider)) {
            return prefs(context).getString("api_key_" + index, "");
        }
        if (PROVIDER_CUSTOM.equals(provider)) {
            return prefs(context).getString("custom_api_key_" + index, "");
        }
        return "";
    }

    public static void setApiKey(Context context, String provider, int index, String value) {
        prefs(context).edit().putString("key_" + provider + "_" + index, value).apply();
    }

    public static void clearAll(Context context) {
        prefs(context).edit().clear().apply();
    }
}

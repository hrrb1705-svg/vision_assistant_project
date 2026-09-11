package com.example.visionassistant;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleHelper {
    public static Context wrap(Context context) {
        String languageCode = AppPrefs.getLanguage(context);
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }
}

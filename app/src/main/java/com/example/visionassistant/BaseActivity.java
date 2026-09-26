package com.example.visionassistant;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

// همهٔ صفحه‌های برنامه از این کلاس ارث می‌برند تا زبان به‌درستی روی همهٔ آنها اعمال شود.
// اگر کاربر زبان را در تنظیمات عوض کند و بعد به صفحه‌ای برگردد که از قبل باز بوده
// (مثلاً صفحهٔ اصلی که هیچ‌وقت بسته نمی‌شود)، آن صفحه با فراخوانی recreate() از نو ساخته
// می‌شود تا زبان تازه رویش هم اعمال شود.
public abstract class BaseActivity extends AppCompatActivity {

    private String appliedLanguage;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appliedLanguage = AppPrefs.getLanguage(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String currentLanguage = AppPrefs.getLanguage(this);
        if (appliedLanguage != null && !appliedLanguage.equals(currentLanguage)) {
            recreate();
        }
    }
}

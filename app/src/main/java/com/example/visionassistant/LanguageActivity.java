package com.example.visionassistant;

import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LanguageActivity extends AppCompatActivity {

    private RadioButton radioFarsi;
    private RadioButton radioEnglish;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language);

        radioFarsi = findViewById(R.id.radioFarsi);
        radioEnglish = findViewById(R.id.radioEnglish);

        if ("en".equals(AppPrefs.getLanguage(this))) {
            radioEnglish.setChecked(true);
        } else {
            radioFarsi.setChecked(true);
        }

        Button btnSave = findViewById(R.id.btnSaveLanguage);
        btnSave.setOnClickListener(v -> {
            AppPrefs.setLanguage(this, radioEnglish.isChecked() ? "en" : "fa");
            Toast.makeText(this, R.string.settings_saved_message, Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}

package com.example.visionassistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Button btnOpenLanguage = findViewById(R.id.btnOpenLanguage);
        Button btnOpenServices = findViewById(R.id.btnOpenServices);

        btnOpenLanguage.setOnClickListener(v ->
                startActivity(new Intent(this, LanguageActivity.class)));
        findViewById(R.id.btnOpenErrors).setOnClickListener(v ->
                startActivity(new Intent(this, ErrorLogActivity.class)));
        CheckBox skip = findViewById(R.id.chkSkipSummary);
        CheckBox full = findViewById(R.id.chkVideoFull);
        EditText edt = findViewById(R.id.edtInterval);
        skip.setChecked(AppPrefs.isSummarySkipped(this));
        full.setChecked(AppPrefs.isVideoFullMode(this));
        edt.setText(String.valueOf(AppPrefs.getVideoIntervalSec(this)));
        skip.setOnCheckedChangeListener((b, c) -> AppPrefs.setSummarySkipped(this, c));
        full.setOnCheckedChangeListener((b, c) -> AppPrefs.setVideoFullMode(this, c));
        edt.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) {
                try { AppPrefs.setVideoIntervalSec(SettingsActivity.this, Integer.parseInt(e.toString().trim())); } catch (Exception ignored) {}
            }
        });
        btnOpenServices.setOnClickListener(v ->
                startActivity(new Intent(this, ServicesActivity.class)));
    }
}

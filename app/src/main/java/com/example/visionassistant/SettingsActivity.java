package com.example.visionassistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Button btnOpenLanguage = findViewById(R.id.btnOpenLanguage);
        Button btnOpenServices = findViewById(R.id.btnOpenServices);

        btnOpenLanguage.setOnClickListener(v ->
                startActivity(new Intent(this, LanguageActivity.class)));
        btnOpenServices.setOnClickListener(v ->
                startActivity(new Intent(this, ServicesActivity.class)));
    }
}

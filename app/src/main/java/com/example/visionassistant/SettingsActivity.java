package com.example.visionassistant;

import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private RadioButton radioFarsi;
    private RadioButton radioEnglish;
    private RadioButton radioProviderGoogle;
    private RadioButton radioProviderCustom;
    private EditText editKey1, editKey2, editKey3, editKey4, editKey5;
    private EditText editCustomEndpoint, editCustomModel;
    private EditText editCustomKey1, editCustomKey2, editCustomKey3, editCustomKey4, editCustomKey5;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        RadioGroup radioGroupLanguage = findViewById(R.id.radioGroupLanguage);
        radioFarsi = findViewById(R.id.radioFarsi);
        radioEnglish = findViewById(R.id.radioEnglish);

        RadioGroup radioGroupProvider = findViewById(R.id.radioGroupProvider);
        radioProviderGoogle = findViewById(R.id.radioProviderGoogle);
        radioProviderCustom = findViewById(R.id.radioProviderCustom);

        editKey1 = findViewById(R.id.editKey1);
        editKey2 = findViewById(R.id.editKey2);
        editKey3 = findViewById(R.id.editKey3);
        editKey4 = findViewById(R.id.editKey4);
        editKey5 = findViewById(R.id.editKey5);

        editCustomEndpoint = findViewById(R.id.editCustomEndpoint);
        editCustomModel = findViewById(R.id.editCustomModel);
        editCustomKey1 = findViewById(R.id.editCustomKey1);
        editCustomKey2 = findViewById(R.id.editCustomKey2);
        editCustomKey3 = findViewById(R.id.editCustomKey3);
        editCustomKey4 = findViewById(R.id.editCustomKey4);
        editCustomKey5 = findViewById(R.id.editCustomKey5);

        if ("en".equals(AppPrefs.getLanguage(this))) {
            radioEnglish.setChecked(true);
        } else {
            radioFarsi.setChecked(true);
        }

        if (AppPrefs.PROVIDER_CUSTOM.equals(AppPrefs.getProvider(this))) {
            radioProviderCustom.setChecked(true);
        } else {
            radioProviderGoogle.setChecked(true);
        }

        editKey1.setText(AppPrefs.getApiKey(this, 1));
        editKey2.setText(AppPrefs.getApiKey(this, 2));
        editKey3.setText(AppPrefs.getApiKey(this, 3));
        editKey4.setText(AppPrefs.getApiKey(this, 4));
        editKey5.setText(AppPrefs.getApiKey(this, 5));

        editCustomEndpoint.setText(AppPrefs.getCustomEndpoint(this));
        editCustomModel.setText(AppPrefs.getCustomModel(this));
        editCustomKey1.setText(AppPrefs.getCustomApiKey(this, 1));
        editCustomKey2.setText(AppPrefs.getCustomApiKey(this, 2));
        editCustomKey3.setText(AppPrefs.getCustomApiKey(this, 3));
        editCustomKey4.setText(AppPrefs.getCustomApiKey(this, 4));
        editCustomKey5.setText(AppPrefs.getCustomApiKey(this, 5));

        Button btnSave = findViewById(R.id.btnSaveSettings);
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void saveSettings() {
        String languageCode = radioEnglish.isChecked() ? "en" : "fa";
        AppPrefs.setLanguage(this, languageCode);

        String provider = radioProviderCustom.isChecked()
                ? AppPrefs.PROVIDER_CUSTOM : AppPrefs.PROVIDER_GOOGLE;
        AppPrefs.setProvider(this, provider);

        AppPrefs.setApiKey(this, 1, editKey1.getText().toString().trim());
        AppPrefs.setApiKey(this, 2, editKey2.getText().toString().trim());
        AppPrefs.setApiKey(this, 3, editKey3.getText().toString().trim());
        AppPrefs.setApiKey(this, 4, editKey4.getText().toString().trim());
        AppPrefs.setApiKey(this, 5, editKey5.getText().toString().trim());

        AppPrefs.setCustomEndpoint(this, editCustomEndpoint.getText().toString().trim());
        AppPrefs.setCustomModel(this, editCustomModel.getText().toString().trim());
        AppPrefs.setCustomApiKey(this, 1, editCustomKey1.getText().toString().trim());
        AppPrefs.setCustomApiKey(this, 2, editCustomKey2.getText().toString().trim());
        AppPrefs.setCustomApiKey(this, 3, editCustomKey3.getText().toString().trim());
        AppPrefs.setCustomApiKey(this, 4, editCustomKey4.getText().toString().trim());
        AppPrefs.setCustomApiKey(this, 5, editCustomKey5.getText().toString().trim());

        Toast.makeText(this, R.string.settings_saved_message, Toast.LENGTH_SHORT).show();
        finish();
    }
}

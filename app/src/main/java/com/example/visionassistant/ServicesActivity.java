package com.example.visionassistant;

import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ServicesActivity extends AppCompatActivity {

    private RadioGroup radioGroupProvider;
    private RadioButton radioProviderGoogle;
    private RadioButton radioProviderCustom;
    private EditText editServiceName;
    private EditText editServiceEndpoint;
    private TextView labelKey1, labelKey2, labelKey3, labelKey4, labelKey5;
    private EditText editKey1, editKey2, editKey3, editKey4, editKey5;

    // بافر موقت برای اینکه وقتی کاربر بین گوگل و سرویس سفارشی جابه‌جا می‌شود
    // کلیدهایی که تازه تایپ کرده، قبل از ذخیره، از دست نروند
    private final String[] googleKeysBuffer = new String[5];
    private final String[] customKeysBuffer = new String[5];
    private String currentProvider;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_services);

        radioGroupProvider = findViewById(R.id.radioGroupProvider);
        radioProviderGoogle = findViewById(R.id.radioProviderGoogle);
        radioProviderCustom = findViewById(R.id.radioProviderCustom);
        editServiceName = findViewById(R.id.editServiceName);
        editServiceEndpoint = findViewById(R.id.editServiceEndpoint);

        labelKey1 = findViewById(R.id.labelKey1);
        labelKey2 = findViewById(R.id.labelKey2);
        labelKey3 = findViewById(R.id.labelKey3);
        labelKey4 = findViewById(R.id.labelKey4);
        labelKey5 = findViewById(R.id.labelKey5);
        editKey1 = findViewById(R.id.editKey1);
        editKey2 = findViewById(R.id.editKey2);
        editKey3 = findViewById(R.id.editKey3);
        editKey4 = findViewById(R.id.editKey4);
        editKey5 = findViewById(R.id.editKey5);

        for (int i = 1; i <= 5; i++) {
            googleKeysBuffer[i - 1] = AppPrefs.getApiKey(this, i);
            customKeysBuffer[i - 1] = AppPrefs.getCustomApiKey(this, i);
        }

        editServiceName.setText(AppPrefs.getCustomModel(this));
        editServiceEndpoint.setText(AppPrefs.getCustomEndpoint(this));

        currentProvider = AppPrefs.getProvider(this);
        if (AppPrefs.PROVIDER_CUSTOM.equals(currentProvider)) {
            radioProviderCustom.setChecked(true);
        } else {
            radioProviderGoogle.setChecked(true);
        }
        applyProviderUi(currentProvider);

        radioGroupProvider.setOnCheckedChangeListener((group, checkedId) -> {
            String newProvider = checkedId == R.id.radioProviderCustom
                    ? AppPrefs.PROVIDER_CUSTOM : AppPrefs.PROVIDER_GOOGLE;
            if (!newProvider.equals(currentProvider)) {
                saveFieldsIntoBuffer(currentProvider);
                currentProvider = newProvider;
                applyProviderUi(currentProvider);
            }
        });

        Button btnSave = findViewById(R.id.btnSaveServices);
        btnSave.setOnClickListener(v -> saveSettings());
    }

    // مقادیر فعلی پنج فیلد را در بافر همان سرویسی که تا الان انتخاب بود ذخیره می‌کند
    private void saveFieldsIntoBuffer(String provider) {
        String[] buffer = AppPrefs.PROVIDER_CUSTOM.equals(provider) ? customKeysBuffer : googleKeysBuffer;
        buffer[0] = editKey1.getText().toString();
        buffer[1] = editKey2.getText().toString();
        buffer[2] = editKey3.getText().toString();
        buffer[3] = editKey4.getText().toString();
        buffer[4] = editKey5.getText().toString();
    }

    // پنج فیلد و برچسب‌ها و فعال یا غیرفعال بودن نام/آدرس را با سرویس انتخاب‌شده هماهنگ می‌کند
    private void applyProviderUi(String provider) {
        boolean isCustom = AppPrefs.PROVIDER_CUSTOM.equals(provider);
        String[] buffer = isCustom ? customKeysBuffer : googleKeysBuffer;

        editKey1.setText(buffer[0]);
        editKey2.setText(buffer[1]);
        editKey3.setText(buffer[2]);
        editKey4.setText(buffer[3]);
        editKey5.setText(buffer[4]);

        int label1 = isCustom ? R.string.custom_key_label_1 : R.string.key_label_1;
        int label2 = isCustom ? R.string.custom_key_label_2 : R.string.key_label_2;
        int label3 = isCustom ? R.string.custom_key_label_3 : R.string.key_label_3;
        int label4 = isCustom ? R.string.custom_key_label_4 : R.string.key_label_4;
        int label5 = isCustom ? R.string.custom_key_label_5 : R.string.key_label_5;

        labelKey1.setText(label1);
        labelKey2.setText(label2);
        labelKey3.setText(label3);
        labelKey4.setText(label4);
        labelKey5.setText(label5);
        editKey1.setContentDescription(getString(label1));
        editKey2.setContentDescription(getString(label2));
        editKey3.setContentDescription(getString(label3));
        editKey4.setContentDescription(getString(label4));
        editKey5.setContentDescription(getString(label5));

        editServiceName.setEnabled(isCustom);
        editServiceEndpoint.setEnabled(isCustom);
    }

    private void saveSettings() {
        saveFieldsIntoBuffer(currentProvider);

        AppPrefs.setProvider(this, currentProvider);
        AppPrefs.setCustomModel(this, editServiceName.getText().toString().trim());
        AppPrefs.setCustomEndpoint(this, editServiceEndpoint.getText().toString().trim());

        for (int i = 0; i < 5; i++) {
            AppPrefs.setApiKey(this, i + 1, googleKeysBuffer[i] == null ? "" : googleKeysBuffer[i].trim());
            AppPrefs.setCustomApiKey(this, i + 1, customKeysBuffer[i] == null ? "" : customKeysBuffer[i].trim());
        }

        Toast.makeText(this, R.string.settings_saved_message, Toast.LENGTH_SHORT).show();
        finish();
    }
}

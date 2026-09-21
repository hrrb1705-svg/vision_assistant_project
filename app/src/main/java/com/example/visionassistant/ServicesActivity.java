package com.example.visionassistant;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ServicesActivity extends AppCompatActivity {

    private static final String[] PROVIDERS = {
            AppPrefs.PROVIDER_GOOGLE, AppPrefs.PROVIDER_GROQ, AppPrefs.PROVIDER_XAI, AppPrefs.PROVIDER_CUSTOM
    };

    private Spinner spinnerProvider;
    private EditText editServiceName;
    private EditText editServiceEndpoint;
    private TextView labelKey1, labelKey2, labelKey3, labelKey4, labelKey5;
    private EditText editKey1, editKey2, editKey3, editKey4, editKey5;

    // بافر موقت برای هر سه سرویس، تا وقتی کاربر بین آن‌ها جابه‌جا می‌شود، چیزی که تازه تایپ کرده از دست نرود
    private final String[][] keysBuffer = new String[PROVIDERS.length][5];
    private final String[] nameBuffer = new String[PROVIDERS.length];
    private final String[] endpointBuffer = new String[PROVIDERS.length];
    private int currentIndex = 0;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_services);

        spinnerProvider = findViewById(R.id.spinnerProvider);
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

        String[] labels = {
                getString(R.string.provider_google),
                getString(R.string.provider_groq),
                getString(R.string.provider_xai),
                getString(R.string.provider_custom)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProvider.setAdapter(adapter);

        for (int i = 0; i < PROVIDERS.length; i++) {
            String provider = PROVIDERS[i];
            nameBuffer[i] = AppPrefs.getModel(this, provider);
            endpointBuffer[i] = AppPrefs.getEndpoint(this, provider);
            for (int k = 0; k < 5; k++) {
                keysBuffer[i][k] = AppPrefs.getApiKey(this, provider, k + 1);
            }
        }

        String activeProvider = AppPrefs.getActiveProvider(this);
        currentIndex = 0;
        for (int i = 0; i < PROVIDERS.length; i++) {
            if (PROVIDERS[i].equals(activeProvider)) {
                currentIndex = i;
                break;
            }
        }
        spinnerProvider.setSelection(currentIndex);
        applyProviderUi(currentIndex);

        spinnerProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != currentIndex) {
                    saveFieldsIntoBuffer(currentIndex);
                    currentIndex = position;
                    applyProviderUi(currentIndex);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Button btnSave = findViewById(R.id.btnSaveServices);
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void saveFieldsIntoBuffer(int index) {
        nameBuffer[index] = editServiceName.getText().toString();
        endpointBuffer[index] = editServiceEndpoint.getText().toString();
        keysBuffer[index][0] = editKey1.getText().toString();
        keysBuffer[index][1] = editKey2.getText().toString();
        keysBuffer[index][2] = editKey3.getText().toString();
        keysBuffer[index][3] = editKey4.getText().toString();
        keysBuffer[index][4] = editKey5.getText().toString();
    }

    private void applyProviderUi(int index) {
        editServiceName.setText(nameBuffer[index]);
        editServiceEndpoint.setText(endpointBuffer[index]);
        editKey1.setText(keysBuffer[index][0]);
        editKey2.setText(keysBuffer[index][1]);
        editKey3.setText(keysBuffer[index][2]);
        editKey4.setText(keysBuffer[index][3]);
        editKey5.setText(keysBuffer[index][4]);

        labelKey1.setText(getString(R.string.key_label_1));
        labelKey2.setText(getString(R.string.key_label_2));
        labelKey3.setText(getString(R.string.key_label_3));
        labelKey4.setText(getString(R.string.key_label_4));
        labelKey5.setText(getString(R.string.key_label_5));
        editKey1.setContentDescription(getString(R.string.key_label_1));
        editKey2.setContentDescription(getString(R.string.key_label_2));
        editKey3.setContentDescription(getString(R.string.key_label_3));
        editKey4.setContentDescription(getString(R.string.key_label_4));
        editKey5.setContentDescription(getString(R.string.key_label_5));
    }

    private void saveSettings() {
        saveFieldsIntoBuffer(currentIndex);

        AppPrefs.setActiveProvider(this, PROVIDERS[currentIndex]);
        for (int i = 0; i < PROVIDERS.length; i++) {
            String provider = PROVIDERS[i];
            AppPrefs.setModel(this, provider, nameBuffer[i] == null ? "" : nameBuffer[i].trim());
            AppPrefs.setEndpoint(this, provider, endpointBuffer[i] == null ? "" : endpointBuffer[i].trim());
            for (int k = 0; k < 5; k++) {
                String value = keysBuffer[i][k] == null ? "" : keysBuffer[i][k].trim();
                AppPrefs.setApiKey(this, provider, k + 1, value);
            }
        }

        Toast.makeText(this, R.string.settings_saved_message, Toast.LENGTH_SHORT).show();
        finish();
    }
}

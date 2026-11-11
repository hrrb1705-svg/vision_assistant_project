package com.example.visionassistant;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServicesActivity extends BaseActivity {

    private static final String[] PROVIDERS = {
            AppPrefs.PROVIDER_GOOGLE, AppPrefs.PROVIDER_GROQ, AppPrefs.PROVIDER_XAI, AppPrefs.PROVIDER_CUSTOM
    };

    private Spinner spinnerProvider;
    private EditText editServiceName;
    private EditText editServiceEndpoint;
    private TextView labelKey1, labelKey2, labelKey3, labelKey4, labelKey5;
    private EditText editKey1, editKey2, editKey3, editKey4, editKey5;
    private Button btnTestKeys;
    private TextView tvTestStatus;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // بافر موقت برای هر سرویس، تا وقتی کاربر بین آن‌ها جابه‌جا می‌شود، چیزی که تازه تایپ کرده از دست نرود
    private final String[][] keysBuffer = new String[PROVIDERS.length][5];
    private final String[] nameBuffer = new String[PROVIDERS.length];
    private final String[] endpointBuffer = new String[PROVIDERS.length];
    private int currentIndex = 0;

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
        btnTestKeys = findViewById(R.id.btnTestKeys);
        tvTestStatus = findViewById(R.id.tvTestStatus);

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
                    tvTestStatus.setText("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Button btnSave = findViewById(R.id.btnSaveServices);
        btnSave.setOnClickListener(v -> saveSettings());
        btnTestKeys.setOnClickListener(v -> testKeys());
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

    // آدرس کامل درخواست: اگر کاربر آدرس کامل را نوشته همان، وگرنه از آدرس سرویس‌دهنده
    // (فقط برای گزینهٔ سایر) آدرس استاندارد چت ساخته می‌شود
    private String effectiveEndpoint(int index) {
        String endpoint = endpointBuffer[index] == null ? "" : endpointBuffer[index].trim();
        if (!endpoint.isEmpty()) {
            return endpoint;
        }
        return "";
    }

    // --- آزمایش همهٔ کلیدهای واردشده با مقادیر همین صفحه، حتی اگر هنوز ذخیره نشده باشند ---
    private void testKeys() {
        saveFieldsIntoBuffer(currentIndex);
        int index = currentIndex;
        String provider = PROVIDERS[index];
        String endpoint = effectiveEndpoint(index);
        String model = nameBuffer[index] == null ? "" : nameBuffer[index].trim();

        if (endpoint.isEmpty() || model.isEmpty()) {
            showDialog(getString(R.string.custom_not_configured));
            return;
        }

        List<Integer> numbers = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            String key = keysBuffer[index][k] == null ? "" : keysBuffer[index][k].trim();
            if (!key.isEmpty()) {
                numbers.add(k + 1);
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            showDialog(getString(R.string.no_keys_message));
            return;
        }

        btnTestKeys.setEnabled(false);
        tvTestStatus.setText(R.string.test_in_progress);

        executor.execute(() -> {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                String result = KeyTester.test(this, provider, endpoint, model, keys.get(i));
                if (!getString(R.string.test_key_ok).equals(result)) {
                    ErrorLog.add(getApplicationContext(), String.format(getString(R.string.key_status_line_format),
                            numbers.get(i), result));
                }
                lines.add(String.format(getString(R.string.key_status_line_format),
                        numbers.get(i), result));
            }
            String summary = String.join("\n", lines);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                btnTestKeys.setEnabled(true);
                tvTestStatus.setText(R.string.test_finished);
                showDialog(summary);
            });
        });
    }

    private void showDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.test_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.error_dialog_ok, null)
                .show();
    }

    private void saveSettings() {
        saveFieldsIntoBuffer(currentIndex);

        AppPrefs.setActiveProvider(this, PROVIDERS[currentIndex]);
        for (int i = 0; i < PROVIDERS.length; i++) {
            String provider = PROVIDERS[i];
            AppPrefs.setModel(this, provider, nameBuffer[i] == null ? "" : nameBuffer[i].trim());
            AppPrefs.setEndpoint(this, provider, effectiveEndpoint(i));
            for (int k = 0; k < 5; k++) {
                String value = keysBuffer[i][k] == null ? "" : keysBuffer[i][k].trim();
                AppPrefs.setApiKey(this, provider, k + 1, value);
            }
        }

        Toast.makeText(this, R.string.settings_saved_message, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

package com.example.visionassistant;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class ErrorLogActivity extends BaseActivity {

    private ListView listErrors;
    private TextView tvNoErrors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error_log);
        listErrors = findViewById(R.id.listErrors);
        tvNoErrors = findViewById(R.id.tvNoErrors);
        Button btnCopy = findViewById(R.id.btnCopyErrors);
        Button btnClear = findViewById(R.id.btnClearErrors);

        btnCopy.setOnClickListener(v -> {
            List<String> all = ErrorLog.getAll(this);
            if (all.isEmpty()) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("errors", String.join("\n\n", all)));
            Toast.makeText(this, R.string.errors_copied, Toast.LENGTH_SHORT).show();
        });
        btnClear.setOnClickListener(v -> {
            ErrorLog.clear(this);
            reload();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        List<String> all = ErrorLog.getAll(this);
        listErrors.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, all));
        tvNoErrors.setVisibility(all.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }
}

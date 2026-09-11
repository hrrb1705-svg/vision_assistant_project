package com.example.visionassistant;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1001;
    private static final String GEMINI_MODEL = "gemini-1.5-flash";
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent";

    private PreviewView viewFinder;
    private Button btnHelp;
    private Button btnMenu;
    private Button btnCaptureDescribe;
    private TextView tvStatus;
    private ImageCapture imageCapture;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        btnHelp = findViewById(R.id.btnHelp);
        btnMenu = findViewById(R.id.btnMenu);
        btnCaptureDescribe = findViewById(R.id.btnCaptureDescribe);
        tvStatus = findViewById(R.id.tvStatus);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    REQUEST_PERMISSIONS);
        } else {
            startCamera();
        }

        btnCaptureDescribe.setOnClickListener(v -> captureAndDescribe());
        btnHelp.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, HelpActivity.class)));
        btnMenu.setOnClickListener(this::showMainMenu);
    }

    private void showMainMenu(android.view.View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.main_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener((MenuItem item) -> {
            if (item.getItemId() == R.id.menu_settings) {
                startActivity(new android.content.Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                tvStatus.setText(R.string.permission_denied);
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture);
            } catch (Exception e) {
                tvStatus.setText(getString(R.string.error_camera_start_prefix) + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureAndDescribe() {
        if (imageCapture == null) {
            tvStatus.setText(R.string.camera_not_ready);
            return;
        }
        tvStatus.setText(R.string.capturing_image);
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        byte[] bytes = imageProxyToJpegBytes(image);
                        image.close();
                        sendToApi(bytes);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        tvStatus.setText(getString(R.string.error_capture_prefix) + exception.getMessage());
                    }
                });
    }

    private byte[] imageProxyToJpegBytes(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            java.nio.ByteBuffer buffer = planes[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                return bytes;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(R.string.error_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.error_dialog_ok, null)
                .show());
    }

    private void showResult(String text) {
        runOnUiThread(() -> tvStatus.setText(text));
    }

    private void sendToApi(byte[] imageBytes) {
        if (imageBytes.length == 0) {
            showError(getString(R.string.empty_image));
            return;
        }
        String provider = AppPrefs.getProvider(this);
        if (AppPrefs.PROVIDER_CUSTOM.equals(provider)) {
            sendToCustomProvider(imageBytes);
        } else {
            sendToGemini(imageBytes);
        }
    }

    // کلیدهای گوگل واردشده در تنظیمات را می‌خواند و خانه‌های خالی را کنار می‌گذارد
    private List<String> loadGoogleKeys() {
        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String key = AppPrefs.getApiKey(this, i);
            if (key != null && !key.trim().isEmpty()) {
                keys.add(key.trim());
            }
        }
        return keys;
    }

    private List<String> loadCustomKeys() {
        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String key = AppPrefs.getCustomApiKey(this, i);
            if (key != null && !key.trim().isEmpty()) {
                keys.add(key.trim());
            }
        }
        return keys;
    }

    private void sendToGemini(byte[] imageBytes) {
        List<String> apiKeys = loadGoogleKeys();
        if (apiKeys.isEmpty()) {
            showError(getString(R.string.no_keys_message));
            return;
        }
        executor.execute(() -> {
            String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            List<String> errorLines = new ArrayList<>();

            for (int i = 0; i < apiKeys.size(); i++) {
                String apiKey = apiKeys.get(i);
                try {
                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mime_type", "image/jpeg");
                    inlineData.put("data", base64);

                    JSONObject imagePart = new JSONObject();
                    imagePart.put("inline_data", inlineData);

                    JSONObject textPart = new JSONObject();
                    textPart.put("text", getString(R.string.prompt_describe));

                    JSONObject content = new JSONObject();
                    content.put("parts", new JSONArray().put(textPart).put(imagePart));

                    JSONObject body = new JSONObject();
                    body.put("contents", new JSONArray().put(content));

                    Request request = new Request.Builder()
                            .url(GEMINI_URL)
                            .header("x-goog-api-key", apiKey)
                            .header("Content-Type", "application/json")
                            .post(RequestBody.create(body.toString(),
                                    MediaType.parse("application/json")))
                            .build();

                    try (Response response = httpClient.newCall(request).execute()) {
                        String json = readResponseBody(response);

                        if (response.isSuccessful()) {
                            JSONObject root = new JSONObject(json);
                            String result = root.getJSONArray("candidates")
                                    .getJSONObject(0)
                                    .getJSONObject("content")
                                    .getJSONArray("parts")
                                    .getJSONObject(0)
                                    .getString("text");
                            showResult(result);
                            return;
                        }

                        errorLines.add(String.format(getString(R.string.key_status_line_format),
                                i + 1, describeHttpError(response.code())));
                    }
                } catch (Exception e) {
                    errorLines.add(String.format(getString(R.string.key_status_line_format),
                            i + 1, getString(R.string.generic_error_prefix) + e.getMessage()));
                }
            }

            showError(String.join("\n", errorLines));
        });
    }

    private void sendToCustomProvider(byte[] imageBytes) {
        String endpoint = AppPrefs.getCustomEndpoint(this);
        String model = AppPrefs.getCustomModel(this);
        if (endpoint == null || endpoint.trim().isEmpty()
                || model == null || model.trim().isEmpty()) {
            showError(getString(R.string.custom_not_configured));
            return;
        }
        List<String> apiKeys = loadCustomKeys();
        if (apiKeys.isEmpty()) {
            showError(getString(R.string.no_keys_message));
            return;
        }
        String finalEndpoint = endpoint.trim();
        String finalModel = model.trim();

        executor.execute(() -> {
            String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            List<String> errorLines = new ArrayList<>();

            for (int i = 0; i < apiKeys.size(); i++) {
                String apiKey = apiKeys.get(i);
                try {
                    // قالب سازگار با OpenAI (messages / content) که رایج‌ترین قالب بین سرویس‌دهنده‌های واسط است
                    JSONObject imageUrl = new JSONObject();
                    imageUrl.put("url", "data:image/jpeg;base64," + base64);

                    JSONObject imagePart = new JSONObject();
                    imagePart.put("type", "image_url");
                    imagePart.put("image_url", imageUrl);

                    JSONObject textPart = new JSONObject();
                    textPart.put("type", "text");
                    textPart.put("text", getString(R.string.prompt_describe));

                    JSONObject message = new JSONObject();
                    message.put("role", "user");
                    message.put("content", new JSONArray().put(textPart).put(imagePart));

                    JSONObject body = new JSONObject();
                    body.put("model", finalModel);
                    body.put("messages", new JSONArray().put(message));

                    Request request = new Request.Builder()
                            .url(finalEndpoint)
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .post(RequestBody.create(body.toString(),
                                    MediaType.parse("application/json")))
                            .build();

                    try (Response response = httpClient.newCall(request).execute()) {
                        String json = readResponseBody(response);

                        if (response.isSuccessful()) {
                            JSONObject root = new JSONObject(json);
                            String result = root.getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content");
                            showResult(result);
                            return;
                        }

                        errorLines.add(String.format(getString(R.string.key_status_line_format),
                                i + 1, describeHttpError(response.code())));
                    }
                } catch (Exception e) {
                    errorLines.add(String.format(getString(R.string.key_status_line_format),
                            i + 1, getString(R.string.generic_error_prefix) + e.getMessage()));
                }
            }

            showError(String.join("\n", errorLines));
        });
    }

    private String describeHttpError(int code) {
        if (code == 429) {
            return getString(R.string.quota_exceeded);
        }
        if (code == 401 || code == 403) {
            return getString(R.string.invalid_key);
        }
        return getString(R.string.server_error_prefix) + code;
    }

    private static String readResponseBody(Response response) throws Exception {
        InputStream is = response.body() != null ? response.body().byteStream() : null;
        return is != null ? new String(readAll(is), StandardCharsets.UTF_8) : "";
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = is.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

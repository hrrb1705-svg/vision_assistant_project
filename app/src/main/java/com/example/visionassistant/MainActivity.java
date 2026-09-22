package com.example.visionassistant;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

    private PreviewView viewFinder;
    private Button btnHelp;
    private Button btnMenu;
    private Button btnCaptureCamera;
    private Button btnChooseMedia;
    private Button btnExit;
    private TextView tvStatus;
    private View followUpRow;
    private EditText editQuestion;
    private Button btnSend;
    private Button btnSaveConversation;
    private ImageCapture imageCapture;
    private ActivityResultLauncher<String> pickMediaLauncher;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    // وضعیت گفتگوی جاری دربارهٔ یک تصویر؛ تا وقتی عکس تازه‌ای گرفته نشود همین‌جا می‌ماند
    private final List<ChatTurn> conversationTurns = new ArrayList<>();
    private byte[] conversationImageBytes = null;
    // اگر یک سوال پیگیری در حال ارسال باشد، متن آن اینجا نگه داشته می‌شود تا اگر شکست خورد به کاربر برگردانده شود
    private String pendingFollowUpQuestion = null;

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
        btnCaptureCamera = findViewById(R.id.btnCaptureCamera);
        btnChooseMedia = findViewById(R.id.btnChooseMedia);
        btnExit = findViewById(R.id.btnExit);
        tvStatus = findViewById(R.id.tvStatus);
        followUpRow = findViewById(R.id.followUpRow);
        editQuestion = findViewById(R.id.editQuestion);
        btnSend = findViewById(R.id.btnSend);
        btnSaveConversation = findViewById(R.id.btnSaveConversation);

        pickMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onMediaPicked);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    REQUEST_PERMISSIONS);
        } else {
            startCamera();
        }

        btnCaptureCamera.setOnClickListener(v -> captureAndDescribe());
        btnChooseMedia.setOnClickListener(v -> pickMediaLauncher.launch("image/*"));
        btnExit.setOnClickListener(v -> confirmExit());
        btnHelp.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, HelpActivity.class)));
        btnMenu.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, MenuActivity.class)));
        btnSend.setOnClickListener(v -> sendFollowUpQuestion());
        btnSaveConversation.setOnClickListener(v -> saveCurrentConversation());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshReadyState();
    }

    // بر اساس اینکه سرویس فعال، آدرس و مدل و حداقل یک کلید دارد یا نه، دکمه‌های گرفتن/انتخاب عکس را فعال یا غیرفعال می‌کند
    private void refreshReadyState() {
        String provider = AppPrefs.getActiveProvider(this);
        String endpoint = AppPrefs.getEndpoint(this, provider);
        String model = AppPrefs.getModel(this, provider);
        boolean hasKey = !loadKeys(provider).isEmpty();

        boolean ready = endpoint != null && !endpoint.trim().isEmpty()
                && model != null && !model.trim().isEmpty()
                && hasKey;

        btnCaptureCamera.setEnabled(ready);
        btnChooseMedia.setEnabled(ready);

        // اگر یک گفتگو از قبل روی صفحه است، آن را با پیام آماده به کار جایگزین نمی‌کنیم
        if (conversationTurns.isEmpty()) {
            if (!ready) {
                if (!hasKey) {
                    tvStatus.setText(R.string.no_keys_message);
                } else {
                    tvStatus.setText(R.string.custom_not_configured);
                }
            } else {
                tvStatus.setText(R.string.status_ready);
            }
        }

        showFollowUpUi(!conversationTurns.isEmpty());
    }

    private void showFollowUpUi(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        followUpRow.setVisibility(visibility);
        btnSaveConversation.setVisibility(visibility);
    }

    // کلید خروج فقط از برنامه خارج می‌شود؛ هیچ کلید یا تنظیماتی پاک نمی‌شود
    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_exit_title)
                .setMessage(R.string.confirm_exit_message)
                .setPositiveButton(R.string.confirm_yes, (dialog, which) -> finishAffinity())
                .setNegativeButton(R.string.confirm_no, null)
                .show();
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
                        startNewConversation(bytes);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        tvStatus.setText(getString(R.string.error_capture_prefix) + exception.getMessage());
                    }
                });
    }

    private void onMediaPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        tvStatus.setText(R.string.capturing_image);
        executor.execute(() -> {
            byte[] bytes = fileUriToJpegBytes(uri);
            startNewConversation(bytes);
        });
    }

    private byte[] fileUriToJpegBytes(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return new byte[0];
            }
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap == null) {
                return new byte[0];
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
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

    // --- شروع یک گفتگوی تازه با یک عکس تازه ---
    private void startNewConversation(byte[] imageBytes) {
        if (imageBytes.length == 0) {
            showError(getString(R.string.empty_image));
            runOnUiThread(this::refreshReadyState);
            return;
        }
        conversationTurns.clear();
        conversationImageBytes = imageBytes;
        conversationTurns.add(new ChatTurn("user", getString(R.string.prompt_describe), true));
        pendingFollowUpQuestion = null;

        runOnUiThread(() -> {
            tvStatus.setText(R.string.capturing_image);
            setConversationBusy(true);
        });

        sendConversation();
    }

    // --- ارسال یک سوال پیگیری دربارهٔ همان عکس ---
    private void sendFollowUpQuestion() {
        String question = editQuestion.getText().toString().trim();
        if (question.isEmpty() || conversationImageBytes == null) {
            return;
        }
        conversationTurns.add(new ChatTurn("user", question, false));
        pendingFollowUpQuestion = question;
        editQuestion.setText("");

        tvStatus.setText(renderConversation() + "\n\n" + getString(R.string.sending_followup_suffix));
        setConversationBusy(true);

        sendConversation();
    }

    private void setConversationBusy(boolean busy) {
        btnSend.setEnabled(!busy);
        editQuestion.setEnabled(!busy);
        btnCaptureCamera.setEnabled(!busy);
        btnChooseMedia.setEnabled(!busy);
        btnSaveConversation.setEnabled(!busy && !conversationTurns.isEmpty());
    }

    // متن قابل‌نمایش کل گفتگو؛ اولین پیام کاربر (دستور پیش‌فرض توصیف تصویر) نشان داده نمی‌شود
    private String renderConversation() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < conversationTurns.size(); i++) {
            ChatTurn t = conversationTurns.get(i);
            if (t.hasImage) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            if ("assistant".equals(t.role)) {
                sb.append(getString(R.string.chat_assistant_prefix)).append(t.text);
            } else {
                sb.append(getString(R.string.chat_you_prefix)).append(t.text);
            }
        }
        return sb.toString();
    }

    private void onConversationReply(String replyText) {
        runOnUiThread(() -> {
            conversationTurns.add(new ChatTurn("assistant", replyText, false));
            pendingFollowUpQuestion = null;
            tvStatus.setText(renderConversation());
            setConversationBusy(false);
            showFollowUpUi(true);
        });
    }

    private void onConversationFailed(String joinedErrors) {
        runOnUiThread(() -> {
            if (pendingFollowUpQuestion != null && !conversationTurns.isEmpty()) {
                // این شکست مربوط به یک سوال پیگیری بود؛ همان سوال را برمی‌گردانیم تا دوباره فرستاده شود
                conversationTurns.remove(conversationTurns.size() - 1);
                editQuestion.setText(pendingFollowUpQuestion);
                tvStatus.setText(renderConversation());
            } else {
                // این شکست مربوط به توصیف اولیهٔ تصویر بود؛ کل گفتگو را کنار می‌گذاریم
                conversationTurns.clear();
                conversationImageBytes = null;
            }
            pendingFollowUpQuestion = null;
            setConversationBusy(false);
            refreshReadyState();
            showError(joinedErrors);
        });
    }

    private void saveCurrentConversation() {
        if (conversationImageBytes == null || conversationTurns.isEmpty()) {
            return;
        }
        List<ChatTurn> snapshot = new ArrayList<>(conversationTurns);
        byte[] imageSnapshot = conversationImageBytes;
        executor.execute(() -> {
            try {
                ConversationStore.saveNewConversation(this, imageSnapshot, snapshot);
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.conversation_saved, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                showError(getString(R.string.save_failed_prefix) + e.getMessage());
            }
        });
    }

    // کلیدهای واردشده برای یک سرویس مشخص را می‌خواند و خانه‌های خالی را کنار می‌گذارد
    private List<String> loadKeys(String provider) {
        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String key = AppPrefs.getApiKey(this, provider, i);
            if (key != null && !key.trim().isEmpty()) {
                keys.add(key.trim());
            }
        }
        return keys;
    }

    // بر اساس سرویس فعال، درخواست کل گفتگو (شامل تاریخچه) را با قالب همان سرویس می‌فرستد
    private void sendConversation() {
        String provider = AppPrefs.getActiveProvider(this);
        if (AppPrefs.PROVIDER_GOOGLE.equals(provider)) {
            sendGeminiConversation();
        } else if (AppPrefs.PROVIDER_XAI.equals(provider)) {
            sendXaiConversation();
        } else {
            sendOpenAiCompatibleConversation(provider);
        }
    }

    private void sendGeminiConversation() {
        List<String> apiKeys = loadKeys(AppPrefs.PROVIDER_GOOGLE);
        if (apiKeys.isEmpty()) {
            onConversationFailed(getString(R.string.no_keys_message));
            return;
        }
        String endpointBase = AppPrefs.getEndpoint(this, AppPrefs.PROVIDER_GOOGLE);
        String model = AppPrefs.getModel(this, AppPrefs.PROVIDER_GOOGLE);
        if (endpointBase.endsWith("/")) {
            endpointBase = endpointBase.substring(0, endpointBase.length() - 1);
        }
        String url = endpointBase + "/models/" + model + ":generateContent";
        String finalUrl = url;
        List<ChatTurn> turnsSnapshot = new ArrayList<>(conversationTurns);
        byte[] imageBytes = conversationImageBytes;

        executor.execute(() -> {
            String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            List<String> errorLines = new ArrayList<>();

            for (int i = 0; i < apiKeys.size(); i++) {
                String apiKey = apiKeys.get(i);
                try {
                    JSONArray contents = new JSONArray();
                    for (ChatTurn turn : turnsSnapshot) {
                        JSONArray parts = new JSONArray();
                        parts.put(new JSONObject().put("text", turn.text));
                        if (turn.hasImage) {
                            JSONObject inlineData = new JSONObject();
                            inlineData.put("mime_type", "image/jpeg");
                            inlineData.put("data", base64);
                            parts.put(new JSONObject().put("inline_data", inlineData));
                        }
                        JSONObject content = new JSONObject();
                        content.put("role", "assistant".equals(turn.role) ? "model" : "user");
                        content.put("parts", parts);
                        contents.put(content);
                    }

                    JSONObject body = new JSONObject();
                    body.put("contents", contents);

                    Request request = new Request.Builder()
                            .url(finalUrl)
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
                            onConversationReply(result);
                            return;
                        }

                        errorLines.add(String.format(getString(R.string.key_status_line_format),
                                i + 1, describeHttpError(response.code(), json)));
                    }
                } catch (Exception e) {
                    errorLines.add(String.format(getString(R.string.key_status_line_format),
                            i + 1, getString(R.string.generic_error_prefix) + e.getMessage()));
                }
            }

            onConversationFailed(String.join("\n", errorLines));
        });
    }

    // قالب سازگار با OpenAI (messages / content)، برای Groq و هر سرویس دیگری که همین قالب رایج را پشتیبانی کند
    private void sendOpenAiCompatibleConversation(String provider) {
        String endpoint = AppPrefs.getEndpoint(this, provider);
        String model = AppPrefs.getModel(this, provider);
        if (endpoint == null || endpoint.trim().isEmpty()
                || model == null || model.trim().isEmpty()) {
            onConversationFailed(getString(R.string.custom_not_configured));
            return;
        }
        List<String> apiKeys = loadKeys(provider);
        if (apiKeys.isEmpty()) {
            onConversationFailed(getString(R.string.no_keys_message));
            return;
        }
        String finalEndpoint = endpoint.trim();
        String finalModel = model.trim();
        List<ChatTurn> turnsSnapshot = new ArrayList<>(conversationTurns);
        byte[] imageBytes = conversationImageBytes;

        executor.execute(() -> {
            String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            List<String> errorLines = new ArrayList<>();

            for (int i = 0; i < apiKeys.size(); i++) {
                String apiKey = apiKeys.get(i);
                try {
                    JSONArray messages = new JSONArray();
                    for (ChatTurn turn : turnsSnapshot) {
                        JSONObject message = new JSONObject();
                        message.put("role", "assistant".equals(turn.role) ? "assistant" : "user");
                        if (turn.hasImage) {
                            JSONObject imageUrl = new JSONObject();
                            imageUrl.put("url", "data:image/jpeg;base64," + base64);

                            JSONObject imagePart = new JSONObject();
                            imagePart.put("type", "image_url");
                            imagePart.put("image_url", imageUrl);

                            JSONObject textPart = new JSONObject();
                            textPart.put("type", "text");
                            textPart.put("text", turn.text);

                            message.put("content", new JSONArray().put(textPart).put(imagePart));
                        } else {
                            message.put("content", turn.text);
                        }
                        messages.put(message);
                    }

                    JSONObject body = new JSONObject();
                    body.put("model", finalModel);
                    body.put("messages", messages);

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
                            onConversationReply(result);
                            return;
                        }

                        errorLines.add(String.format(getString(R.string.key_status_line_format),
                                i + 1, describeHttpError(response.code(), json)));
                    }
                } catch (Exception e) {
                    errorLines.add(String.format(getString(R.string.key_status_line_format),
                            i + 1, getString(R.string.generic_error_prefix) + e.getMessage()));
                }
            }

            onConversationFailed(String.join("\n", errorLines));
        });
    }

    // قالب Responses API که Xai/Grok از آن استفاده می‌کند؛ با قالب OpenAI معمول (messages) فرق دارد
    private void sendXaiConversation() {
        String provider = AppPrefs.PROVIDER_XAI;
        String endpoint = AppPrefs.getEndpoint(this, provider);
        String model = AppPrefs.getModel(this, provider);
        if (endpoint == null || endpoint.trim().isEmpty()
                || model == null || model.trim().isEmpty()) {
            onConversationFailed(getString(R.string.custom_not_configured));
            return;
        }
        List<String> apiKeys = loadKeys(provider);
        if (apiKeys.isEmpty()) {
            onConversationFailed(getString(R.string.no_keys_message));
            return;
        }
        String finalEndpoint = endpoint.trim();
        String finalModel = model.trim();
        List<ChatTurn> turnsSnapshot = new ArrayList<>(conversationTurns);
        byte[] imageBytes = conversationImageBytes;

        executor.execute(() -> {
            String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            List<String> errorLines = new ArrayList<>();

            for (int i = 0; i < apiKeys.size(); i++) {
                String apiKey = apiKeys.get(i);
                try {
                    JSONArray input = new JSONArray();
                    for (ChatTurn turn : turnsSnapshot) {
                        boolean isAssistant = "assistant".equals(turn.role);
                        JSONObject textPart = new JSONObject();
                        textPart.put("type", isAssistant ? "output_text" : "input_text");
                        textPart.put("text", turn.text);

                        JSONArray content = new JSONArray().put(textPart);
                        if (turn.hasImage) {
                            JSONObject imagePart = new JSONObject();
                            imagePart.put("type", "input_image");
                            imagePart.put("image_url", "data:image/jpeg;base64," + base64);
                            content.put(imagePart);
                        }

                        JSONObject message = new JSONObject();
                        message.put("role", isAssistant ? "assistant" : "user");
                        message.put("content", content);
                        input.put(message);
                    }

                    JSONObject body = new JSONObject();
                    body.put("model", finalModel);
                    body.put("input", input);

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
                            String result = extractResponsesApiText(new JSONObject(json));
                            onConversationReply(result);
                            return;
                        }

                        errorLines.add(String.format(getString(R.string.key_status_line_format),
                                i + 1, describeHttpError(response.code(), json)));
                    }
                } catch (Exception e) {
                    errorLines.add(String.format(getString(R.string.key_status_line_format),
                            i + 1, getString(R.string.generic_error_prefix) + e.getMessage()));
                }
            }

            onConversationFailed(String.join("\n", errorLines));
        });
    }

    // خروجی Responses API یک ساختار output_text ساده یا آرایه output دارد؛ هر دو حالت را پوشش می‌دهیم
    private static String extractResponsesApiText(JSONObject root) throws Exception {
        if (root.has("output_text") && !root.isNull("output_text")) {
            String direct = root.optString("output_text", "");
            if (!direct.trim().isEmpty()) {
                return direct;
            }
        }
        StringBuilder combined = new StringBuilder();
        if (root.has("output")) {
            JSONArray output = root.getJSONArray("output");
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.getJSONObject(i);
                if (!item.has("content")) {
                    continue;
                }
                JSONArray content = item.getJSONArray("content");
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.getJSONObject(j);
                    if (part.has("text")) {
                        if (combined.length() > 0) {
                            combined.append("\n");
                        }
                        combined.append(part.getString("text"));
                    }
                }
            }
        }
        if (combined.length() > 0) {
            return combined.toString();
        }
        throw new Exception("unexpected response shape");
    }

    private String describeHttpError(int code, String rawJson) {
        String trimmedBody = rawJson == null ? "" : rawJson.trim();
        boolean looksLikeHtmlBlockPage = trimmedBody.startsWith("<")
                || trimmedBody.toLowerCase(java.util.Locale.ROOT).contains("<html");

        String base;
        if (looksLikeHtmlBlockPage && (code == 403 || code == 429 || code == 401)) {
            base = getString(R.string.blocked_by_network);
        } else if (code == 429) {
            base = getString(R.string.quota_exceeded);
        } else if (code == 401 || code == 403) {
            base = getString(R.string.invalid_key);
        } else {
            base = getString(R.string.server_error_prefix) + code;
        }
        String detail = extractServerErrorMessage(rawJson);
        if (detail == null || detail.trim().isEmpty()) {
            detail = rawJson;
        }
        detail = truncate(detail == null ? "" : detail.trim(), 250);
        if (!detail.isEmpty()) {
            return base + " - " + detail;
        }
        return base;
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }

    private static String extractServerErrorMessage(String rawJson) {
        try {
            JSONObject root = new JSONObject(rawJson);
            if (root.has("error")) {
                Object errorField = root.get("error");
                if (errorField instanceof JSONObject) {
                    JSONObject errorObj = (JSONObject) errorField;
                    if (errorObj.has("message")) {
                        return errorObj.getString("message");
                    }
                } else if (errorField instanceof String) {
                    return (String) errorField;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
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

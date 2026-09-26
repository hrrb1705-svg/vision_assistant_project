package com.example.visionassistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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

public class MainActivity extends BaseActivity {

    // برای دریافت یک گفتگوی ادامه‌یافته از صفحهٔ ConversationViewActivity
    public static final String EXTRA_CONTINUE_IMAGE_PATH = "continue_image_path";
    public static final String EXTRA_CONTINUE_CONTEXT_JSON = "continue_context_json";
    public static final String EXTRA_CONTINUE_QUESTION = "continue_question";

    private Button btnHelp;
    private Button btnMenu;
    private Button btnAttach;
    private Button btnExit;
    private TextView tvStatus;
    private ListView listTurns;
    private ArrayAdapter<String> turnsAdapter;
    private EditText editQuestion;
    private Button btnSend;
    private Button btnSaveConversation;
    private ActivityResultLauncher<Intent> attachmentLauncher;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    // وضعیت گفتگوی جاری دربارهٔ یک تصویر؛ تا وقتی گفتگوی تازه‌ای شروع نشود همین‌جا می‌ماند
    private final List<ChatTurn> conversationTurns = new ArrayList<>();
    private byte[] conversationImageBytes = null;
    // تصویری که با کلید پیوست‌ها انتخاب شده ولی هنوز گفتگویی با آن شروع نشده
    private byte[] pendingAttachedImageBytes = null;
    // اگر یک سوال پیگیری در حال ارسال باشد، متن آن اینجا نگه داشته می‌شود تا اگر شکست خورد به کاربر برگردانده شود
    private String pendingFollowUpQuestion = null;
    private boolean isBusy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnHelp = findViewById(R.id.btnHelp);
        btnMenu = findViewById(R.id.btnMenu);
        btnAttach = findViewById(R.id.btnAttach);
        btnExit = findViewById(R.id.btnExit);
        tvStatus = findViewById(R.id.tvStatus);
        listTurns = findViewById(R.id.listTurns);
        editQuestion = findViewById(R.id.editQuestion);
        btnSend = findViewById(R.id.btnSend);
        btnSaveConversation = findViewById(R.id.btnSaveConversation);

        turnsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listTurns.setAdapter(turnsAdapter);

        attachmentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onAttachmentResult);

        btnAttach.setOnClickListener(v ->
                attachmentLauncher.launch(new Intent(this, AttachmentActivity.class)));
        btnExit.setOnClickListener(v -> confirmExit());
        btnHelp.setOnClickListener(v ->
                startActivity(new Intent(this, HelpActivity.class)));
        btnMenu.setOnClickListener(v ->
                startActivity(new Intent(this, MenuActivity.class)));
        btnSend.setOnClickListener(v -> onSendClicked());
        btnSaveConversation.setOnClickListener(v -> saveCurrentConversation());

        editQuestion.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                updateSendEnabled();
            }
        });

        handleContinueIntentIfPresent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshReadyState();
    }

    // --- دریافت یک گفتگوی ادامه‌یافته از صفحهٔ گفتگوهای ذخیره‌شده ---
    private void handleContinueIntentIfPresent(Intent intent) {
        if (intent == null) {
            return;
        }
        String imagePath = intent.getStringExtra(EXTRA_CONTINUE_IMAGE_PATH);
        String question = intent.getStringExtra(EXTRA_CONTINUE_QUESTION);
        String contextJson = intent.getStringExtra(EXTRA_CONTINUE_CONTEXT_JSON);
        if (imagePath == null || question == null) {
            return;
        }

        executor.execute(() -> {
            byte[] imageBytes = readFileBytes(imagePath);
            if (imageBytes.length == 0) {
                runOnUiThread(() -> showError(getString(R.string.empty_image)));
                return;
            }

            List<ChatTurn> contextTurns = new ArrayList<>();
            try {
                if (contextJson != null) {
                    JSONArray arr = new JSONArray(contextJson);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject m = arr.getJSONObject(i);
                        contextTurns.add(new ChatTurn(m.getString("role"), m.getString("text"), false));
                    }
                }
            } catch (Exception ignored) {
            }

            runOnUiThread(() -> {
                conversationTurns.clear();
                conversationImageBytes = imageBytes;
                pendingAttachedImageBytes = null;
                conversationTurns.add(new ChatTurn("user", getString(R.string.prompt_describe), true));
                conversationTurns.addAll(contextTurns);
                conversationTurns.add(new ChatTurn("user", question, false));
                pendingFollowUpQuestion = question;

                refreshTurnsList();
                tvStatus.setText(getString(R.string.sending_followup_suffix));
                setConversationBusy(true);
                sendConversation();
            });
        });
    }

    // بر اساس اینکه سرویس فعال، آدرس و مدل و حداقل یک کلید دارد یا نه، کلید پیوست‌ها را فعال یا غیرفعال می‌کند
    private boolean isServiceReady() {
        String provider = AppPrefs.getActiveProvider(this);
        String endpoint = AppPrefs.getEndpoint(this, provider);
        String model = AppPrefs.getModel(this, provider);
        boolean hasKey = !loadKeys(provider).isEmpty();
        return endpoint != null && !endpoint.trim().isEmpty()
                && model != null && !model.trim().isEmpty()
                && hasKey;
    }

    private void refreshReadyState() {
        boolean ready = isServiceReady();
        btnAttach.setEnabled(ready && !isBusy);

        if (conversationTurns.isEmpty() && pendingAttachedImageBytes == null) {
            if (!ready) {
                boolean hasKey = !loadKeys(AppPrefs.getActiveProvider(this)).isEmpty();
                tvStatus.setText(hasKey ? R.string.custom_not_configured : R.string.no_keys_message);
            } else {
                tvStatus.setText(R.string.status_ready);
            }
        }

        updateSendEnabled();
    }

    private void updateSendEnabled() {
        boolean hasQuestion = !editQuestion.getText().toString().trim().isEmpty();
        boolean enabled;
        if (!conversationTurns.isEmpty()) {
            enabled = hasQuestion && !isBusy;
        } else {
            enabled = pendingAttachedImageBytes != null && !isBusy;
        }
        btnSend.setEnabled(enabled);
        btnSaveConversation.setVisibility(
                (!conversationTurns.isEmpty() && conversationImageBytes != null) ? View.VISIBLE : View.GONE);
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

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.error_dialog_ok, null)
                .show();
    }

    // --- نتیجهٔ صفحهٔ پیوست‌ها: یا عکس گرفته شده یا فایلی انتخاب شده ---
    private void onAttachmentResult(androidx.activity.result.ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            return;
        }
        String path = result.getData().getStringExtra(AttachmentActivity.RESULT_EXTRA_PATH);
        if (path == null) {
            return;
        }
        executor.execute(() -> {
            byte[] bytes = readFileBytes(path);
            runOnUiThread(() -> onImageAttached(bytes));
        });
    }

    private void onImageAttached(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            showError(getString(R.string.empty_image));
            return;
        }
        if (!conversationTurns.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.warn_replace_conversation_title)
                    .setMessage(R.string.warn_replace_conversation_message)
                    .setPositiveButton(R.string.confirm_yes, (d, w) -> {
                        resetToReadyState();
                        setPendingImage(bytes);
                    })
                    .setNegativeButton(R.string.confirm_no, null)
                    .show();
        } else {
            setPendingImage(bytes);
        }
    }

    private void setPendingImage(byte[] bytes) {
        pendingAttachedImageBytes = bytes;
        tvStatus.setText(R.string.image_attached_status);
        updateSendEnabled();
    }

    // --- کلید ارسال: یا گفتگوی تازه شروع می‌کند یا سوال پیگیری می‌فرستد ---
    private void onSendClicked() {
        String question = editQuestion.getText().toString().trim();
        if (conversationTurns.isEmpty()) {
            if (pendingAttachedImageBytes == null) {
                showError(getString(R.string.need_image_first));
                return;
            }
            byte[] imageBytes = pendingAttachedImageBytes;
            pendingAttachedImageBytes = null;
            startNewConversation(imageBytes, question);
        } else {
            if (question.isEmpty()) {
                return;
            }
            sendFollowUpQuestion(question);
        }
    }

    // --- شروع یک گفتگوی تازه با یک عکس تازه و سوال اختیاری کاربر ---
    private void startNewConversation(byte[] imageBytes, String questionText) {
        if (imageBytes.length == 0) {
            showError(getString(R.string.empty_image));
            refreshReadyState();
            return;
        }
        conversationTurns.clear();
        conversationImageBytes = imageBytes;
        String firstText = questionText.isEmpty() ? getString(R.string.prompt_describe) : questionText;
        conversationTurns.add(new ChatTurn("user", firstText, true));
        pendingFollowUpQuestion = null;
        editQuestion.setText("");

        refreshTurnsList();
        tvStatus.setText(R.string.capturing_image);
        setConversationBusy(true);

        sendConversation();
    }

    // --- ارسال یک سوال پیگیری دربارهٔ همان عکس ---
    private void sendFollowUpQuestion(String question) {
        if (question.isEmpty() || conversationImageBytes == null) {
            return;
        }
        conversationTurns.add(new ChatTurn("user", question, false));
        pendingFollowUpQuestion = question;
        editQuestion.setText("");

        refreshTurnsList();
        tvStatus.setText(R.string.sending_followup_suffix);
        setConversationBusy(true);

        sendConversation();
    }

    private void setConversationBusy(boolean busy) {
        isBusy = busy;
        btnSend.setEnabled(false);
        editQuestion.setEnabled(!busy);
        btnAttach.setEnabled(!busy && isServiceReady());
        btnSaveConversation.setEnabled(!busy && !conversationTurns.isEmpty());
        if (!busy) {
            updateSendEnabled();
        }
    }

    // هر سوال و هر پاسخ به‌صورت یک ردیف جدا در فهرست نمایش داده می‌شود تا صفحه‌خوان بین آنها مکث کند؛
    // اولین پیام کاربر فقط وقتی نشان داده می‌شود که خودش یک سوال واقعی بوده، نه دستور پیش‌فرض توصیف تصویر
    private void refreshTurnsList() {
        List<String> lines = new ArrayList<>();
        String defaultPrompt = getString(R.string.prompt_describe);
        for (ChatTurn t : conversationTurns) {
            if (t.hasImage && defaultPrompt.equals(t.text)) {
                continue;
            }
            String prefix = "assistant".equals(t.role)
                    ? getString(R.string.chat_assistant_prefix)
                    : getString(R.string.chat_you_prefix);
            lines.add(prefix + t.text);
        }
        turnsAdapter.clear();
        turnsAdapter.addAll(lines);
        turnsAdapter.notifyDataSetChanged();
        updateSendEnabled();
    }

    private void onConversationReply(String replyText) {
        runOnUiThread(() -> {
            conversationTurns.add(new ChatTurn("assistant", replyText, false));
            pendingFollowUpQuestion = null;
            refreshTurnsList();
            tvStatus.setText(R.string.status_ready);
            setConversationBusy(false);
        });
    }

    private void onConversationFailed(String joinedErrors) {
        runOnUiThread(() -> {
            if (pendingFollowUpQuestion != null && !conversationTurns.isEmpty()) {
                // این شکست مربوط به یک سوال پیگیری بود؛ همان سوال را برمی‌گردانیم تا دوباره فرستاده شود
                conversationTurns.remove(conversationTurns.size() - 1);
                editQuestion.setText(pendingFollowUpQuestion);
                refreshTurnsList();
                tvStatus.setText(R.string.status_ready);
            } else {
                // این شکست مربوط به توصیف اولیهٔ تصویر بود؛ کل گفتگو را کنار می‌گذاریم
                conversationTurns.clear();
                conversationImageBytes = null;
                refreshTurnsList();
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
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.conversation_saved, Toast.LENGTH_SHORT).show();
                    resetToReadyState();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(getString(R.string.save_failed_prefix) + e.getMessage()));
            }
        });
    }

    // بعد از ذخیره، سیستم را برای شروع یک گفتگوی تازه آماده می‌کند
    private void resetToReadyState() {
        conversationTurns.clear();
        conversationImageBytes = null;
        pendingAttachedImageBytes = null;
        pendingFollowUpQuestion = null;
        editQuestion.setText("");
        refreshTurnsList();
        refreshReadyState();
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

    private static byte[] readFileBytes(String path) {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
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

package com.example.visionassistant;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// یک درخواست متنی بسیار کوتاه (بدون تصویر) به سرویس انتخاب‌شده می‌فرستد تا معلوم شود
// آدرس، نام مدل و کلید درست کار می‌کنند. خروجی یک جملهٔ کوتاه فارسی یا انگلیسی است.
public class KeyTester {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    // متن نتیجه برای یک کلید؛ اگر کلید سالم باشد پیام موفقیت برمی‌گردد
    public static String test(Context ctx, String provider, String endpoint, String model, String apiKey) {
        try {
            Request request;
            if (AppPrefs.PROVIDER_GOOGLE.equals(provider)) {
                request = buildGemini(endpoint, model, apiKey);
            } else if (AppPrefs.PROVIDER_XAI.equals(provider)) {
                request = buildXai(endpoint, model, apiKey);
            } else {
                request = buildOpenAi(endpoint, model, apiKey);
            }

            Exception last = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                try (Response response = CLIENT.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        return ctx.getString(R.string.test_key_ok);
                    }
                    if (response.code() >= 500 && attempt < 2) { Thread.sleep(1500); continue; }
                    return describeHttpError(ctx, response.code(), body);
                } catch (java.io.IOException io) {
                    last = io;
                    if (attempt < 2) Thread.sleep(2000);
                }
            }
            return ctx.getString(R.string.generic_error_prefix) + (last != null ? last.getMessage() : "");
        } catch (Exception e) {
            return ctx.getString(R.string.generic_error_prefix) + e.getMessage();
        }
    }

    private static Request buildGemini(String endpoint, String model, String apiKey) throws Exception {
        String base = endpoint.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        JSONObject part = new JSONObject().put("text", "Hi");
        JSONObject content = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(part));
        JSONObject body = new JSONObject().put("contents", new JSONArray().put(content));
        return new Request.Builder()
                .url(base + "/models/" + model.trim() + ":generateContent")
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
    }

    private static Request buildOpenAi(String endpoint, String model, String apiKey) throws Exception {
        JSONObject message = new JSONObject().put("role", "user").put("content", "Hi");
        JSONObject body = new JSONObject()
                .put("model", model.trim())
                .put("messages", new JSONArray().put(message))
                .put("max_tokens", 5);
        return new Request.Builder()
                .url(endpoint.trim())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
    }

    private static Request buildXai(String endpoint, String model, String apiKey) throws Exception {
        JSONObject textPart = new JSONObject().put("type", "input_text").put("text", "Hi");
        JSONObject message = new JSONObject()
                .put("role", "user")
                .put("content", new JSONArray().put(textPart));
        JSONObject body = new JSONObject()
                .put("model", model.trim())
                .put("input", new JSONArray().put(message));
        return new Request.Builder()
                .url(endpoint.trim())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
    }

    private static String describeHttpError(Context ctx, int code, String rawJson) {
        String trimmed = rawJson == null ? "" : rawJson.trim();
        boolean htmlPage = trimmed.startsWith("<")
                || trimmed.toLowerCase(Locale.ROOT).contains("<html");

        String base;
        if (htmlPage && (code == 403 || code == 429 || code == 401)) {
            base = ctx.getString(R.string.blocked_by_network);
        } else if (code == 429) {
            base = ctx.getString(R.string.quota_exceeded);
        } else if (code == 401 || code == 403) {
            base = ctx.getString(R.string.invalid_key);
        } else if (code == 404) {
            base = ctx.getString(R.string.test_not_found);
        } else {
            base = ctx.getString(R.string.server_error_prefix) + code;
        }

        String detail = extractMessage(rawJson);
        if (detail == null || detail.trim().isEmpty()) {
            detail = trimmed;
        }
        if (detail.length() > 200) {
            detail = detail.substring(0, 200) + "...";
        }
        return detail.isEmpty() ? base : base + " - " + detail;
    }

    private static String extractMessage(String rawJson) {
        try {
            JSONObject root = new JSONObject(rawJson);
            if (root.has("error")) {
                Object err = root.get("error");
                if (err instanceof JSONObject && ((JSONObject) err).has("message")) {
                    return ((JSONObject) err).getString("message");
                }
                if (err instanceof String) {
                    return (String) err;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

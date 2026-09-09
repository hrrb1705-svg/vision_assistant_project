package com.example.visionassistant;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1001;
    private static final String API_KEY = "12345";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";

    private PreviewView viewFinder;
    private Button btnCaptureDescribe;
    private TextView tvStatus;
    private ImageCapture imageCapture;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                tvStatus.setText("دسترسی دوربین داده نشد");
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
                tvStatus.setText("خطا در راه‌اندازی دوربین: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureAndDescribe() {
        if (imageCapture == null) {
            tvStatus.setText("دوربین آماده نیست");
            return;
        }
        tvStatus.setText("در حال گرفتن تصویر...");
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
                        tvStatus.setText("خطا در گرفتن تصویر: " + exception.getMessage());
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

    private void sendToApi(byte[] imageBytes) {
        executor.execute(() -> {
            try {
                if (imageBytes.length == 0) {
                    runOnUiThread(() -> tvStatus.setText("تصویر خالی است"));
                    return;
                }
                String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                JSONObject imageContent = new JSONObject();
                imageContent.put("type", "image_url");
                imageContent.put("image_url",
                        new JSONObject().put("url", "data:image/jpeg;base64," + base64));

                JSONObject textContent = new JSONObject();
                textContent.put("type", "text");
                textContent.put("text", "اشیاء و موانع موجود در این تصویر را توصیف کن.");

                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content",
                        "You are a vision assistant for a blind user. Describe objects and obstacles concisely.");

                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content",
                        new JSONArray().put(textContent).put(imageContent));

                JSONObject body = new JSONObject();
                body.put("model", MODEL);
                body.put("messages", new JSONArray().put(systemMessage).put(userMessage));

                Request request = new Request.Builder()
                        .url(API_URL)
                        .header("Authorization", "Bearer " + API_KEY)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(),
                                MediaType.parse("application/json")))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    InputStream is = response.body() != null
                            ? response.body().byteStream() : null;
                    String json = is != null
                            ? new String(readAll(is), StandardCharsets.UTF_8) : "";
                    String result;
                    if (response.isSuccessful()) {
                        JSONObject root = new JSONObject(json);
                        result = root.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                    } else {
                        result = "خطای سرور: " + response.code() + " - " + json;
                    }
                    String finalResult = result;
                    runOnUiThread(() -> tvStatus.setText(finalResult));
                }
            } catch (Exception e) {
                String msg = "خطا: " + e.getMessage();
                runOnUiThread(() -> tvStatus.setText(msg));
            }
        });
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

package com.example.visionassistant;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.concurrentutors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};

    private PreviewView viewFinder;
    private Button btnCaptureDescribe;
    private TextView tvStatus;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private TextToSpeech tts;

    // کلید موقت طبق درخواست کاربر
    private static final String API_KEY = "12345";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        btnCaptureDescribe = findViewById(R.id.btnCaptureDescribe);
        tvStatus = findViewById(R.id.tvStatus);

        initTTS();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        btnCaptureDescribe.setOnClickListener(v -> takePhotoAndAnalyze());

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("fa"));
                speak("برنامه دستیار بینایی آماده است.");
            }
        });
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ID");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhotoAndAnalyze() {
        if (imageCapture == null) return;

        speak("در حال تحلیل تصویر");
        tvStatus.setText("در حال تصویربرداری و ارسال...");

        File photoFile = new File(getExternalCacheDir(), "capture.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                cameraExecutor.execute(() -> sendToAiApi(photoFile));
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> {
                    speak("خطا در ثبت عکس");
                    tvStatus.setText("خطا در عکس‌برداری");
                });
            }
        });
    }

    private void sendToAiApi(File photoFile) {
        try {
            byte[] bytes = new byte[(int) photoFile.length()];
            FileInputStream fis = new FileInputStream(photoFile);
            fis.read(bytes);
            fis.close();
            String base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP);

            String prompt = "شما دستیار فرد نابینا هستید. این تصویر را بسیار کوتاه، دقیق و مستقیم توصیف کنید. اولویت اصلی شما تشخیص موانع، گودال‌ها، پله‌ها، افراد نزدیک و خطرات است. جهت‌ها را به صورت ساعت یا چپ/راست بیان کنید. پاسخ را فارسی، مختصر و بدون مقدمه‌چینی بنویسید.";

            JSONObject inlineData = new JSONObject();
            inlineData.put("mimeType", "image/jpeg");
            inlineData.put("data", base64Image);

            JSONObject partImage = new JSONObject();
            partImage.put("inlineData", inlineData);

            JSONObject partText = new JSONObject();
            partText.put("text", prompt);

            JSONArray parts = new JSONArray();
            parts.put(partText);
            parts.put(partImage);

            JSONObject content = new JSONObject();
            content.put("parts", parts);

            JSONArray contents = new JSONArray();
            contents.put(content);

            JSONObject payload = new JSONObject contents.put(content);

            JSONObject payload = new JSONObject();
            payload.put("contents            RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder().url(API_URL).post(body).build();

            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String responseData = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseData);
                String resultText = jsonResponse.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text");

                runOnUiThread(() -> {
                    tvStatus.setText(resultText);
                    speak(resultText);
                });
            } else {
                runOnUiThread(() -> {
                    speak("خطا در دریافت پاسخ از سرور");
                    tvStatus.setText("خطای سرور: " + response.code());
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> {
                speak("خطا در ارتباط اینترنتی");
                tvStatus.setText("خطای ارتباطی: " + e.getMessage());
            });
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "مجوز دسترسی به دوربین و صدا داده نشد.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        cameraExecutor.shutdown();
    }
}

package com.example.visionassistant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// صفحهٔ افزودن تصویر یا ویدیو: یا با دوربین (با کنترل دست خودِ کاربر، نه فوری و خودکار)
// عکس می‌گیرد یا یک ویدیوی کوتاه ضبط می‌کند، یا فایلی از گوشی انتخاب می‌کند
public class AttachmentActivity extends BaseActivity {

    public static final String RESULT_EXTRA_PATH = "attached_image_path";
    public static final String RESULT_EXTRA_MIME = "attached_image_mime";
    private static final int REQUEST_CAMERA_PERMISSION = 2001;

    private PreviewView viewFinder;
    private View groupChoice;
    private View groupCamera;
    private Button btnAttachCamera;
    private Button btnAttachFile;
    private Button btnCancelAttach;
    private Button btnCaptureNow;
    private Button btnRecordVideo;
    private Button btnBackFromCamera;
    private TextView tvCameraHint;
    private Button btnPauseRecording;
    private Button btnStopRecording;
    private boolean paused = false;

    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private boolean cameraReady = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> pickFileLauncher;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attachment);

        viewFinder = findViewById(R.id.viewFinderAttach);
        groupChoice = findViewById(R.id.groupChoice);
        groupCamera = findViewById(R.id.groupCamera);
        btnAttachCamera = findViewById(R.id.btnAttachCamera);
        btnAttachFile = findViewById(R.id.btnAttachFile);
        btnCancelAttach = findViewById(R.id.btnCancelAttach);
        btnCaptureNow = findViewById(R.id.btnCaptureNow);
        btnRecordVideo = findViewById(R.id.btnRecordVideo);
        btnBackFromCamera = findViewById(R.id.btnBackFromCamera);
        tvCameraHint = findViewById(R.id.tvCameraHint);
        btnPauseRecording = findViewById(R.id.btnPauseRecording);
        btnStopRecording = findViewById(R.id.btnStopRecording);
        android.widget.EditText edtRec = findViewById(R.id.edtRecInterval);
        android.widget.Spinner chkRec = findViewById(R.id.spnRecVideoMode);
        edtRec.setText(String.valueOf(AppPrefs.getVideoIntervalSec(this)));
        VideoModeSpinner.bind(this, chkRec);
        edtRec.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence x, int a, int b, int c) {}
            public void onTextChanged(CharSequence x, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) {
                try { AppPrefs.setVideoIntervalSec(AttachmentActivity.this, Integer.parseInt(e.toString().trim())); } catch (Exception ignored) {}
            }
        });
        btnPauseRecording.setOnClickListener(v -> {
            if (activeRecording == null) return;
            if (paused) { activeRecording.resume(); paused = false; btnPauseRecording.setText(R.string.btn_pause_recording); }
            else { activeRecording.pause(); paused = true; btnPauseRecording.setText(R.string.btn_resume_recording); }
        });
        btnStopRecording.setOnClickListener(v -> stopRecording());

        pickFileLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onFilePicked);

        btnAttachCamera.setOnClickListener(v -> openCameraPanel());
        btnAttachFile.setOnClickListener(v -> pickFileLauncher.launch("*/*"));
        btnCancelAttach.setOnClickListener(v -> finish());
        btnCaptureNow.setOnClickListener(v -> captureFromCamera());
        btnRecordVideo.setOnClickListener(v -> onRecordVideoPressed());
        btnBackFromCamera.setOnClickListener(v -> closeCameraPanel());
    }

    // --- ورود به حالت دوربین: اول اجازه می‌گیرد، بعد کنترل عکس/ویدیو را نشان می‌دهد ---
    private void openCameraPanel() {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    REQUEST_CAMERA_PERMISSION);
            return;
        }
        groupChoice.setVisibility(View.GONE);
        groupCamera.setVisibility(View.VISIBLE);
        tvCameraHint.setText(R.string.camera_ready_hint);
        startCamera();
    }

    private void closeCameraPanel() {
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
        groupCamera.setVisibility(View.GONE);
        groupChoice.setVisibility(View.VISIBLE);
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            boolean cameraGranted = false;
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.CAMERA.equals(permissions[i])
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    cameraGranted = true;
                }
            }
            if (cameraGranted) {
                openCameraPanel();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.error_dialog_title)
                        .setMessage(R.string.permission_denied)
                        .setPositiveButton(R.string.error_dialog_ok, null)
                        .show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();

                Recorder recorder = new Recorder.Builder().build();
                videoCapture = VideoCapture.withOutput(recorder);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture, videoCapture);
                cameraReady = true;
            } catch (Exception e) {
                showError(getString(R.string.error_camera_start_prefix) + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // --- گرفتن عکس، فقط وقتی خود کاربر این کلید را بزند ---
    private void captureFromCamera() {
        if (!cameraReady || imageCapture == null) {
            Toast.makeText(this, R.string.camera_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        setCameraButtonsEnabled(false);
        tvCameraHint.setText(R.string.capturing_image);
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        byte[] bytes = ImageUtils.imageProxyToJpegBytes(image);
                        image.close();
                        finishWithBytes(bytes, "image/jpeg");
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        runOnUiThread(() -> {
                            setCameraButtonsEnabled(true);
                            tvCameraHint.setText(R.string.camera_ready_hint);
                            showError(getString(R.string.error_capture_prefix) + exception.getMessage());
                        });
                    }
                });
    }

    // --- ضبط یک ویدیوی کوتاه؛ با فشردن دوباره یا بعد از چند ثانیه خودش تمام می‌شود ---
    private void onRecordVideoPressed() {
        if (activeRecording != null) {
            return;
        }
        if (!cameraReady || videoCapture == null) {
            Toast.makeText(this, R.string.camera_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean hasAudio = hasPermission(Manifest.permission.RECORD_AUDIO);

        File outFile = new File(getCacheDir(), "attach_" + System.currentTimeMillis() + ".mp4");
        FileOutputOptions outputOptions = new FileOutputOptions.Builder(outFile).build();

        androidx.camera.video.PendingRecording pending = videoCapture.getOutput()
                .prepareRecording(this, outputOptions);
        if (hasAudio) {
            pending = pending.withAudioEnabled();
        }

        btnCaptureNow.setEnabled(false);
        btnAttachFile.setEnabled(false);
        btnRecordVideo.setEnabled(false);
        btnPauseRecording.setEnabled(true);
        btnStopRecording.setEnabled(true);
        paused = false;
        tvCameraHint.setText(R.string.recording_started);

        activeRecording = pending.start(ContextCompat.getMainExecutor(this), event -> {
            if (event instanceof VideoRecordEvent.Finalize) {
                VideoRecordEvent.Finalize finalize = (VideoRecordEvent.Finalize) event;
                activeRecording = null;
                handler.removeCallbacksAndMessages(null);
                btnRecordVideo.setText(R.string.btn_start_recording);
                btnPauseRecording.setEnabled(false);
                btnStopRecording.setEnabled(false);
                if (finalize.hasError()) {
                    setCameraButtonsEnabled(true);
                    tvCameraHint.setText(R.string.camera_ready_hint);
                    showError(getString(R.string.error_capture_prefix) + finalize.getCause());
                } else {
                    tvCameraHint.setText(R.string.processing_video);
                    readFileAndFinish(outFile, "video/mp4");
                }
            }
        });

        // ویدیو باید کوتاه بماند، پس بعد از مدت مشخصی خودش قطع می‌شود
    }

    private void stopRecording() {
        if (activeRecording != null) {
            activeRecording.stop();
        }
    }

    private void setCameraButtonsEnabled(boolean enabled) {
        btnCaptureNow.setEnabled(enabled);
        btnRecordVideo.setEnabled(enabled);
        btnAttachFile.setEnabled(enabled);
    }

    private void onFilePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        setCameraButtonsEnabled(false);
        Toast.makeText(this, R.string.capturing_image, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            String type = getContentResolver().getType(uri);
            String lastSeg = uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment().toLowerCase(java.util.Locale.ROOT);
            boolean looksVideo = lastSeg.endsWith(".mp4") || lastSeg.endsWith(".mov") || lastSeg.endsWith(".3gp")
                    || lastSeg.endsWith(".webm") || lastSeg.endsWith(".mkv");
            if ((type != null && type.startsWith("video/")) || (type == null && looksVideo)) {
                try {
                    File vf = new File(getCacheDir(), "attach_" + System.currentTimeMillis() + ".mp4");
                    try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                         FileOutputStream fos = new FileOutputStream(vf)) {
                        byte[] buf = new byte[65536]; int n;
                        while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                    }
                    readFileAndFinish(vf, "video/mp4");
                } catch (Exception e) {
                    runOnUiThread(() -> { setCameraButtonsEnabled(true); showError(getString(R.string.generic_error_prefix) + e.getMessage()); });
                }
                return;
            }
            byte[] bytes = ImageUtils.fileUriToJpegBytes(this, uri);
            finishWithBytes(bytes, "image/jpeg");
        });
    }

    private void finishWithBytes(byte[] bytes, String mime) {
        if (bytes == null || bytes.length == 0) {
            runOnUiThread(() -> {
                setCameraButtonsEnabled(true);
                showError(getString(R.string.empty_image));
            });
            return;
        }
        try {
            String ext = "video/mp4".equals(mime) ? ".mp4" : ".jpg";
            File outFile = new File(getCacheDir(), "attach_" + System.currentTimeMillis() + ext);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(bytes);
            }
            returnResult(outFile, mime);
        } catch (Exception e) {
            runOnUiThread(() -> {
                setCameraButtonsEnabled(true);
                showError(getString(R.string.generic_error_prefix) + e.getMessage());
            });
        }
    }

    private void readFileAndFinish(File file, String mime) {
        if (!file.exists() || file.length() == 0) {
            setCameraButtonsEnabled(true);
            showError(getString(R.string.empty_image));
            return;
        }
        returnResult(file, mime);
    }

    private void returnResult(File file, String mime) {
        Intent result = new Intent();
        result.putExtra(RESULT_EXTRA_PATH, file.getAbsolutePath());
        result.putExtra(RESULT_EXTRA_MIME, mime);
        setResult(RESULT_OK, result);
        runOnUiThread(this::finish);
    }

    private void showError(String message) {
        ErrorLog.add(this, message);
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.error_dialog_ok, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeRecording != null) {
            activeRecording.stop();
        }
        handler.removeCallbacksAndMessages(null);
        executor.shutdown();
    }
}

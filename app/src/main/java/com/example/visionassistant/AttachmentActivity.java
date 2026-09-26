package com.example.visionassistant;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
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

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// صفحهٔ افزودن تصویر: یا با دوربین عکس می‌گیرد یا فایلی از گوشی انتخاب می‌کند،
// و مسیر فایل موقت نتیجه را به صفحهٔ فراخواننده برمی‌گرداند
public class AttachmentActivity extends BaseActivity {

    public static final String RESULT_EXTRA_PATH = "attached_image_path";
    private static final int REQUEST_CAMERA_PERMISSION = 2001;

    private PreviewView viewFinder;
    private Button btnAttachCamera;
    private Button btnAttachFile;
    private Button btnCancelAttach;
    private ImageCapture imageCapture;
    private boolean captureQueued = false;
    private ActivityResultLauncher<String> pickFileLauncher;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attachment);

        viewFinder = findViewById(R.id.viewFinderAttach);
        btnAttachCamera = findViewById(R.id.btnAttachCamera);
        btnAttachFile = findViewById(R.id.btnAttachFile);
        btnCancelAttach = findViewById(R.id.btnCancelAttach);

        pickFileLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onFilePicked);

        btnAttachCamera.setOnClickListener(v -> onCameraButtonPressed());
        btnAttachFile.setOnClickListener(v -> pickFileLauncher.launch("image/*"));
        btnCancelAttach.setOnClickListener(v -> finish());

        if (hasCameraPermission()) {
            startCamera();
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void onCameraButtonPressed() {
        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        if (imageCapture == null) {
            captureQueued = true;
            startCamera();
            return;
        }
        captureFromCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
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
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture);
                if (captureQueued) {
                    captureQueued = false;
                    captureFromCamera();
                }
            } catch (Exception e) {
                showError(getString(R.string.error_camera_start_prefix) + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureFromCamera() {
        btnAttachCamera.setEnabled(false);
        btnAttachFile.setEnabled(false);
        Toast.makeText(this, R.string.capturing_image, Toast.LENGTH_SHORT).show();
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        byte[] bytes = ImageUtils.imageProxyToJpegBytes(image);
                        image.close();
                        finishWithBytes(bytes);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        runOnUiThread(() -> {
                            btnAttachCamera.setEnabled(true);
                            btnAttachFile.setEnabled(true);
                            showError(getString(R.string.error_capture_prefix) + exception.getMessage());
                        });
                    }
                });
    }

    private void onFilePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        btnAttachCamera.setEnabled(false);
        btnAttachFile.setEnabled(false);
        Toast.makeText(this, R.string.capturing_image, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            byte[] bytes = ImageUtils.fileUriToJpegBytes(this, uri);
            finishWithBytes(bytes);
        });
    }

    private void finishWithBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            runOnUiThread(() -> {
                btnAttachCamera.setEnabled(true);
                btnAttachFile.setEnabled(true);
                showError(getString(R.string.empty_image));
            });
            return;
        }
        try {
            File outFile = new File(getCacheDir(), "attach_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(bytes);
            }
            Intent result = new Intent();
            result.putExtra(RESULT_EXTRA_PATH, outFile.getAbsolutePath());
            setResult(RESULT_OK, result);
            runOnUiThread(this::finish);
        } catch (Exception e) {
            runOnUiThread(() -> {
                btnAttachCamera.setEnabled(true);
                btnAttachFile.setEnabled(true);
                showError(getString(R.string.generic_error_prefix) + e.getMessage());
            });
        }
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.error_dialog_ok, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

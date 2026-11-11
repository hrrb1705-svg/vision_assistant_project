package com.example.visionassistant;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConversationViewActivity extends BaseActivity {

    private ConversationStore.ConversationDetail detail;
    private String conversationId;
    private ListView listTurns;
    private ArrayAdapter<String> turnsAdapter;
    private List<Integer> rowTurn = new ArrayList<>();
    private EditText editContinueQuestion;
    private TextView tvConversationStatus;
    // اگر کاربر در همین صفحه تصویر یا ویدیوی تازه‌ای پیوست کند، به‌جای رسانهٔ اصلی گفتگو همین استفاده می‌شود
    private String newAttachedImagePath = null;
    private String newAttachedImageMime = null;
    private ActivityResultLauncher<Intent> attachmentLauncher;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation_view);

        TextView tvTitle = findViewById(R.id.tvConversationTitle);
        ImageView ivThumbnail = findViewById(R.id.ivConversationThumbnail);
        android.widget.Button btnPlayVideo = findViewById(R.id.btnPlayVideo);
        listTurns = findViewById(R.id.listConversationTurns);
        editContinueQuestion = findViewById(R.id.editContinueQuestion);
        tvConversationStatus = findViewById(R.id.tvConversationStatus);
        android.widget.Button btnContinueAttach = findViewById(R.id.btnContinueAttach);
        android.widget.Button btnContinueSend = findViewById(R.id.btnContinueSend);

        attachmentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onAttachmentResult);

        conversationId = getIntent().getStringExtra("conversation_id");
        if (conversationId == null) {
            finish();
            return;
        }

        try {
            detail = ConversationStore.loadConversation(this, conversationId);
            tvTitle.setText(detail.title);

            boolean isVideo = "video/mp4".equals(detail.imageMime);
            if (detail.imagePath != null && !isVideo) {
                Bitmap bitmap = BitmapFactory.decodeFile(detail.imagePath);
                if (bitmap != null) {
                    ivThumbnail.setImageBitmap(bitmap);
                } else {
                    ivThumbnail.setVisibility(View.GONE);
                }
            } else {
                ivThumbnail.setVisibility(View.GONE);
            }

            if (detail.imagePath != null && isVideo) {
                btnPlayVideo.setVisibility(View.VISIBLE);
                btnPlayVideo.setOnClickListener(v -> playVideo(detail.imagePath));
            }

            List<String> lines = new ArrayList<>();
            rowTurn = new ArrayList<>();
            for (int ti = 0; ti < detail.turns.size(); ti++) {
                ChatTurn turn = detail.turns.get(ti);
                String prefix = "assistant".equals(turn.role)
                        ? getString(R.string.chat_assistant_prefix)
                        : getString(R.string.chat_you_prefix);
                List<String> rows = ParagraphRows.build(this, prefix, turn.text);
                for (int r = 0; r < rows.size(); r++) {
                    lines.add(rows.get(r));
                    rowTurn.add(ti);
                }
            }
            turnsAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_multiple_choice, lines);
            listTurns.setAdapter(turnsAdapter);
        } catch (Exception e) {
            tvConversationStatus.setText(getString(R.string.generic_error_prefix) + e.getMessage());
            return;
        }

        btnContinueAttach.setOnClickListener(v ->
                attachmentLauncher.launch(new Intent(this, AttachmentActivity.class)));
        btnContinueSend.setOnClickListener(v -> onContinueClicked());
    }

    private void playVideo(String path) {
        try {
            Uri contentUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", new File(path));
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "video/mp4");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error_dialog_title)
                    .setMessage(R.string.video_playback_no_app)
                    .setPositiveButton(R.string.error_dialog_ok, null)
                    .show();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error_dialog_title)
                    .setMessage(getString(R.string.generic_error_prefix) + e.getMessage())
                    .setPositiveButton(R.string.error_dialog_ok, null)
                    .show();
        }
    }

    private void onAttachmentResult(androidx.activity.result.ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            return;
        }
        String path = result.getData().getStringExtra(AttachmentActivity.RESULT_EXTRA_PATH);
        String mime = result.getData().getStringExtra(AttachmentActivity.RESULT_EXTRA_MIME);
        if (path == null) {
            return;
        }
        String finalMime = (mime == null || mime.isEmpty()) ? "image/jpeg" : mime;
        if (finalMime.startsWith("video/") && !AppPrefs.PROVIDER_GOOGLE.equals(AppPrefs.getActiveProvider(this))) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error_dialog_title)
                    .setMessage(R.string.video_not_supported_by_service)
                    .setPositiveButton(R.string.error_dialog_ok, null)
                    .show();
            return;
        }
        newAttachedImagePath = path;
        newAttachedImageMime = finalMime;
        tvConversationStatus.setText(R.string.image_attached_status);
    }

    // فقط بلوک‌هایی که کاربر با دو ضربه در فهرست انتخاب کرده، همراه سوال تازه و رسانه
    // (تازه یا همان رسانهٔ اصلی گفتگو) به صفحهٔ اصلی فرستاده می‌شوند تا گفتگو ادامه پیدا کند
    private void onContinueClicked() {
        String question = editContinueQuestion.getText().toString().trim();
        String imagePath = newAttachedImagePath != null ? newAttachedImagePath : detail.imagePath;
        String imageMime = newAttachedImagePath != null
                ? newAttachedImageMime
                : (detail.imageMime != null ? detail.imageMime : "image/jpeg");

        if (question.isEmpty() && newAttachedImagePath == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error_dialog_title)
                    .setMessage(R.string.error_need_question_or_image)
                    .setPositiveButton(R.string.error_dialog_ok, null)
                    .show();
            return;
        }
        if (imagePath == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error_dialog_title)
                    .setMessage(R.string.need_image_first)
                    .setPositiveButton(R.string.error_dialog_ok, null)
                    .show();
            return;
        }

        String effectiveQuestion = question.isEmpty()
                ? getString(R.string.prompt_describe)
                : question;

        SparseBooleanArray checked = listTurns.getCheckedItemPositions();
        boolean[] turnChecked = new boolean[detail.turns.size()];
        for (int r = 0; r < rowTurn.size(); r++) {
            if (checked.get(r)) turnChecked[rowTurn.get(r)] = true;
        }
        JSONArray contextArray = new JSONArray();
        try {
            for (int i = 0; i < detail.turns.size(); i++) {
                if (turnChecked[i]) {
                    ChatTurn t = detail.turns.get(i);
                    JSONObject m = new JSONObject();
                    m.put("role", t.role);
                    m.put("text", t.text);
                    contextArray.put(m);
                }
            }
        } catch (Exception ignored) {
        }

        String finalImagePath = imagePath;
        String finalImageMime = imageMime;
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_CONTINUE_IMAGE_PATH, finalImagePath);
        intent.putExtra(MainActivity.EXTRA_CONTINUE_IMAGE_MIME, finalImageMime);
        intent.putExtra(MainActivity.EXTRA_CONTINUE_CONTEXT_JSON, contextArray.toString());
        intent.putExtra(MainActivity.EXTRA_CONTINUE_QUESTION, effectiveQuestion);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

}

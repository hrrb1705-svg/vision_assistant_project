package com.example.visionassistant;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ConversationViewActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation_view);

        TextView tvTitle = findViewById(R.id.tvConversationTitle);
        ImageView ivThumbnail = findViewById(R.id.ivConversationThumbnail);
        TextView tvTranscript = findViewById(R.id.tvConversationTranscript);

        String id = getIntent().getStringExtra("conversation_id");
        if (id == null) {
            finish();
            return;
        }

        try {
            ConversationStore.ConversationDetail detail = ConversationStore.loadConversation(this, id);
            tvTitle.setText(detail.title);

            if (detail.imagePath != null) {
                Bitmap bitmap = BitmapFactory.decodeFile(detail.imagePath);
                if (bitmap != null) {
                    ivThumbnail.setImageBitmap(bitmap);
                } else {
                    ivThumbnail.setVisibility(View.GONE);
                }
            } else {
                ivThumbnail.setVisibility(View.GONE);
            }

            StringBuilder sb = new StringBuilder();
            for (ChatTurn turn : detail.turns) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                if ("assistant".equals(turn.role)) {
                    sb.append(getString(R.string.chat_assistant_prefix)).append(turn.text);
                } else {
                    sb.append(getString(R.string.chat_you_prefix)).append(turn.text);
                }
            }
            tvTranscript.setText(sb.toString());
        } catch (Exception e) {
            tvTranscript.setText(getString(R.string.generic_error_prefix) + e.getMessage());
        }
    }
}

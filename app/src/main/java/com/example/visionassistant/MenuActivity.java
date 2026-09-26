package com.example.visionassistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MenuActivity extends BaseActivity {

    private ListView listConversations;
    private TextView tvNoConversations;
    private Button btnSettingsFromMenu;
    private final List<ConversationStore.ConversationMeta> currentItems = new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        listConversations = findViewById(R.id.listConversations);
        tvNoConversations = findViewById(R.id.tvNoConversations);
        btnSettingsFromMenu = findViewById(R.id.btnSettingsFromMenu);

        btnSettingsFromMenu.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        listConversations.setOnItemClickListener((parent, view, position, id) -> {
            ConversationStore.ConversationMeta meta = currentItems.get(position);
            Intent intent = new Intent(this, ConversationViewActivity.class);
            intent.putExtra("conversation_id", meta.id);
            startActivity(intent);
        });

        listConversations.setOnItemLongClickListener((parent, view, position, id) -> {
            showConversationOptions(currentItems.get(position));
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadConversations();
    }

    private void reloadConversations() {
        currentItems.clear();
        currentItems.addAll(ConversationStore.listConversations(this));

        List<String> titles = new ArrayList<>();
        for (ConversationStore.ConversationMeta meta : currentItems) {
            titles.add(meta.title);
        }

        listConversations.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, titles));

        boolean empty = currentItems.isEmpty();
        tvNoConversations.setVisibility(empty ? View.VISIBLE : View.GONE);
        listConversations.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // با کلیک طولانی روی یک گفتگو، انتخاب بین تغییر نام یا حذف آن
    private void showConversationOptions(ConversationStore.ConversationMeta meta) {
        String[] options = {
                getString(R.string.rename_conversation),
                getString(R.string.delete_conversation)
        };
        new AlertDialog.Builder(this)
                .setTitle(meta.title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameDialog(meta);
                    } else {
                        confirmDelete(meta);
                    }
                })
                .show();
    }

    private void showRenameDialog(ConversationStore.ConversationMeta meta) {
        EditText input = new EditText(this);
        input.setText(meta.title);
        input.setContentDescription(getString(R.string.rename_conversation));
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle(R.string.rename_conversation)
                .setView(input)
                .setPositiveButton(R.string.confirm_yes, (dialog, which) -> {
                    String newTitle = input.getText().toString().trim();
                    if (newTitle.isEmpty()) {
                        return;
                    }
                    try {
                        ConversationStore.renameConversation(this, meta.id, newTitle);
                    } catch (Exception ignored) {
                    }
                    reloadConversations();
                })
                .setNegativeButton(R.string.confirm_no, null)
                .show();
    }

    private void confirmDelete(ConversationStore.ConversationMeta meta) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_conversation)
                .setMessage(R.string.confirm_delete_conversation_message)
                .setPositiveButton(R.string.confirm_yes, (dialog, which) -> {
                    ConversationStore.deleteConversation(this, meta.id);
                    reloadConversations();
                })
                .setNegativeButton(R.string.confirm_no, null)
                .show();
    }
}

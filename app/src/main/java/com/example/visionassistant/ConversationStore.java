package com.example.visionassistant;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// گفتگوهای ذخیره‌شده را به صورت یک فایل json و یک فایل jpg برای هرکدام،
// داخل حافظه داخلی خود برنامه نگه می‌دارد. این فایل‌ها با گوشی‌های دیگر یا
// اپ‌های دیگر به اشتراک گذاشته نمی‌شوند و فقط خود برنامه به آنها دسترسی دارد.
public class ConversationStore {

    public static class ConversationMeta {
        public final String id;
        public final String title;
        public final long timestamp;

        public ConversationMeta(String id, String title, long timestamp) {
            this.id = id;
            this.title = title;
            this.timestamp = timestamp;
        }
    }

    public static class ConversationDetail {
        public final String title;
        public final String imagePath; // ممکن است null باشد
        public final List<ChatTurn> turns;

        public ConversationDetail(String title, String imagePath, List<ChatTurn> turns) {
            this.title = title;
            this.imagePath = imagePath;
            this.turns = turns;
        }
    }

    private static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "conversations");
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    // یک گفتگوی تازه را با عکس آن ذخیره می‌کند و شناسهٔ آن را برمی‌گرداند
    public static String saveNewConversation(Context ctx, byte[] imageBytes, List<ChatTurn> turns) throws Exception {
        long timestamp = System.currentTimeMillis();
        String id = String.valueOf(timestamp);
        String defaultTitle = defaultTitleFor(timestamp);

        File imageFile = new File(dir(ctx), "img_" + id + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            fos.write(imageBytes);
        }

        JSONArray messages = new JSONArray();
        for (ChatTurn t : turns) {
            // متن پیش‌فرض «این تصویر را توصیف کن» ذخیره نمی‌شود، چون خودکار است؛
            // پاسخ دستیار و سوال‌های واقعی کاربر ذخیره می‌شوند
            if (t.hasImage) {
                continue;
            }
            JSONObject m = new JSONObject();
            m.put("role", t.role);
            m.put("text", t.text);
            messages.put(m);
        }

        JSONObject root = new JSONObject();
        root.put("id", id);
        root.put("title", defaultTitle);
        root.put("timestamp", timestamp);
        root.put("image", imageFile.getName());
        root.put("messages", messages);

        File jsonFile = new File(dir(ctx), id + ".json");
        try (FileWriter fw = new FileWriter(jsonFile)) {
            fw.write(root.toString());
        }
        return id;
    }

    public static List<ConversationMeta> listConversations(Context ctx) {
        List<ConversationMeta> result = new ArrayList<>();
        File[] files = dir(ctx).listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return result;
        }
        for (File f : files) {
            try {
                JSONObject root = new JSONObject(readFile(f));
                result.add(new ConversationMeta(
                        root.getString("id"),
                        root.optString("title", ""),
                        root.optLong("timestamp", 0)));
            } catch (Exception ignored) {
                // یک فایل خراب یا ناقص را نادیده می‌گیریم تا کل فهرست خراب نشود
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        return result;
    }

    public static ConversationDetail loadConversation(Context ctx, String id) throws Exception {
        File jsonFile = new File(dir(ctx), id + ".json");
        JSONObject root = new JSONObject(readFile(jsonFile));
        String title = root.optString("title", "");

        String imageName = root.optString("image", "");
        String imagePath = null;
        if (!imageName.isEmpty()) {
            File img = new File(dir(ctx), imageName);
            if (img.exists()) {
                imagePath = img.getAbsolutePath();
            }
        }

        List<ChatTurn> turns = new ArrayList<>();
        JSONArray messages = root.optJSONArray("messages");
        if (messages != null) {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.getJSONObject(i);
                turns.add(new ChatTurn(m.getString("role"), m.getString("text"), false));
            }
        }
        return new ConversationDetail(title, imagePath, turns);
    }

    public static void renameConversation(Context ctx, String id, String newTitle) throws Exception {
        File jsonFile = new File(dir(ctx), id + ".json");
        JSONObject root = new JSONObject(readFile(jsonFile));
        root.put("title", newTitle);
        try (FileWriter fw = new FileWriter(jsonFile)) {
            fw.write(root.toString());
        }
    }

    public static void deleteConversation(Context ctx, String id) {
        File jsonFile = new File(dir(ctx), id + ".json");
        try {
            JSONObject root = new JSONObject(readFile(jsonFile));
            String imageName = root.optString("image", "");
            if (!imageName.isEmpty()) {
                new File(dir(ctx), imageName).delete();
            }
        } catch (Exception ignored) {
        }
        jsonFile.delete();
    }

    private static String readFile(File f) throws Exception {
        byte[] bytes = Files.readAllBytes(f.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String defaultTitleFor(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US);
        return sdf.format(new java.util.Date(timestamp));
    }
}

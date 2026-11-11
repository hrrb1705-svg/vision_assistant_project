package com.example.visionassistant;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// متن را به پاراگراف‌های شماره‌دار تبدیل می‌کند تا صفحه‌خوان بتواند بین بخش‌ها جابه‌جا شود.
// متن‌های خیلی بلند هم در مرز جمله به چند ردیف شماره‌دار شکسته می‌شوند.
public class ParagraphRows {

    // پاسخ مدل را به متن ساده تبدیل می‌کند: حذف علامت‌های مارک‌دان (ستاره، #، بک‌تیک، جدول و …)
    public static String plain(String text) {
        if (text == null) return "";
        String t = text.replace("\r", "");
        t = t.replaceAll("(?m)^\\s*[-*_]{3,}\\s*$", "");          // خط جداکننده
        t = t.replaceAll("(?m)^\\s*#{1,6}\\s*", "");               // سرتیتر
        t = t.replaceAll("(?m)^\\s*>\\s?", "");                    // نقل‌قول
        t = t.replaceAll("(?m)^\\s*[*+-]\\s+", "");                // بولت
        t = t.replaceAll("!?\\[([^\\]]*)\\]\\([^)]*\\)", "$1");   // لینک و تصویر
        t = t.replaceAll("(\\*\\*|__)(.+?)\\1", "$2");           // پررنگ
        t = t.replaceAll("(?<![\\w*])[*_](?!\\s)(.+?)(?<!\\s)[*_](?![\\w*])", "$1"); // کج
        t = t.replaceAll("`{1,3}", "");                               // بک‌تیک
        t = t.replaceAll("(?m)^\\s*\\|?[-:| ]+\\|[-:| ]*$", "");     // خط جدول
        t = t.replaceAll("\\s*\\|\\s*", " ، ");                     // ستون جدول
        t = t.replaceAll("[*#_~]{1,}", "");                           // هر ستاره/# باقی‌مانده
        t = t.replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }

    // دستور ثابت برای پاسخ بدون مارک‌داون، به متن کاربر اضافه می‌شود
    public static String withPlainHint(String role, String text) {
        if (text == null || "assistant".equals(role)) return text;
        return text + "\n\n(Reply in plain text only. Do not use markdown, asterisks, # headings, tables, emojis or decorative symbols.)";
    }

    private static final int MAX_CHARS = 280;

    // پیشوند: شماره پاراگراف (با ارقام فارسی در زبان fa)، و پیشوند اختیاری مثل «شما:» فقط روی ردیف اول
    public static List<String> build(Context ctx, String prefix, String text) {
        List<String> rows = new ArrayList<>();
        if (text == null) return rows;
        boolean fa = isPersian(ctx);
        int n = 0;
        for (String para : text.split("\\n\\s*\\n|\\n")) {
            String p = para.trim();
            if (p.isEmpty()) continue;
            for (String chunk : chunk(p)) {
                n++;
                String head = (n == 1 && prefix != null) ? prefix : "";
                rows.add(head + toDigits(n, fa) + ". " + chunk);
            }
        }
        if (rows.isEmpty() && prefix != null) {
            rows.add(prefix.trim());
        }
        return rows;
    }

    private static List<String> chunk(String p) {
        List<String> out = new ArrayList<>();
        while (p.length() > MAX_CHARS) {
            int cut = -1;
            for (int i = MAX_CHARS; i > MAX_CHARS / 2; i--) {
                char c = p.charAt(i - 1);
                if (c == '.' || c == '!' || c == '?' || c == '؟' || c == '۔' || c == ' ') {
                    cut = i;
                    break;
                }
            }
            if (cut < 0) cut = MAX_CHARS;
            out.add(p.substring(0, cut).trim());
            p = p.substring(cut).trim();
        }
        if (!p.isEmpty()) out.add(p);
        return out;
    }

    private static boolean isPersian(Context ctx) {
        Locale l = ctx.getResources().getConfiguration().getLocales().get(0);
        return "fa".equals(l.getLanguage());
    }

    private static String toDigits(int n, boolean fa) {
        String s = String.valueOf(n);
        if (!fa) return s;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append((char) ('۰' + (c - '0')));
        }
        return sb.toString();
    }
}

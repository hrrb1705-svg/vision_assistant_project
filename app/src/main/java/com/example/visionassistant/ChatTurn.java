package com.example.visionassistant;

// یک پیام در یک گفتگوی دربارهٔ یک تصویر: یا از طرف کاربر است یا پاسخ دستیار
public class ChatTurn {
    public final String role; // "user" یا "assistant"
    public final String text;
    public final boolean hasImage; // فقط اولین پیام کاربر است که تصویر همراهش می‌رود

    public ChatTurn(String role, String text, boolean hasImage) {
        this.role = role;
        this.text = text;
        this.hasImage = hasImage;
    }
}

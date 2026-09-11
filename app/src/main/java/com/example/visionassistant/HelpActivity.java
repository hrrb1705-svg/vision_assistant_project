package com.example.visionassistant;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
    }
}

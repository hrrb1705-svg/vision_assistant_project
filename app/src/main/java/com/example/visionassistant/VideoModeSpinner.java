package com.example.visionassistant;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

public final class VideoModeSpinner {
    private VideoModeSpinner() {}

    public static void bind(Context c, Spinner sp) {
        String[] items = c.getResources().getStringArray(R.array.video_modes);
        ArrayAdapter<String> ad = new ArrayAdapter<>(c, android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(AppPrefs.getVideoMode(c));
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { AppPrefs.setVideoMode(c, pos); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
    }
}

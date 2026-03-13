package com.peggy.virtualchat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 綁定 API Key 設定區塊，建立導航跳轉
        LinearLayout layoutApiKey = findViewById(R.id.layoutApiKeySettings);
        layoutApiKey.setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, ApiKeySettingsActivity.class));
        });
    }
}